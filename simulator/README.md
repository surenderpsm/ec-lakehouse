# Traffic simulator

A Python script that plays a small marketplace against the API's gateway, so you can watch the
whole system work and fill its databases with realistic data. It only uses the public
`/api/v1/**` endpoints, the same way a real client would.

What happens:

- **Sellers** sign up, apply to sell, wait for the admin, list products across Olist-style
  categories (`cama_mesa_banho`, `eletronicos`, ...), restock when they run low, and ship and
  deliver their part of each order.
- **The admin** creates the categories if they're missing and approves (sometimes rejects)
  seller applications.
- **Customers** sign up from Brazilian cities, browse (search, category, deals, a seller's store),
  view products, add to cart, sometimes walk away, check out with Olist's payment mix
  (mostly credit card with installments, then boleto), occasionally cancel, and check their
  orders. Now and then a basket goes over the fake provider's limit and the payment is declined.

## Run it

Start the API first (see [`api/README.md`](../api/README.md)), then:

```bash
cd simulator
pip install -r requirements.txt       # just `requests`
python simulate.py                    # runs until Ctrl+C, about one action a second
```

Sample output:

```
05:39:09 VENDOR   Bazar do Lar LFXCT1      listed Deluxe 27" Monitor - Green at R$ 2,454.13 (stock 17)
05:39:09 CUSTOMER Camila Oliveira          searched 'eco': 3 result(s)
05:39:09 CUSTOMER Camila Oliveira          added 3 x Eco Plush Bear - Grey to cart
05:39:09 ORDER    Camila Oliveira          checked out order 4dca97b1: R$ 846.91 via CREDIT_CARD, 2 seller(s)
05:39:11 SHIP     Loja Feliz LFXCT2        shipped order 4dca97b1 (2 line(s))
05:39:34 WARN     Sofia Pereira            payment declined (CREDIT_CARD), gave up and emptied the cart
```

A summary (counts per activity, gross order value, HTTP status codes) prints every 50 actions
and when it stops. The first Ctrl+C finishes the current action and prints the summary.

## Options

| Option | Default | |
|---|---|---|
| `--base-url` | `http://localhost:8080` | Gateway URL. Env `SIM_BASE_URL` |
| `--admin-email` | `admin@ec.local` | Env `BOOTSTRAP_ADMIN_EMAIL` (same name as the API's `.env`) |
| `--admin-password` | `change-me-admin` | Env `BOOTSTRAP_ADMIN_PASSWORD` |
| `-n`, `--iterations` | `0` | Number of actions, `0` runs until Ctrl+C |
| `--delay` | `1.0` | Average seconds between actions (randomised ±70%). `0` for as fast as possible |
| `--vendors` | `3` | Sellers onboarded before the loop starts |
| `--customers` | `5` | Customers signed up before the loop starts |
| `--seed` | random | Seed for a repeatable mix of actions |
| `--summary-every` | `50` | Summary every N actions, `0` only at the end |
| `--wait` | `120` | Seconds to wait for the gateway to come up |
| `-v`, `--verbose` | off | Also print each HTTP call with status and latency |
| `--no-color` | off | Plain output (colour is off anyway when not on a terminal) |

Examples:

```bash
python simulate.py -n 500 --delay 0 --seed 42          # quick, repeatable seed of ~150 orders
python simulate.py --base-url http://10.0.0.5:8080     # against a deployed stack
BOOTSTRAP_ADMIN_PASSWORD=secret python simulate.py -v  # custom admin, show every request
```

With `-n`, the exit code is 1 if any unexpected API error happened, so it can double as a load
smoke test.

## Notes

- Each run creates new users (`*@sim.local`, password `sim-password-123`) and stores tagged
  with a short run id, so runs never collide and you can tell them apart in the data.
- Users live only in memory: a new run doesn't reuse sellers or customers from an earlier run,
  but everything it created stays in the databases.
- Access tokens last 15 minutes, so long runs sign each user in again before they expire.
- Clickstream and event capture are planned for v2 of the API. This script only drives the REST
  API today. Its actions are a natural place to emit page-view events later.
