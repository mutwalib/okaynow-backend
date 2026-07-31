package com.okaynow.evv;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisitAttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String caregiverToken;
    private String clientToken;
    private String clientProfileId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin-visit+" + System.nanoTime() + "@example.com");
        caregiverToken = registerCaregiver("cg-visit+" + System.nanoTime() + "@example.com");
        clientProfileId = createClient();
        clientToken = loginClientFromProfile();
    }

    @Test
    void caregiverClocksInThenClientConfirmsAndAgencySeesBoth() throws Exception {
        String shiftId = createConfirmedShift();

        mockMvc.perform(get("/api/visits/by-shift/" + shiftId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/visits/by-shift/" + shiftId + "/clock-in")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat": 42.3601, "lng": -71.0589}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clockInAt").exists())
                .andExpect(jsonPath("$.method").value("GPS"))
                .andExpect(jsonPath("$.clientArrivalConfirmed").value(false));

        mockMvc.perform(get("/api/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/visits/by-shift/" + shiftId + "/confirm-arrival")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientArrivalConfirmed").value(true))
                .andExpect(jsonPath("$.clientArrivalConfirmedAt").exists());

        mockMvc.perform(get("/api/visits/by-shift/" + shiftId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientArrivalConfirmed").value(true))
                .andExpect(jsonPath("$.clockInAt").exists());
    }

    private String createConfirmedShift() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientProfileId": "%s",
                                  "requiredQualification": "CNA",
                                  "date": "2030-09-01",
                                  "startTime": "09:00:00",
                                  "endTime": "13:00:00",
                                  "addressLine": "1 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "lat": 42.36,
                                  "lng": -71.06,
                                  "payRate": 22.00,
                                  "billRate": 34.00
                                }
                                """.formatted(clientProfileId)))
                .andExpect(status().isCreated())
                .andReturn();
        String shiftId = objectMapper.readTree(create.getResponse().getContentAsString())
                .get("shifts").get(0).get("id").asText();

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        MvcResult claim = mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isCreated())
                .andReturn();
        String claimId = objectMapper.readTree(claim.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return shiftId;
    }

    private String createAdminAndLogin(String email) throws Exception {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        return login(email);
    }

    private String registerCaregiver(String email) throws Exception {
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "CAREGIVER",
                                  "firstName": "Casey",
                                  "lastName": "Aide"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(register.getResponse().getContentAsString())
                .get("accessToken").asText();
        mockMvc.perform(put("/api/caregivers/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Casey",
                                  "lastName": "Aide",
                                  "qualifications": ["CNA"],
                                  "hourlyRateMin": 18,
                                  "hourlyRateMax": 28,
                                  "serviceRadiusMiles": 25,
                                  "homeLat": 42.36,
                                  "homeLng": -71.06
                                }
                                """))
                .andExpect(status().isOk());
        return token;
    }

    private String createClient() throws Exception {
        String email = "client-visit+" + System.nanoTime() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "firstName": "Pat",
                                  "lastName": "Client",
                                  "addressLine": "1 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "lat": 42.36,
                                  "lng": -71.06,
                                  "registeringForSelf": true
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        // stash email on a field via login later — store on User by finding
        this.clientEmail = email;
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String clientEmail;

    private String loginClientFromProfile() throws Exception {
        return login(clientEmail);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
