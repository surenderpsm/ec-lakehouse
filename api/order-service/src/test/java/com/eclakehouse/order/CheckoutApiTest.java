package com.eclakehouse.order;

import static com.eclakehouse.common.test.TestJwt.user;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.eclakehouse.common.test.PostgresTestConfig;
import com.eclakehouse.order.client.CartClient;
import com.eclakehouse.order.client.Clients.Cart;
import com.eclakehouse.order.client.Clients.CartItem;
import com.eclakehouse.order.client.Clients.PaymentResult;
import com.eclakehouse.order.client.Clients.ReservedItem;
import com.eclakehouse.order.client.InventoryClient;
import com.eclakehouse.order.client.PaymentClient;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class CheckoutApiTest {

    private static final String CHECKOUT = """
            {"shippingAddress":{"recipient":"Ana","street":"Rua A, 1","city":"Sao Paulo","state":"sp",
             "zipCodePrefix":"01310"},"paymentMethod":"CREDIT_CARD","installments":2}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CartClient cart;

    @MockitoBean
    InventoryClient inventory;

    @MockitoBean
    PaymentClient payments;

    final UUID customer = UUID.randomUUID();
    final UUID sellerA = UUID.randomUUID();
    final UUID sellerB = UUID.randomUUID();
    final UUID lamp = UUID.randomUUID();
    final UUID book = UUID.randomUUID();

    @Test
    void checkoutSplitsOrderPerVendorAndVendorsFulfilTheirPart() throws Exception {
        givenCartWithTwoVendors();
        when(payments.authorize(any(), any(), anyString(), anyInt(), anyList()))
                .thenReturn(new PaymentResult(UUID.randomUUID(), "AUTHORIZED", null));

        String body = mvc.perform(post("/api/v1/orders/checkout").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.totalAmount").value(75.0))
                .andExpect(jsonPath("$.vendorOrders", hasSize(2)))
                .andExpect(jsonPath("$.shippingAddress.state").value("SP"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(body, "$.id");
        verify(cart).clear();
        verify(payments).authorize(any(), eq(new BigDecimal("75.00")), eq("CREDIT_CARD"), eq(2), anyList());

        // seller A sees only their own sub-order
        String aOrders = mvc.perform(get("/api/v1/vendor/orders").param("status", "PLACED").with(user(sellerA, "VENDOR")))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].subtotal").value(40.0))
                .andExpect(jsonPath("$.content[0].items[0].productName").value("Lamp"))
                .andReturn().getResponse().getContentAsString();
        String aSubOrder = JsonPath.read(aOrders, "$.content[0].id");

        // seller B can't touch seller A's sub-order
        mvc.perform(post("/api/v1/vendor/orders/{id}/ship", aSubOrder).with(user(sellerB, "VENDOR")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/vendor/orders/{id}/ship", aSubOrder).with(user(sellerA, "VENDOR")))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(user(customer, "CUSTOMER")))
                .andExpect(jsonPath("$.status").value("PARTIALLY_SHIPPED"));

        // once anything shipped, the customer can no longer cancel
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(customer, "CUSTOMER")))
                .andExpect(status().isConflict());

        String bSubOrder = JsonPath.read(mvc.perform(get("/api/v1/vendor/orders").with(user(sellerB, "VENDOR")))
                .andReturn().getResponse().getContentAsString(), "$.content[0].id");
        mvc.perform(post("/api/v1/vendor/orders/{id}/ship", bSubOrder).with(user(sellerB, "VENDOR")));
        mvc.perform(post("/api/v1/vendor/orders/{id}/deliver", aSubOrder).with(user(sellerA, "VENDOR")));
        mvc.perform(post("/api/v1/vendor/orders/{id}/deliver", bSubOrder).with(user(sellerB, "VENDOR")))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(user(customer, "CUSTOMER")))
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // other customers can't see it
        mvc.perform(get("/api/v1/orders/{id}", orderId).with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void declinedPaymentReleasesStockAndKeepsCart() throws Exception {
        givenCartWithTwoVendors();
        when(payments.authorize(any(), any(), anyString(), anyInt(), anyList()))
                .thenReturn(new PaymentResult(UUID.randomUUID(), "DECLINED", "card refused"));

        mvc.perform(post("/api/v1/orders/checkout").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isPaymentRequired());

        verify(inventory).release(anyList());
        verify(cart, never()).clear();
        mvc.perform(get("/api/v1/orders").with(user(customer, "CUSTOMER")))
                .andExpect(jsonPath("$.content[0].status").value("PAYMENT_FAILED"));
    }

    @Test
    void cancelRefundsAndReleasesStock() throws Exception {
        givenCartWithTwoVendors();
        when(payments.authorize(any(), any(), anyString(), anyInt(), anyList()))
                .thenReturn(new PaymentResult(UUID.randomUUID(), "AUTHORIZED", null));
        String orderId = JsonPath.read(mvc.perform(post("/api/v1/orders/checkout")
                .with(user(customer, "CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).with(user(customer, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.vendorOrders[0].status").value("CANCELLED"));
        verify(payments).refund(UUID.fromString(orderId));
        verify(inventory).release(anyList());
    }

    @Test
    void emptyCartAndBadInputAreRejected() throws Exception {
        when(cart.getCart()).thenReturn(new Cart(List.of()));
        mvc.perform(post("/api/v1/orders/checkout").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(CHECKOUT))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders/checkout").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
        verify(inventory, never()).reserve(anyList());
    }

    private void givenCartWithTwoVendors() {
        when(cart.getCart()).thenReturn(new Cart(List.of(
                new CartItem(lamp, sellerA, 2), new CartItem(book, sellerB, 1))));
        when(inventory.reserve(anyList())).thenReturn(List.of(
                new ReservedItem(lamp, sellerA, "Lamp", new BigDecimal("20.00"), 2),
                new ReservedItem(book, sellerB, "Book", new BigDecimal("35.00"), 1)));
    }
}
