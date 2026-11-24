#!/usr/bin/env python3
"""Traffic simulator for the EC Lakehouse API.

Plays a small marketplace against the gateway: sellers sign up and wait for an admin to approve
them, approved sellers list and restock products, customers sign up, browse, fill carts (and
sometimes walk away), check out, now and then cancel, and sellers ship and deliver their part of
each order. Everything goes through the public gateway, exactly like a real client would.

    python simulate.py                          # run until Ctrl+C against http://localhost:8080
    python simulate.py --iterations 200         # 200 actions, then a summary
    python simulate.py --base-url http://10.0.0.5:8080 --delay 0.5 --seed 42

Settings can also come from the environment: SIM_BASE_URL, BOOTSTRAP_ADMIN_EMAIL,
BOOTSTRAP_ADMIN_PASSWORD (the same names the API's .env uses).
"""
from __future__ import annotations

import argparse
import os
import random
import signal
import string
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable

try:
    import requests
except ImportError:  # pragma: no cover
    sys.exit("This script needs the 'requests' package: pip install -r requirements.txt")

# --------------------------------------------------------------------------------------------
# Fake-but-plausible data (Brazilian flavour, to match the Olist dataset the lakehouse uses)
# --------------------------------------------------------------------------------------------

FIRST_NAMES = ["Ana", "Bruno", "Camila", "Diego", "Eduarda", "Felipe", "Gabriela", "Heitor",
               "Isabela", "João", "Larissa", "Lucas", "Mariana", "Mateus", "Natália", "Otávio",
               "Paula", "Rafael", "Sofia", "Thiago", "Vitória", "Yuri"]
LAST_NAMES = ["Silva", "Santos", "Oliveira", "Souza", "Lima", "Pereira", "Costa", "Ferreira",
              "Almeida", "Ribeiro", "Carvalho", "Gomes", "Martins", "Rocha", "Barbosa"]
CITIES = [("São Paulo", "SP", "01310"), ("Rio de Janeiro", "RJ", "20040"),
          ("Belo Horizonte", "MG", "30130"), ("Curitiba", "PR", "80010"),
          ("Porto Alegre", "RS", "90010"), ("Salvador", "BA", "40020"),
          ("Recife", "PE", "50030"), ("Fortaleza", "CE", "60060"), ("Campinas", "SP", "13010"),
          ("Florianópolis", "SC", "88010"), ("Goiânia", "GO", "74003"), ("Brasília", "DF", "70040")]
STREETS = ["Rua das Flores", "Avenida Paulista", "Rua XV de Novembro", "Rua Augusta",
           "Avenida Brasil", "Rua da Consolação", "Rua Sete de Setembro", "Avenida Atlântica"]
STORE_WORDS = ["Casa", "Loja", "Mundo", "Empório", "Ateliê", "Bazar", "Cantinho", "Oficina"]
STORE_THEMES = ["do Lar", "Criativo", "Tech", "da Moda", "Verde", "Esportivo", "Feliz", "Prime"]

# slug -> (display name, [(product base name, min price, max price)])
CATALOG: dict[str, tuple[str, list[tuple[str, float, float]]]] = {
    "cama_mesa_banho": ("Bed, Bath & Table", [("Cotton Bed Sheet Set", 79, 249),
                        ("Bath Towel", 25, 89), ("Decorative Pillow", 30, 120)]),
    "beleza_saude": ("Health & Beauty", [("Moisturizing Cream", 19, 95), ("Hair Dryer", 89, 399),
                     ("Perfume 100ml", 120, 690)]),
    "esporte_lazer": ("Sports & Leisure", [("Yoga Mat", 49, 180), ("Football", 39, 220),
                      ("Adjustable Dumbbell", 150, 890)]),
    "informatica_acessorios": ("Computers & Accessories", [("Wireless Mouse", 39, 199),
                               ("Mechanical Keyboard", 149, 799), ("USB-C Hub", 69, 259),
                               ("27\" Monitor", 999, 2899)]),
    "moveis_decoracao": ("Furniture & Decor", [("Wall Clock", 45, 190), ("Floor Lamp", 120, 560),
                         ("Bookshelf", 280, 1490)]),
    "utilidades_domesticas": ("Housewares", [("Non-stick Pan", 59, 299), ("Glass Food Container", 29, 110),
                              ("Electric Kettle", 89, 349)]),
    "relogios_presentes": ("Watches & Gifts", [("Analog Watch", 129, 1299), ("Gift Box", 39, 189)]),
    "brinquedos": ("Toys", [("Building Blocks Set", 59, 399), ("Plush Bear", 35, 149),
                   ("Puzzle 1000 pieces", 49, 129)]),
    "eletronicos": ("Electronics", [("Bluetooth Speaker", 99, 699), ("Smartwatch", 299, 1899),
                    ("Noise-cancelling Headphones", 349, 2499), ("Gaming Laptop", 6999, 14999)]),
}
ADJECTIVES = ["Classic", "Premium", "Compact", "Deluxe", "Eco", "Pro", "Essential", "Vintage", "Smart"]
COLORS = ["Black", "White", "Blue", "Grey", "Green", "Red", "Beige", "Navy"]
SEARCH_TERMS = ["lamp", "watch", "mouse", "towel", "speaker", "pan", "puzzle", "yoga", "pro", "eco"]
PAYMENT_METHODS = [("CREDIT_CARD", 0.74), ("BOLETO", 0.19), ("VOUCHER", 0.04), ("DEBIT_CARD", 0.03)]
PASSWORD = "sim-password-123"


