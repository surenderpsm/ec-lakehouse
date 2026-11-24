package com.eclakehouse.cart;

import static com.eclakehouse.common.test.TestJwt.user;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import com.eclakehouse.cart.CatalogClient.ProductView;
import com.eclakehouse.common.test.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CartApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CatalogClient catalog;

    @Test
    void cartGroupsItemsByVendorAndTotals() throws Exception {
        UUID me = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        UUID p1 = product(sellerA, "Lamp", "20.00", 10);
        UUID p2 = product(sellerA, "Bulb", "2.50", 10);
        UUID p3 = product(sellerB, "Book", "35.00", 1);

        add(me, p1, 1);
        add(me, p1, 1); // adds up to 2
        add(me, p2, 4);
        add(me, p3, 1)
                .andExpect(jsonPath("$.vendors", hasSize(2)))
                .andExpect(jsonPath("$.itemCount").value(7))
                .andExpect(jsonPath("$.total").value(85.0))
                .andExpect(jsonPath("$.vendors[0].sellerId").value(sellerA.toString()))
                .andExpect(jsonPath("$.vendors[0].subtotal").value(50.0));

        mvc.perform(put("/api/v1/cart/items/{id}", p2).with(user(me, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        // someone else's cart is separate
        mvc.perform(get("/api/v1/cart").with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(delete("/api/v1/cart").with(user(me, "CUSTOMER"))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart").with(user(me, "CUSTOMER")))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsMoreThanStockAndUnknownProducts() throws Exception {
        UUID me = UUID.randomUUID();
        UUID scarce = product(UUID.randomUUID(), "Rare", "99.00", 1);
        add(me, scarce, 2).andExpect(status().isConflict());

        UUID missing = UUID.randomUUID();
        when(catalog.getProduct(missing)).thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND,
                "Not Found", null, null, null));
        add(me, missing, 1).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
    }

    private UUID product(UUID seller, String name, String price, int stock) {
        UUID id = UUID.randomUUID();
        when(catalog.getProduct(id)).thenReturn(new ProductView(id, seller, name, new BigDecimal(price), stock,
                "ACTIVE"));
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions add(UUID me, UUID productId, int qty)
            throws Exception {
        return mvc.perform(post("/api/v1/cart/items").with(user(me, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"%s\",\"quantity\":%d}".formatted(productId, qty)));
    }
}
