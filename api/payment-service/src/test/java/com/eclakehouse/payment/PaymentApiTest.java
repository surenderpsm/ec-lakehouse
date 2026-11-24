package com.eclakehouse.payment;

import static com.eclakehouse.common.test.TestJwt.user;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(properties = "ec.payment.fake.decline-above=1000")
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class PaymentApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void authorizesAndSplitsPayoutsWithCommission() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        String body = request(order, "150.00", sellerA, "100.00", sellerB, "50.00");

        mvc.perform(post("/internal/payments").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.payouts", hasSize(2)));

        // same order again returns the same payment instead of charging twice
        mvc.perform(post("/internal/payments").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.payouts", hasSize(2)));

        mvc.perform(get("/api/v1/vendor/payouts").with(user(sellerA, "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].commission").value(10.0))
                .andExpect(jsonPath("$.content[0].netAmount").value(90.0));

        mvc.perform(get("/api/v1/payments/orders/{id}", order).with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/internal/payments/{id}/refund", order).with(user(customer, "CUSTOMER")))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.payouts[0].status").value("CANCELLED"));
    }

    @Test
    void declinesAboveLimitAndRejectsBadSplits() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        mvc.perform(post("/internal/payments").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(UUID.randomUUID(), "5000.00", seller, "5000.00", null, null)))
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.payouts", hasSize(0)));

        mvc.perform(post("/internal/payments").with(user(customer, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(UUID.randomUUID(), "100.00", seller, "90.00", null, null)))
                .andExpect(status().isBadRequest());
    }

    private static String request(UUID order, String amount, UUID s1, String a1, UUID s2, String a2) {
        String splits = "{\"sellerId\":\"%s\",\"amount\":%s}".formatted(s1, a1)
                + (s2 == null ? "" : ",{\"sellerId\":\"%s\",\"amount\":%s}".formatted(s2, a2));
        return """
                {"orderId":"%s","amount":%s,"method":"CREDIT_CARD","installments":3,"splits":[%s]}"""
                .formatted(order, amount, splits);
    }
}
