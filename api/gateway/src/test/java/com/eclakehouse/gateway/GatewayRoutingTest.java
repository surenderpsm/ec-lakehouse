package com.eclakehouse.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

/** Every service URL points at one fake backend that echoes the path it received. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    static final HttpServer backend = start();

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + backend.getAddress().getPort();
        for (String s : new String[] { "AUTH", "VENDOR", "CATALOG", "CART", "ORDER", "PAYMENT" }) {
            registry.add(s + "_SERVICE_URL", () -> url);
        }
    }

    @AfterAll
    static void stop() {
        backend.stop(0);
    }

    @Test
    void routesPublicApiPaths() throws Exception {
        for (String path : new String[] { "/api/v1/auth/login", "/api/v1/products", "/api/v1/cart",
                "/api/v1/orders/123", "/api/v1/vendor/payouts", "/api/v1/admin/vendors" }) {
            HttpResponse<String> res = get(path);
            assertThat(res.statusCode()).as(path).isEqualTo(200);
            assertThat(res.body()).isEqualTo(path);
        }
    }

    @Test
    void doesNotExposeInternalEndpoints() throws Exception {
        assertThat(get("/internal/inventory/reserve").statusCode()).isEqualTo(404);
        assertThat(get("/internal/payments").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = exchange.getRequestURI().getPath().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
