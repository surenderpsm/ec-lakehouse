package com.eclakehouse.vendor;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static com.eclakehouse.common.test.TestJwt.user;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.eclakehouse.common.test.PostgresTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class VendorApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuthClient authClient;

    @Test
    void applyApproveAndShowPublicly() throws Exception {
        UUID userId = UUID.randomUUID();
        String store = "Loja " + userId;

        mvc.perform(post("/api/v1/vendors").with(user(userId, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"storeName":"%s","contactEmail":"loja@test.local","city":"sao paulo","state":"sp"}"""
                        .formatted(store)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.state").value("SP"));

        // not visible to the public until approved
        mvc.perform(get("/api/v1/vendors/{id}", userId)).andExpect(status().isNotFound());

        // customers can't approve
        mvc.perform(post("/api/v1/admin/vendors/{id}/approve", userId).with(user(userId, "CUSTOMER")))
                .andExpect(status().isForbidden());
        verify(authClient, never()).grantVendorRole(userId);

        mvc.perform(post("/api/v1/admin/vendors/{id}/approve", userId).with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(authClient).grantVendorRole(userId);

        mvc.perform(get("/api/v1/vendors/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value(store))
                .andExpect(jsonPath("$.contactEmail").doesNotExist());

        mvc.perform(post("/api/v1/admin/vendors/{id}/suspend", userId).with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        verify(authClient).revokeVendorRole(userId);
    }

    @Test
    void cannotApplyTwiceOrReuseStoreName() throws Exception {
        UUID first = UUID.randomUUID();
        String body = """
                {"storeName":"Same Name %s","contactEmail":"a@test.local"}""".formatted(first);
        mvc.perform(post("/api/v1/vendors").with(user(first, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/vendors").with(user(first, "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/vendors").with(user(UUID.randomUUID(), "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void applyingRequiresSignIn() throws Exception {
        mvc.perform(post("/api/v1/vendors").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

}
