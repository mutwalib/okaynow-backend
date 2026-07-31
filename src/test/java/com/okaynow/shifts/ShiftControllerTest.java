package com.okaynow.shifts;

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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftControllerTest {

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

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin+" + System.nanoTime() + "@example.com");
        caregiverToken = registerAndGetToken("cg+" + System.nanoTime() + "@example.com", "CAREGIVER");
    }

    /**
     * ADMIN accounts cannot be self-registered through the public endpoint, so tests
     * provision them directly in the database and log in normally.
     */
    private String createAdminAndLogin(String email) throws Exception {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "%s",
                                  "firstName": "Test",
                                  "lastName": "User"
                                }
                                """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static final String SHIFT_BODY = """
            {
              "requiredQualification": "CNA",
              "date": "2026-08-01",
              "startTime": "09:00",
              "endTime": "17:00",
              "addressLine": "123 Main St",
              "city": "Boston",
              "state": "MA",
              "zip": "02108",
              "lat": 42.3601,
              "lng": -71.0589,
              "payRate": 22.00,
              "billRate": 34.00,
              "notes": "Daytime shift, mobility assistance required"
            }
            """;

    private String createShift() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SHIFT_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shifts").get(0).get("id").asText();
    }

    @Test
    void adminCanCreateShift() throws Exception {
        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SHIFT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.scheduleType").value("ONE_OFF"))
                .andExpect(jsonPath("$.shifts[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.shifts[0].payRate").value(22.00))
                .andExpect(jsonPath("$.shifts[0].billRate").value(34.00));
    }

    @Test
    void adminCanCreateDailyRoutineSeries() throws Exception {
        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requiredQualification": "CNA",
                                  "scheduleType": "DAILY_ROUTINE",
                                  "date": "2026-08-01",
                                  "endDate": "2026-08-03",
                                  "startTime": "09:00",
                                  "endTime": "17:00",
                                  "addressLine": "123 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "payRate": 22.00,
                                  "billRate": 34.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleType").value("DAILY_ROUTINE"))
                .andExpect(jsonPath("$.createdCount").value(3))
                .andExpect(jsonPath("$.seriesId", notNullValue()))
                .andExpect(jsonPath("$.shifts[0].scheduleType").value("DAILY_ROUTINE"))
                .andExpect(jsonPath("$.shifts[0].date").value("2026-08-01"))
                .andExpect(jsonPath("$.shifts[2].date").value("2026-08-03"));
    }

    @Test
    void caregiverCannotCreateShift() throws Exception {
        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SHIFT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void billRateBelowPayRateIsRejected() throws Exception {
        String badBody = SHIFT_BODY.replace("\"billRate\": 34.00", "\"billRate\": 10.00");
        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() throws Exception {
        String badBody = SHIFT_BODY.replace("\"endTime\": \"17:00\"", "\"endTime\": \"08:00\"");
        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void caregiverCanReadShifts() throws Exception {
        String id = createShift();

        // Draft shifts are hidden from caregivers until an admin publishes them.
        mockMvc.perform(get("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/shifts")
                        .param("status", "OPEN")
                        .param("qualification", "CNA")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(id)).isEmpty());

        mockMvc.perform(post("/api/admin/shifts/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Boston"));

        mockMvc.perform(get("/api/shifts")
                        .param("status", "OPEN")
                        .param("qualification", "CNA")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='%s')]".formatted(id)).isNotEmpty());
    }

    @Test
    void adminCanUpdateShift() throws Exception {
        String id = createShift();

        mockMvc.perform(patch("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payRate": 24.00, "billRate": 36.00, "notes": "Updated"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payRate").value(24.00))
                .andExpect(jsonPath("$.billRate").value(36.00))
                .andExpect(jsonPath("$.notes").value("Updated"));
    }

    @Test
    void statusCannotBeChangedThroughGenericUpdate() throws Exception {
        String id = createShift();

        mockMvc.perform(patch("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED", "notes": "trying to skip lifecycle"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void adminCanDeleteOpenShift() throws Exception {
        String id = createShift();

        mockMvc.perform(delete("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/shifts/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/shifts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownShiftReturns404() throws Exception {
        mockMvc.perform(get("/api/shifts/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
