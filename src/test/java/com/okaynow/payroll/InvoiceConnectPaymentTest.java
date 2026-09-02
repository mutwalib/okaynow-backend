package com.okaynow.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okaynow.agencies.domain.Agency;
import com.okaynow.agencies.domain.AgencyStaff;
import com.okaynow.agencies.domain.SubscriptionPlan;
import com.okaynow.agencies.domain.SubscriptionStatus;
import com.okaynow.agencies.repository.AgencyRepository;
import com.okaynow.agencies.repository.AgencyStaffRepository;
import com.okaynow.payroll.domain.ClientInvoice;
import com.okaynow.payroll.domain.ClientInvoiceLine;
import com.okaynow.payroll.domain.InvoiceStatus;
import com.okaynow.payroll.repository.ClientInvoiceRepository;
import com.okaynow.users.domain.ClientProfile;
import com.okaynow.users.domain.Role;
import com.okaynow.users.domain.User;
import com.okaynow.users.domain.UserStatus;
import com.okaynow.users.repository.ClientProfileRepository;
import com.okaynow.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceConnectPaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ClientInvoiceRepository invoiceRepository;

    @Autowired
    private AgencyRepository agencyRepository;

    @Autowired
    private AgencyStaffRepository agencyStaffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void agencyCanSendInvoiceAndHomeSeesPayableOnlineWhenConnectReady() throws Exception {
        Agency agency = agencyRepository.save(Agency.builder()
                .slug("connect-invoice-" + System.nanoTime())
                .legalName("Connect Invoice Agency")
                .displayName("Connect Invoice Agency")
                .city("Boston")
                .state("MA")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionPlan(SubscriptionPlan.PROFESSIONAL)
                .subscriptionPeriodStart(Instant.now())
                .subscriptionPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .directoryListed(true)
                .stripeConnectAccountId("acct_test_connect")
                .stripeConnectChargesEnabled(true)
                .stripeConnectPayoutsEnabled(true)
                .build());

        User agencyAdmin = userRepository.save(User.builder()
                .email("agency+inv+" + System.nanoTime() + "@example.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(Role.AGENCY_ADMIN)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build());
        agencyStaffRepository.save(AgencyStaff.builder()
                .agency(agency)
                .user(agencyAdmin)
                .build());

        String homeEmail = "home+inv+" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "role":"CLIENT",
                                  "firstName":"Home",
                                  "lastName":"Payer",
                                  "registeringForSelf":true,
                                  "acceptedLegalDocumentIds":%s
                                }
                                """.formatted(homeEmail, legalIdsJson())))
                .andExpect(status().isCreated());
        User home = userRepository.findByEmail(homeEmail).orElseThrow();
        home.setEmailVerified(true);
        home.setStatus(UserStatus.ACTIVE);
        userRepository.save(home);
        ClientProfile client = clientProfileRepository.findByUserId(home.getId()).orElseThrow();

        ClientInvoice invoice = ClientInvoice.builder()
                .invoiceNumber("INV-TEST-" + System.nanoTime())
                .agencyId(agency.getId())
                .clientProfileId(client.getId())
                .status(InvoiceStatus.DRAFT)
                .issuedDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .totalAmount(new BigDecimal("100.00"))
                .createdBy(agencyAdmin.getId())
                .build();
        invoice.addLine(ClientInvoiceLine.builder()
                .shiftDate(LocalDate.now())
                .description("Test shift")
                .hours(new BigDecimal("4.00"))
                .billRate(new BigDecimal("25.00"))
                .amount(new BigDecimal("100.00"))
                .build());
        invoice = invoiceRepository.save(invoice);

        String agencyToken = issueToken(agencyAdmin);
        mockMvc.perform(get("/api/agencies/me/invoices")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNumber").value(invoice.getInvoiceNumber()))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        mockMvc.perform(post("/api/agencies/me/invoices/" + invoice.getId() + "/send")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.payableOnline").value(true));

        String homeToken = issueToken(home);
        mockMvc.perform(get("/api/clients/me/invoices")
                        .header("Authorization", "Bearer " + homeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payableOnline").value(true));

        // Stripe keys are typically unset in test → graceful message, not 500.
        mockMvc.perform(post("/api/clients/me/invoices/" + invoice.getId() + "/checkout")
                        .header("Authorization", "Bearer " + homeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private String issueToken(User user) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(user.getEmail())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String legalIdsJson() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/legal/current"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
        List<UUID> ids = new ArrayList<>();
        for (JsonNode n : arr) {
            ids.add(UUID.fromString(n.get("id").asText()));
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(ids.get(i)).append('"');
        }
        json.append(']');
        return json.toString();
    }
}
