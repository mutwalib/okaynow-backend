package com.okaynow.admin;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminClientControllerTest {

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
        String email = "client-admin+" + System.nanoTime() + "@example.com";
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        adminToken = login(email, "password123");
    }

    @Test
    void ownerRegistersClientAndCreatesShiftAtClientAddress() throws Exception {
        String clientEmail = "client+" + System.nanoTime() + "@example.com";
        MvcResult clientResult = mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "temporary123",
                                  "firstName": "Alice",
                                  "lastName": "Care",
                                  "addressLine": "66 Sherburne Ave",
                                  "city": "Tyngsboro",
                                  "state": "MA",
                                  "zip": "01879",
                                  "careNeeds": "Mobility support",
                                  "registeringForSelf": true
                                }
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(clientEmail))
                .andExpect(jsonPath("$.clientType").value("FAMILY"))
                .andExpect(jsonPath("$.addressLine").value("66 Sherburne Ave"))
                .andReturn();

        String clientProfileId = objectMapper.readTree(
                clientResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].clientType").value("FAMILY"));

        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientProfileId": "%s",
                                  "requiredQualification": "CNA",
                                  "date": "2026-08-01",
                                  "startTime": "09:00",
                                  "endTime": "17:00",
                                  "addressLine": "Ignored address",
                                  "city": "Ignored city",
                                  "state": "MA",
                                  "zip": "00000",
                                  "payRate": 22.00,
                                  "billRate": 34.00
                                }
                                """.formatted(clientProfileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shifts[0].clientProfileId").value(clientProfileId))
                .andExpect(jsonPath("$.shifts[0].addressLine").value("66 Sherburne Ave"))
                .andExpect(jsonPath("$.shifts[0].city").value("Tyngsboro"))
                .andExpect(jsonPath("$.shifts[0].zip").value("01879"));
    }

    @Test
    void facilityAccountsAppearInClientsList() throws Exception {
        String facilityEmail = "facility-client+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "FACILITY",
                                  "firstName": "Pat",
                                  "lastName": "Manager",
                                  "facilityName": "Harbor Adult Day",
                                  "addressLine": "50 Harbor Ave",
                                  "city": "Quincy",
                                  "state": "MA",
                                  "zip": "02169"
                                }
                                """.formatted(facilityEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Harbor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[?(@.clientType == 'FACILITY')].facilityName")
                        .value(org.hamcrest.Matchers.hasItem("Harbor Adult Day")));
    }

    @Test
    void anonymousCannotRegisterClient() throws Exception {
        mockMvc.perform(post("/api/admin/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerControlsClientShiftPermissionsPaymentAndAudit() throws Exception {
        String clientEmail = "permissions+" + System.nanoTime() + "@example.com";
        MvcResult clientResult = mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "temporary123",
                                  "firstName": "Permission",
                                  "lastName": "Client",
                                  "addressLine": "10 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "registeringForSelf": true
                                }
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canViewShifts").value(true))
                .andExpect(jsonPath("$.canCreateShifts").value(false))
                .andExpect(jsonPath("$.canUpdateShifts").value(false))
                .andExpect(jsonPath("$.canDeleteShifts").value(false))
                .andReturn();
        String clientProfileId = objectMapper.readTree(
                clientResult.getResponse().getContentAsString()).get("id").asText();
        String clientToken = login(clientEmail, "temporary123");

        String shiftBody = """
                {
                  "requiredQualification": "CNA",
                  "date": "2026-09-01",
                  "startTime": "09:00",
                  "endTime": "17:00",
                  "addressLine": "10 Main St",
                  "city": "Boston",
                  "state": "MA",
                  "zip": "02108",
                  "payRate": 22.00,
                  "billRate": 34.00
                }
                """;

        mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shiftBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/admin/clients/" + clientProfileId + "/shift-permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "canViewShifts": true,
                                  "canCreateShifts": true,
                                  "canUpdateShifts": true,
                                  "canDeleteShifts": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCreateShifts").value(true));

        MvcResult shiftResult = mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shiftBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shifts[0].clientProfileId").value(clientProfileId))
                .andExpect(jsonPath("$.shifts[0].platformPaid").value(false))
                .andReturn();
        String shiftId = objectMapper.readTree(
                shiftResult.getResponse().getContentAsString()).get("shifts").get(0).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/shifts/" + shiftId + "/platform-payment")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platformPaid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformPaid").value(true));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(3)));
    }

    @Test
    void ownerRegistersClientForSomeoneElseWithMedicaidAndRelationship() throws Exception {
        String clientEmail = "rep+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "temporary123",
                                  "firstName": "Rep",
                                  "lastName": "Family",
                                  "addressLine": "1 Oak St",
                                  "city": "Lowell",
                                  "state": "MA",
                                  "zip": "01852",
                                  "registeringForSelf": false,
                                  "medicaidEligible": "YES",
                                  "relationshipToCareRecipient": "ADULT_CHILD"
                                }
                                """.formatted(clientEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registeringForSelf").value(false))
                .andExpect(jsonPath("$.medicaidEligible").value("YES"))
                .andExpect(jsonPath("$.relationshipToCareRecipient").value("ADULT_CHILD"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
