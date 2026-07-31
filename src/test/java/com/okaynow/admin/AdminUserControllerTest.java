package com.okaynow.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminEmail;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "owner+" + System.nanoTime() + "@example.com";
        userRepository.save(User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        adminToken = login(adminEmail, "password123");
    }

    @Test
    void ownerCanSearchAndSuspendUsers() throws Exception {
        User caregiver = userRepository.save(User.builder()
                .email("managed+" + System.nanoTime() + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.CAREGIVER)
                .status(UserStatus.ACTIVE)
                .build());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("role", "CAREGIVER")
                        .param("search", "managed+"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        mockMvc.perform(patch("/api/admin/users/" + caregiver.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void ownerCannotSuspendSelf() throws Exception {
        User owner = userRepository.findByEmail(adminEmail).orElseThrow();
        mockMvc.perform(patch("/api/admin/users/" + owner.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerCanCreateAnotherOwner() throws Exception {
        mockMvc.perform(post("/api/admin/users/owners")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "second-%d@example.com",
                                  "password": "long-password-123"
                                }
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void anonymousCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
