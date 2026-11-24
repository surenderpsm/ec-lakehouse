package com.eclakehouse.catalog;

import static com.eclakehouse.common.test.TestJwt.user;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.eclakehouse.common.test.PostgresTestConfig;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CatalogApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void vendorListsProductAndCustomersFindIt() throws Exception {
        UUID seller = UUID.randomUUID();
        String slug = "toys-" + UUID.randomUUID().toString().substring(0, 8);
        String category = mvc.perform(post("/api/v1/categories").with(user(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Toys\",\"slug\":\"%s\"}".formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String categoryId = JsonPath.read(category, "$.id");

        String marker = "Robot" + UUID.randomUUID().toString().substring(0, 6);
        String productId = createProduct(seller, marker + " kit", "49.90", 5, categoryId);

        mvc.perform(get("/api/v1/products").param("q", marker.toLowerCase()).param("maxPrice", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].sellerId").value(seller.toString()));
        mvc.perform(get("/api/v1/products").param("q", marker).param("minPrice", "60"))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mvc.perform(get("/api/v1/products").param("categoryId", categoryId))
                .andExpect(jsonPath("$.content", hasSize(1)));

        // hiding it removes it from public views but not from the vendor's own list
        mvc.perform(delete("/api/v1/products/{id}", productId).with(user(seller, "VENDOR")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vendor/products").with(user(seller, "VENDOR")))
                .andExpect(jsonPath("$.content[0].status").value("INACTIVE"));
    }

    @Test
    void onlyVendorsCreateAndOnlyOwnersEdit() throws Exception {
        mvc.perform(post("/api/v1/products").with(user(UUID.randomUUID(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(productJson("Mug", "10.00", 1, null)))
                .andExpect(status().isForbidden());

        UUID owner = UUID.randomUUID();
        String productId = createProduct(owner, "Mug", "10.00", 1, null);
        mvc.perform(put("/api/v1/products/{id}/stock", productId).with(user(UUID.randomUUID(), "VENDOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/products/{id}/stock", productId).with(user(owner, "VENDOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(100));

        mvc.perform(post("/api/v1/products").with(user(owner, "VENDOR"))
                .contentType(MediaType.APPLICATION_JSON).content(productJson("", "-1", 1, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveIsAllOrNothingAndReleaseGivesStockBack() throws Exception {
        UUID seller = UUID.randomUUID();
        String a = createProduct(seller, "A", "5.00", 3, null);
        String b = createProduct(seller, "B", "7.50", 1, null);
        UUID customer = UUID.randomUUID();

        // asking for 2 of B fails, and A's stock must be untouched
        mvc.perform(post("/internal/inventory/reserve").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(items(a, 2, b, 2)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/products/{id}", a)).andExpect(jsonPath("$.stockQuantity").value(3));

        mvc.perform(post("/internal/inventory/reserve").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(items(a, 2, b, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.productId=='%s')].unitPrice".formatted(b)).value(7.5));
        mvc.perform(get("/api/v1/products/{id}", a)).andExpect(jsonPath("$.stockQuantity").value(1));
        mvc.perform(get("/api/v1/products/{id}", b)).andExpect(jsonPath("$.stockQuantity").value(0));

        mvc.perform(post("/internal/inventory/release").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(items(a, 2, b, 1)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products/{id}", a)).andExpect(jsonPath("$.stockQuantity").value(3));

        mvc.perform(post("/internal/inventory/reserve").contentType(MediaType.APPLICATION_JSON)
                .content(items(a, 1, b, 1)))
                .andExpect(status().isUnauthorized());
    }

    private String createProduct(UUID seller, String name, String price, int stock, String categoryId)
            throws Exception {
        String body = mvc.perform(post("/api/v1/products").with(user(seller, "VENDOR"))
                .contentType(MediaType.APPLICATION_JSON).content(productJson(name, price, stock, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String productJson(String name, String price, int stock, String categoryId) {
        return """
                {"name":"%s","price":%s,"stockQuantity":%d,"categoryId":%s}"""
                .formatted(name, price, stock, categoryId == null ? "null" : "\"" + categoryId + "\"");
    }

    private static String items(String p1, int q1, String p2, int q2) {
        return """
                {"items":[{"productId":"%s","quantity":%d},{"productId":"%s","quantity":%d}]}"""
                .formatted(p1, q1, p2, q2);
    }
}
