package com.okaynow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.mail.CapturingEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

    @BeforeEach
    void clearMail() {
        CapturingEmailSender.clear();
    }

    private List<UUID> currentLegalIds() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/legal/current"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
        List<UUID> ids = new ArrayList<>();
        for (JsonNode n : arr) {
            ids.add(UUID.fromString(n.get("id").asText()));
        }
        return ids;
    }

    private String registerBody(String email, String role) throws Exception {
        List<UUID> legalIds = currentLegalIds();
        StringBuilder legalJson = new StringBuilder("[");
        for (int i = 0; i < legalIds.size(); i++) {
            if (i > 0) legalJson.append(',');
            legalJson.append('"').append(legalIds.get(i)).append('"');
        }
        legalJson.append(']');

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
                  "lastName": "Doe",
                  "acceptedLegalDocumentIds": %s%s
                }
                """.formatted(email, role, legalJson, clientFields);
    }

    @Test
    void registerCaregiverRequiresEmailVerification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("caregiver1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiresEmailVerification").value(true))
                .andExpect(jsonPath("$.email").value("caregiver1@example.com"));

        String code = CapturingEmailSender.lastCode();
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"caregiver1@example.com","code":"%s"}
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
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
    void loginWithValidCredentialsSucceedsAfterVerification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("login1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login1@example.com","code":"%s"}
                                """.formatted(CapturingEmailSender.lastCode())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login1@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(false))
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("login2@example.com", "CAREGIVER")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login2@example.com","code":"%s"}
                                """.formatted(CapturingEmailSender.lastCode())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login2@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlowWorks() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("reset1@example.com", "CAREGIVER")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset1@example.com","code":"%s"}
                                """.formatted(CapturingEmailSender.lastCode())))
                .andExpect(status().isOk());

        CapturingEmailSender.clear();
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset1@example.com"}
                                """))
                .andExpect(status().isOk());

        String resetCode = CapturingEmailSender.lastCode();
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset1@example.com","code":"%s","newPassword":"newpassword99"}
                                """.formatted(resetCode)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset1@example.com","password":"newpassword99"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void refreshTokenIssuesNewAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("refresh1@example.com", "CLIENT")))
                .andExpect(status().isCreated());
        MvcResult verified = mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"refresh1@example.com","code":"%s"}
                                """.formatted(CapturingEmailSender.lastCode())))
                .andExpect(status().isOk())
                .andReturn();

        String refresh = objectMapper.readTree(verified.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    void adminLoginRequiresOtp() throws Exception {
        // Bootstrap admin from application-test.yml if present; otherwise create via register rejection path
        // Use bootstrap credentials from default app config in tests — may not be set.
        // Create admin directly isn't public; verify OTP path via AuthService after ensuring bootstrap.
        // Skip if no bootstrap — mark with password reset style using existing user creation through repository is overkill.
        // Instead: register caregiver then promote isn't available. Use forgot-password independence.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@okaynow.com","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.accessToken", nullValue()));

        String otp = CapturingEmailSender.lastCode();
        mockMvc.perform(post("/api/auth/verify-login-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@okaynow.com","code":"%s"}
                                """.formatted(otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