def weighted(rng: random.Random, pairs):
    items, weights = zip(*pairs)
    return rng.choices(items, weights=weights, k=1)[0]


# --------------------------------------------------------------------------------------------
# Console output
# --------------------------------------------------------------------------------------------

class Out:
    COLORS = {"vendor": "35", "admin": "33", "customer": "36", "order": "32", "ship": "34",
              "warn": "31", "info": "90", "head": "1"}

    def __init__(self, color: bool, verbose: bool):
        self.color = color
        self.verbose = verbose

    def paint(self, kind: str, text: str) -> str:
        return f"\033[{self.COLORS[kind]}m{text}\033[0m" if self.color else text

    def event(self, kind: str, actor: str, text: str):
        stamp = datetime.now().strftime("%H:%M:%S")
        tag = self.paint(kind, f"{kind.upper():<8}")
        print(f"{self.paint('info', stamp)} {tag} {actor:<24.24} {text}", flush=True)

    def debug(self, text: str):
        if self.verbose:
            print(self.paint("info", f"           · {text}"), flush=True)


# --------------------------------------------------------------------------------------------
# HTTP client
# --------------------------------------------------------------------------------------------

class ApiError(Exception):
    def __init__(self, status: int, method: str, path: str, body: Any):
        detail = body.get("detail") or body.get("title") if isinstance(body, dict) else body
        super().__init__(f"{method} {path} -> HTTP {status}: {detail}")
        self.status = status
        self.body = body


class Api:
    def __init__(self, base_url: str, out: Out, timeout: float):
        self.base = base_url.rstrip("/")
        self.out = out
        self.timeout = timeout
        self.http = requests.Session()
        self.http.trust_env = False  # talk to the gateway directly, never through a proxy
        self.calls = Counter()

    def call(self, method: str, path: str, token: str | None = None, json: Any = None,
             params: dict | None = None, expect: tuple[int, ...] = (200, 201, 204)) -> Any:
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        started = time.perf_counter()
        resp = self.http.request(method, self.base + path, headers=headers, json=json,
                                 params=params, timeout=self.timeout)
        ms = (time.perf_counter() - started) * 1000
        self.calls[resp.status_code] += 1
        self.out.debug(f"{method} {path} {resp.status_code} {ms:.0f}ms")
        try:
            body = resp.json() if resp.content else None
        except ValueError:
            body = resp.text
        if resp.status_code not in expect:
            raise ApiError(resp.status_code, method, path, body)
        return body

    def wait_until_up(self, seconds: int):
        deadline = time.time() + seconds
        while True:
            try:
                if self.http.get(self.base + "/api/v1/products", timeout=3).status_code == 200:
                    return
            except requests.RequestException:
                pass
            if time.time() > deadline:
                sys.exit(f"Gateway at {self.base} did not answer within {seconds}s. Is the stack up?")
            time.sleep(2)


# --------------------------------------------------------------------------------------------
# Actors
# --------------------------------------------------------------------------------------------

