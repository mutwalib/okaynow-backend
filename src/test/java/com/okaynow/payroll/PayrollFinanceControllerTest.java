package com.okaynow.payroll;

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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollFinanceControllerTest {

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
        adminToken = createAdminAndLogin("admin-pay+" + System.nanoTime() + "@example.com");
        caregiverToken = registerCaregiver("cg-pay+" + System.nanoTime() + "@example.com");
    }

    @Test
    void agencySettingsRoundTripAndFinanceSummary() throws Exception {
        mockMvc.perform(get("/api/admin/settings/agency")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyTakePercent").exists())
                .andExpect(jsonPath("$.payPeriodType").value("WEEKLY"));

        mockMvc.perform(put("/api/admin/settings/agency")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agencyTakePercent": 35.48,
                                  "defaultPayRate": 22.00,
                                  "payPeriodType": "WEEKLY",
                                  "periodStartDay": "MONDAY",
                                  "autoInvoiceOnComplete": true,
                                  "autoInvoiceSendImmediately": true,
                                  "clientCaregiverRejectionFee": 25.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyTakePercent").value(35.48))
                .andExpect(jsonPath("$.defaultPayRate").value(22.00))
                .andExpect(jsonPath("$.autoInvoiceOnComplete").value(true))
                .andExpect(jsonPath("$.clientCaregiverRejectionFee").value(25.00));

        mockMvc.perform(get("/api/admin/finance/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").exists())
                .andExpect(jsonPath("$.clientBilled").exists())
                .andExpect(jsonPath("$.caregiverPending").exists())
                .andExpect(jsonPath("$.agencyMarginAccrued").exists());

        mockMvc.perform(get("/api/payroll/me/summary")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEarned").exists())
                .andExpect(jsonPath("$.pending").exists())
                .andExpect(jsonPath("$.paid").exists());

        // Caregivers cannot read agency settings / finance.
        mockMvc.perform(get("/api/admin/settings/agency")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void caregiverShiftResponseOmitsBillRate() throws Exception {
        String clientId = createClient();
        MvcResult create = mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientProfileId": "%s",
                                  "requiredQualification": "CNA",
                                  "date": "2030-08-01",
                                  "startTime": "09:00:00",
                                  "endTime": "13:00:00",
                                  "addressLine": "1 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "lat": 42.36,
                                  "lng": -71.06,
                                  "payRate": 20.15,
                                  "billRate": 31.00
                                }
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn();
        String shiftId = objectMapper.readTree(create.getResponse().getContentAsString())
                .get("shifts").get(0).get("id").asText();

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payRate").value(20.15))
                .andExpect(jsonPath("$.billRate").value(nullValue()));
    }

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

    private String registerCaregiver(String email) throws Exception {
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "role": "CAREGIVER",
                                  "firstName": "Pay",
                                  "lastName": "Tester"
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
                                  "firstName": "Pay",
                                  "lastName": "Tester",
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
        MvcResult result = mockMvc.perform(post("/api/admin/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "client-pay+%s@example.com",
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
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
