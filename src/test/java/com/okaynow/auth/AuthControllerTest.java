package com.okaynow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerBody(String email, String role) {
        String clientFields = "CLIENT".equals(role)
                ? """
                  ,
                  "registeringForSelf": true
                  """
                : "";
        return """
                {
                  "email": "%s",
                  "password": "password123",
                  "phone": "617-555-0100",
                  "role": "%s",
                  "firstName": "Jane",
                  "lastName": "Doe"%s
                }
                """.formatted(email, role, clientFields);
    }

    @Test
    void registerCaregiverReturnsTokens() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("caregiver1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("CAREGIVER"));
    }

    @Test
    void publicAdminRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("sneaky-admin@example.com", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailRegistrationFails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dupe@example.com", "CLIENT")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("dupe@example.com", "CLIENT")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithValidCredentialsSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("login1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login1@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("login2@example.com", "CAREGIVER")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login2@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIssuesNewAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("refresh1@example.com", "CLIENT")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String refreshToken = body.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void accessTokenGrantsAccessToProtectedEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("me1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.get("accessToken").asText();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me1@example.com"));
    }

    @Test
    void protectedEndpointWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("me2@example.com", "CAREGIVER")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String refreshToken = body.get("refreshToken").asText();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }
}
