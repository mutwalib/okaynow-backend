package com.okaynow.staffing;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientStaffingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "staff-admin+" + System.nanoTime() + "@example.com";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        adminToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void ownerCanRecruitPrimaryAndRotationalCaregiversOnClient() throws Exception {
        String clientEmail = "client+" + System.nanoTime() + "@example.com";
        MvcResult clientResult = mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "temporary123",
                                  "firstName": "Pat",
                                  "lastName": "Client",
                                  "addressLine": "1 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "registeringForSelf": true
                                }
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated())
                .andReturn();
        String clientId = objectMapper.readTree(clientResult.getResponse().getContentAsString())
                .get("id").asText();

        String primaryId = registerCaregiver("primary+" + System.nanoTime() + "@example.com");
        String rotationalId = registerCaregiver("rot+" + System.nanoTime() + "@example.com");

        mockMvc.perform(post("/api/admin/clients/" + clientId + "/caregivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId":"%s","assignmentType":"PRIMARY"}
                                """.formatted(primaryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentType").value("PRIMARY"));

        mockMvc.perform(post("/api/admin/clients/" + clientId + "/caregivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId":"%s","assignmentType":"PRIMARY"}
                                """.formatted(rotationalId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/clients/" + clientId + "/caregivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId":"%s","assignmentType":"ROTATIONAL"}
                                """.formatted(rotationalId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignmentType").value("ROTATIONAL"));

        MvcResult roster = mockMvc.perform(get("/api/admin/clients/" + clientId + "/caregivers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String assignmentId = objectMapper.readTree(roster.getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(delete("/api/admin/clients/" + clientId + "/caregivers/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleClaimsReleased").exists());

        mockMvc.perform(get("/api/admin/clients/" + clientId + "/caregivers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private String registerCaregiver(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "CAREGIVER",
                                  "firstName": "Care",
                                  "lastName": "Giver"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(put("/api/caregivers/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Care","lastName":"Giver","qualifications":["CNA"]}
                                """))
                .andExpect(status().isOk());
        MvcResult me = mockMvc.perform(get("/api/caregivers/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).get("id").asText();
    }
}
