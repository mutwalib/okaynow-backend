package com.okaynow.agencies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.users.domain.Qualification;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgencyPhaseCTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private AgencyStaffRepository agencyStaffRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedListedAgency() {
        if (agencyRepository.findBySlug("north-shore-home-care").isPresent()) {
            return;
        }
        Agency agency = agencyRepository.save(Agency.builder()
                .slug("north-shore-home-care")
                .legalName("North Shore Home Care LLC")
                .displayName("North Shore Home Care")
                .city("Salem")
                .state("MA")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionPlan(SubscriptionPlan.PROFESSIONAL)
                .subscriptionPeriodStart(Instant.now())
                .subscriptionPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .directoryListed(true)
                .publicDescription("Trusted PCA and HHA services on the North Shore.")
                .qualificationsSupported(Set.of(Qualification.PCA, Qualification.HHA))
                .build());
        User agencyAdmin = userRepository.save(User.builder()
                .email("northshore-admin@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.AGENCY_ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());
        agencyStaffRepository.save(com.okaynow.agencies.domain.AgencyStaff.builder()
                .agency(agency)
                .user(agencyAdmin)
                .build());
    }

    @Test
    void agencyCanUpdateTenantSettingsAndExportHoursCsv() throws Exception {
        User admin = userRepository.findByEmail("northshore-admin@example.com").orElseThrow();
        String agencyToken = issueToken(admin);

        mockMvc.perform(get("/api/agencies/me/settings")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultPayRate", notNullValue()))
                .andExpect(jsonPath("$.agencyTakePercent", notNullValue()));

        mockMvc.perform(put("/api/agencies/me/settings")
                        .header("Authorization", "Bearer " + agencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agencyTakePercent": 30.00,
                                  "defaultPayRate": 24.00,
                                  "payPeriodType": "WEEKLY",
                                  "periodStartDay": "MONDAY",
                                  "autoInvoiceOnComplete": true,
                                  "autoInvoiceSendImmediately": false,
                                  "clientCaregiverRejectionFee": 25.00,
                                  "platformConversionFee": 500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultPayRate").value(24.00))
                .andExpect(jsonPath("$.agencyTakePercent").value(30.00))
                .andExpect(jsonPath("$.autoInvoiceSendImmediately").value(false));

        mockMvc.perform(get("/api/agencies/me/billing/connect")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConnectAccount").value(false));

        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();
        mockMvc.perform(get("/api/agencies/me/payroll/export")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("agency-hours-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("shift_date")));
    }

    private String issueToken(User user) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
        return node.get("accessToken").asText();
    }
}
