package com.eclakehouse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = {
        "ec.auth.bootstrap-admin.email=admin@test.local",
        "ec.auth.bootstrap-admin.password=admin-password"
})
@AutoConfigureMockMvc
@Import(PostgresTestConfig.class)
class AuthFlowTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registerLoginMeRefreshLogout() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"secret-pass","fullName":"Ana Souza"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));

        String tokens = login(email, "secret-pass");
        String access = JsonPath.read(tokens, "$.accessToken");
        String refresh = JsonPath.read(tokens, "$.refreshToken");

        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Ana Souza"));

        String rotated = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(rotated, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(refresh);

        // the old refresh token was rotated out
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(newRefresh)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateEmailWrongPasswordAndBadInput() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.local";
        String body = """
                {"email":"%s","password":"secret-pass","fullName":"Dup"}""".formatted(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"wrong-pass\"}".formatted(email)))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminsCanGrantRoles() throws Exception {
        String email = "vendor-" + UUID.randomUUID() + "@test.local";
        String created = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"secret-pass\",\"fullName\":\"V\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(created, "$.id");
        String userToken = JsonPath.read(login(email, "secret-pass"), "$.accessToken");
        String adminToken = JsonPath.read(login("admin@test.local", "admin-password"), "$.accessToken");

        String grant = "{\"role\":\"VENDOR\"}";
        mvc.perform(post("/api/v1/admin/users/{id}/roles", userId).header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON).content(grant))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/{id}/roles", userId).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(grant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER", "VENDOR")));
    }

    @Test
    void publishesJwksAndRejectsMissingToken() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
