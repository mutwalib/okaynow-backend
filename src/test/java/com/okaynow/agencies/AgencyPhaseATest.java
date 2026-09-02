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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgencyPhaseATest {

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
    void directoryListsActiveAgenciesAndHomeCanConnect() throws Exception {
        mockMvc.perform(get("/api/agencies/directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("North Shore Home Care"));

        mockMvc.perform(get("/api/agencies/north-shore-home-care/public-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("north-shore-home-care"));

        String homeToken = loginAs(registerUser("CLIENT", null));

        Agency agency = agencyRepository.findBySlug("north-shore-home-care").orElseThrow();
        mockMvc.perform(post("/api/home/agencies/" + agency.getId() + "/connect-request")
                        .header("Authorization", "Bearer " + homeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Looking for PCA support"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/home/agencies/connected")
                        .header("Authorization", "Bearer " + homeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agencyDisplayName").value("North Shore Home Care"));
    }

    @Test
    void agencyAdminCanRegisterAndUpdateDirectoryProfile() throws Exception {
        String agencyToken = loginAs(registerUser("AGENCY_ADMIN", "Harbor Care Agency"));

        mockMvc.perform(get("/api/agencies/me")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Harbor Care Agency"))
                .andExpect(jsonPath("$.subscriptionStatus").value("TRIAL"));

        mockMvc.perform(patch("/api/agencies/me/directory-profile")
                        .header("Authorization", "Bearer " + agencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Harbor Care Agency",
                                  "publicDescription": "Compassionate home care on the South Shore.",
                                  "directoryListed": true,
                                  "qualificationsSupported": ["PCA","HHA"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directoryListed").value(true));
    }

    @Test
    void agencyAdminCanAcceptHomeConnection() throws Exception {
        Agency agency = agencyRepository.findBySlug("north-shore-home-care").orElseThrow();
        String homeToken = loginAs(registerUser("CLIENT", null));

        MvcResult connect = mockMvc.perform(post("/api/home/agencies/" + agency.getId() + "/connect-request")
                        .header("Authorization", "Bearer " + homeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String connectionId = objectMapper.readTree(connect.getResponse().getContentAsString())
                .get("id").asText();

        User admin = userRepository.findByEmail("northshore-admin@example.com").orElseThrow();
        String agencyToken = issueToken(admin);

        mockMvc.perform(post("/api/agencies/me/connections/" + connectionId + "/accept")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String registerUser(String role, String agencyName) throws Exception {
        String email = role.toLowerCase() + "+" + System.nanoTime() + "@example.com";
        String agencyField = agencyName == null
                ? ""
                : ",\"agencyName\":\"" + agencyName + "\",\"addressLine\":\"10 Harbor Rd\",\"city\":\"Quincy\",\"state\":\"MA\",\"zip\":\"02169\"";
        String clientField = "CLIENT".equals(role) ? ",\"registeringForSelf\":true" : "";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"password123",
                                  "role":"%s",
                                  "firstName":"Test",
                                  "lastName":"User",
                                  "acceptedLegalDocumentIds":%s%s%s
                                }
                                """.formatted(email, role, legalIdsJson(), clientField, agencyField)))
                .andExpect(status().isCreated());
        return email;
    }

    private String loginAs(String email) throws Exception {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerified(true);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        return issueToken(user);
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