@dataclass
class User:
    api: Api
    email: str
    name: str
    id: str = ""
    token: str = ""
    token_at: float = 0.0

    def login(self, password: str = PASSWORD):
        body = self.api.call("POST", "/api/v1/auth/login", json={"email": self.email, "password": password})
        self.token, self.token_at = body["accessToken"], time.time()

    def call(self, method: str, path: str, **kw) -> Any:
        # access tokens live 15 minutes: sign in again a little before that, and once on a 401
        if time.time() - self.token_at > 12 * 60:
            self.login(getattr(self, "password", PASSWORD))
        try:
            return self.api.call(method, path, token=self.token, **kw)
        except ApiError as e:
            if e.status != 401:
                raise
            self.login(getattr(self, "password", PASSWORD))
            return self.api.call(method, path, token=self.token, **kw)


@dataclass
class Admin(User):
    password: str = ""


@dataclass
class Vendor(User):
    store: str = ""
    status: str = "PENDING"  # PENDING, APPROVED, REJECTED
    products: list[str] = field(default_factory=list)


@dataclass
class Customer(User):
    city: tuple[str, str, str] = ("São Paulo", "SP", "01310")
    orders: list[str] = field(default_factory=list)


# --------------------------------------------------------------------------------------------
# The simulation
# --------------------------------------------------------------------------------------------

class Simulation:
    def __init__(self, api: Api, out: Out, rng: random.Random, admin_email: str, admin_password: str):
        self.api, self.out, self.rng = api, out, rng
        self.run_id = "".join(rng.choices(string.ascii_lowercase + string.digits, k=5))
        self.admin = Admin(api, admin_email, "admin", password=admin_password)
        self.vendors: list[Vendor] = []
        self.customers: list[Customer] = []
        self.categories: dict[str, str] = {}  # slug -> id
        self.stats = Counter()
        self.revenue = 0.0
        self.seq = 0

    # ---- helpers --------------------------------------------------------------------------

    def unique(self) -> str:
        self.seq += 1
        return f"{self.run_id}{self.seq}"

    def person(self) -> tuple[str, str]:
        name = f"{self.rng.choice(FIRST_NAMES)} {self.rng.choice(LAST_NAMES)}"
        handle = name.lower().replace(" ", ".").encode("ascii", "ignore").decode()
        return name, f"{handle}.{self.unique()}@sim.local"

    def register(self, cls, **extra):
        name, email = self.person()
        user = cls(self.api, email, name, **extra)
        body = self.api.call("POST", "/api/v1/auth/register",
                             json={"email": email, "password": PASSWORD, "fullName": name})
        user.id = body["id"]
        user.login()
        return user

    def approved_vendors(self) -> list[Vendor]:
        return [v for v in self.vendors if v.status == "APPROVED"]

    # ---- setup ----------------------------------------------------------------------------

    def setup(self, start_vendors: int, start_customers: int):
        self.admin.login(self.admin.password)
        self.out.event("admin", "admin", f"signed in as {self.admin.email}")
        existing = self.api.call("GET", "/api/v1/categories")
        existing = existing.get("content", existing) if isinstance(existing, dict) else existing
        self.categories = {c["slug"]: c["id"] for c in existing}
        for slug, (name, _) in CATALOG.items():
            if slug not in self.categories:
                body = self.admin.call("POST", "/api/v1/categories", json={"name": name, "slug": slug})
                self.categories[slug] = body["id"]
                self.out.event("admin", "admin", f"created category {name}")
        for _ in range(start_vendors):
            self.vendor_applies()
        self.admin_reviews(reject_rate=0.0)
        for v in self.approved_vendors():
            for _ in range(self.rng.randint(3, 6)):
                self.vendor_lists_product(v)
        for _ in range(start_customers):
            self.customer_signs_up()

    # ---- vendor side ----------------------------------------------------------------------

    def vendor_applies(self):
        store = f"{self.rng.choice(STORE_WORDS)} {self.rng.choice(STORE_THEMES)} {self.run_id.upper()}{len(self.vendors) + 1}"
        city, state, zip_ = self.rng.choice(CITIES)
        v = self.register(Vendor, store=store)
        v.call("POST", "/api/v1/vendors", json={
            "storeName": store, "description": f"{store}: quality goods from {city}.",
            "contactEmail": v.email, "zipCodePrefix": zip_, "city": city, "state": state})
        self.vendors.append(v)
        self.stats["vendor applications"] += 1
        self.out.event("vendor", store, f"applied to sell ({v.name}, {city}/{state})")

    def admin_reviews(self, reject_rate: float = 0.1):
        pending = [v for v in self.vendors if v.status == "PENDING"]
        for v in pending:
            if self.rng.random() < reject_rate:
                self.admin.call("POST", f"/api/v1/admin/vendors/{v.id}/reject")
                v.status = "REJECTED"
                self.stats["vendors rejected"] += 1
                self.out.event("admin", "admin", f"rejected {v.store}")
            else:
                self.admin.call("POST", f"/api/v1/admin/vendors/{v.id}/approve")
                v.status = "APPROVED"
                v.login()  # fresh token that carries the VENDOR role
                self.stats["vendors approved"] += 1
                self.out.event("admin", "admin", f"approved {v.store}")

    def vendor_lists_product(self, v: Vendor | None = None):
        v = v or self.rng.choice(self.approved_vendors())
        slug = self.rng.choice(list(CATALOG))
        base, lo, hi = self.rng.choice(CATALOG[slug][1])
        name = f"{self.rng.choice(ADJECTIVES)} {base} - {self.rng.choice(COLORS)}"
        price = round(self.rng.uniform(lo, hi), 2)
        body = v.call("POST", "/api/v1/products", json={
            "name": name, "description": f"{name}, sold by {v.store}.", "price": price,
            "stockQuantity": self.rng.randint(5, 60), "categoryId": self.categories[slug],
            "weightGrams": self.rng.randint(100, 8000)})
        v.products.append(body["id"])
        self.stats["products listed"] += 1
        self.out.event("vendor", v.store, f"listed {name} at R$ {price:,.2f} (stock {body['stockQuantity']})")

    def vendor_restocks(self):
        v = self.rng.choice(self.approved_vendors())
        page = v.call("GET", "/api/v1/vendor/products", params={"size": 50})
        low = [p for p in page["content"] if p["stockQuantity"] < 5]
        if not low:
            return False
        p = self.rng.choice(low)
        qty = p["stockQuantity"] + self.rng.randint(10, 40)
        v.call("PUT", f"/api/v1/products/{p['id']}/stock", json={"quantity": qty})
        self.stats["restocks"] += 1
        self.out.event("vendor", v.store, f"restocked {p['name']} {p['stockQuantity']} -> {qty}")

    def vendor_fulfils(self):
        v = self.rng.choice(self.approved_vendors())
        placed = v.call("GET", "/api/v1/vendor/orders", params={"status": "PLACED", "size": 20})["content"]
        shipped = v.call("GET", "/api/v1/vendor/orders", params={"status": "SHIPPED", "size": 20})["content"]
        if not placed and not shipped:
            return False
        for vo in placed[: self.rng.randint(1, 3)]:
            v.call("POST", f"/api/v1/vendor/orders/{vo['id']}/ship")
            self.stats["vendor orders shipped"] += 1
            self.out.event("ship", v.store, f"shipped order {vo['orderId'][:8]} ({len(vo['items'])} line(s))")
        for vo in shipped[: self.rng.randint(0, 2)]:
            v.call("POST", f"/api/v1/vendor/orders/{vo['id']}/deliver")
            self.stats["vendor orders delivered"] += 1
            self.out.event("ship", v.store, f"delivered order {vo['orderId'][:8]}")

    # ---- customer side --------------------------------------------------------------------

    def customer_signs_up(self):
        c = self.register(Customer, city=self.rng.choice(CITIES))
        self.customers.append(c)
        self.stats["customers"] += 1
        self.out.event("customer", c.name, f"signed up from {c.city[0]}/{c.city[1]}")

    def browse(self, c: Customer) -> list[dict]:
        how = self.rng.choice(["search", "category", "cheap", "vendor", "all"])
        params: dict[str, Any] = {"size": 20}
        if how == "search":
            params["q"] = self.rng.choice(SEARCH_TERMS)
            what = f"searched '{params['q']}'"
        elif how == "category":
            slug = self.rng.choice(list(CATALOG))
            params["categoryId"] = self.categories[slug]
            what = f"browsed {CATALOG[slug][0]}"
        elif how == "cheap":
            params.update(maxPrice=100, sort="price,asc")
            what = "looked for deals under R$ 100"
        elif how == "vendor" and self.approved_vendors():
            v = self.rng.choice(self.approved_vendors())
            params["sellerId"] = v.id
            self.api.call("GET", f"/api/v1/vendors/{v.id}")
            what = f"visited {v.store}"
        else:
            what = "browsed the home page"
        page = self.api.call("GET", "/api/v1/products", params=params)
        products = [p for p in page["content"] if p["stockQuantity"] > 0]
        self.stats["browse sessions"] += 1
        self.out.event("customer", c.name, f"{what}: {page['totalElements']} result(s)")
        return products

    def customer_shops(self):
        c = self.rng.choice(self.customers)
        products = self.browse(c)
        if not products:
            return
        viewed = self.rng.sample(products, k=min(len(products), self.rng.randint(1, 4)))
        for p in viewed:
            self.api.call("GET", f"/api/v1/products/{p['id']}")
        self.stats["product views"] += len(viewed)
        if self.rng.random() < 0.35:
            self.out.event("customer", c.name, f"looked at {len(viewed)} product(s), bought nothing")
            return
        for p in viewed[: self.rng.randint(1, len(viewed))]:
            qty = 1 if p["price"] > 300 else self.rng.choice([1, 1, 1, 2, 3])
            qty = min(qty, p["stockQuantity"])
            c.call("POST", "/api/v1/cart/items", json={"productId": p["id"], "quantity": qty})
            self.stats["cart adds"] += 1
            self.out.event("customer", c.name, f"added {qty} x {p['name']} to cart")
        self.maybe_checkout(c)

    def maybe_checkout(self, c: Customer):
        cart = c.call("GET", "/api/v1/cart")
        if not cart["items"]:
            return
        if self.rng.random() < 0.25:
            self.stats["carts abandoned"] += 1
            self.out.event("customer", c.name, f"left {len(cart['items'])} item(s) in the cart for later")
            return
        method = weighted(self.rng, PAYMENT_METHODS)
        installments = self.rng.choice([1, 1, 2, 3, 4, 6, 10]) if method == "CREDIT_CARD" else 1
        city, state, zip_ = c.city
        try:
            order = c.call("POST", "/api/v1/orders/checkout", json={
                "shippingAddress": {"recipient": c.name, "street": f"{self.rng.choice(STREETS)}, {self.rng.randint(1, 2000)}",
                                    "city": city, "state": state, "zipCodePrefix": zip_},
                "paymentMethod": method, "installments": installments})
        except ApiError as e:
            if e.status == 402:
                self.stats["payments declined"] += 1
                self.out.event("warn", c.name, f"payment declined ({method}), gave up and emptied the cart")
                c.call("DELETE", "/api/v1/cart")
                return
            if e.status == 409:
                self.stats["checkouts out of stock"] += 1
                self.out.event("warn", c.name, "checkout failed: something sold out, emptied cart")
                c.call("DELETE", "/api/v1/cart")
                return
            raise
        c.orders.append(order["id"])
        self.revenue += float(order["totalAmount"])
        self.stats["orders placed"] += 1
        self.out.event("order", c.name, f"checked out order {order['id'][:8]}: R$ {float(order['totalAmount']):,.2f} "
                       f"via {method}{f' x{installments}' if installments > 1 else ''}, "
                       f"{len(order['vendorOrders'])} seller(s)")

    def customer_cancels(self):
        candidates = [c for c in self.customers if c.orders]
        if not candidates:
            return False
        c = self.rng.choice(candidates)
        order_id = self.rng.choice(c.orders)
        order = c.call("GET", f"/api/v1/orders/{order_id}")
        if order["status"] != "PLACED":
            return False
        c.call("POST", f"/api/v1/orders/{order_id}/cancel")
        c.orders.remove(order_id)
        self.stats["orders cancelled"] += 1
        self.out.event("warn", c.name, f"cancelled order {order_id[:8]} (refunded, stock released)")

    def customer_checks_orders(self):
        candidates = [c for c in self.customers if c.orders]
        if not candidates:
            return False
        c = self.rng.choice(candidates)
        page = c.call("GET", "/api/v1/orders", params={"size": 10})
        statuses = Counter(o["status"] for o in page["content"])
        self.out.event("customer", c.name, "checked orders: " + ", ".join(f"{n} {s}" for s, n in statuses.items()))

    # ---- main loop ------------------------------------------------------------------------

    def step(self):
        actions: list[tuple[Callable, float]] = [
            (self.customer_shops, 40), (self.vendor_fulfils, 18), (self.customer_signs_up, 8),
            (self.vendor_lists_product, 8), (self.customer_checks_orders, 6), (self.vendor_restocks, 5),
            (self.vendor_applies, 3), (self.customer_cancels, 2),
        ]
        if any(v.status == "PENDING" for v in self.vendors):
            actions.append((self.admin_reviews, 6))
        if not self.approved_vendors():
            actions = [(self.vendor_applies, 1), (self.admin_reviews, 1)]
        action = weighted(self.rng, actions)
        try:
            action()
        except ApiError as e:
            self.stats["unexpected errors"] += 1
            self.out.event("warn", action.__name__, str(e))
        except requests.RequestException as e:
            self.stats["network errors"] += 1
            self.out.event("warn", action.__name__, f"network error: {e}")
            time.sleep(2)

    def summary(self, started: float, steps: int):
        mins = (time.time() - started) / 60
        print()
        print(self.out.paint("head", f"Summary after {steps} action(s), {mins:.1f} min (run id {self.run_id})"))
        for k, v in sorted(self.stats.items()):
            print(f"  {k:<26} {v}")
        print(f"  {'gross order value':<26} R$ {self.revenue:,.2f}")
        codes = ", ".join(f"{code}: {n}" for code, n in sorted(self.api.calls.items()))
        print(f"  {'HTTP responses':<26} {codes}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-url", default=os.getenv("SIM_BASE_URL", "http://localhost:8080"),
                    help="gateway URL (env SIM_BASE_URL, default http://localhost:8080)")
    ap.add_argument("--admin-email", default=os.getenv("BOOTSTRAP_ADMIN_EMAIL", "admin@ec.local"))
    ap.add_argument("--admin-password", default=os.getenv("BOOTSTRAP_ADMIN_PASSWORD", "change-me-admin"))
    ap.add_argument("-n", "--iterations", type=int, default=0, help="number of actions; 0 runs until Ctrl+C (default)")
    ap.add_argument("--delay", type=float, default=1.0, help="average pause between actions in seconds (default 1.0)")
    ap.add_argument("--vendors", type=int, default=3, help="sellers to onboard before the loop starts (default 3)")
    ap.add_argument("--customers", type=int, default=5, help="customers to sign up before the loop starts (default 5)")
    ap.add_argument("--seed", type=int, help="random seed, for a repeatable run")
    ap.add_argument("--timeout", type=float, default=15, help="HTTP timeout in seconds (default 15)")
    ap.add_argument("--wait", type=int, default=120, help="seconds to wait for the gateway to come up (default 120)")
    ap.add_argument("--summary-every", type=int, default=50, help="print a summary every N actions (0 = only at the end)")
    ap.add_argument("-v", "--verbose", action="store_true", help="also print every HTTP call with status and latency")
    ap.add_argument("--no-color", action="store_true", help="plain output, no ANSI colours")
    args = ap.parse_args()

    out = Out(color=sys.stdout.isatty() and not args.no_color, verbose=args.verbose)
    rng = random.Random(args.seed)
    api = Api(args.base_url, out, args.timeout)
    print(out.paint("head", f"EC Lakehouse traffic simulator -> {api.base}"))
    api.wait_until_up(args.wait)
    sim = Simulation(api, out, rng, args.admin_email, args.admin_password)

    stop = False

    def on_sigint(*_):
        nonlocal stop
        if stop:
            sys.exit(130)
        stop = True
        print("\nStopping after this action (Ctrl+C again to quit now)...")

    signal.signal(signal.SIGINT, on_sigint)
    started, steps = time.time(), 0
    try:
        sim.setup(args.vendors, args.customers)
    except ApiError as e:
        if e.status == 401 and "/auth/login" in str(e):
            sys.exit(f"Admin login failed for {args.admin_email}. Pass --admin-email/--admin-password "
                     "or set BOOTSTRAP_ADMIN_EMAIL/BOOTSTRAP_ADMIN_PASSWORD.")
        raise
    while not stop and (args.iterations == 0 or steps < args.iterations):
        sim.step()
        steps += 1
        if args.summary_every and steps % args.summary_every == 0 and not (args.iterations and steps == args.iterations):
            sim.summary(started, steps)
            print()
        if args.delay > 0:
            time.sleep(rng.uniform(0.3, 1.7) * args.delay)
    sim.summary(started, steps)
    errors = sim.stats["unexpected errors"] + sim.stats["network errors"]
    sys.exit(1 if errors and args.iterations else 0)


if __name__ == "__main__":
    main()
