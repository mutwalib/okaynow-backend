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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgencyPhaseBTest {

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
    void homeShiftRequestAcceptedAndAssignedFromRoster() throws Exception {
        Agency agency = agencyRepository.findBySlug("north-shore-home-care").orElseThrow();
        User admin = userRepository.findByEmail("northshore-admin@example.com").orElseThrow();
        String agencyToken = issueToken(admin);

        String homeEmail = registerUser("CLIENT", null);
        String homeToken = loginAs(homeEmail);

        MvcResult connect = mockMvc.perform(post("/api/home/agencies/" + agency.getId() + "/connect-request")
                        .header("Authorization", "Bearer " + homeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String connectionId = objectMapper.readTree(connect.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/agencies/me/connections/" + connectionId + "/accept")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String caregiverEmail = registerUser("CAREGIVER", null);
        String caregiverToken = loginAs(caregiverEmail);
        setCaregiverQualifications(caregiverToken, "PCA");
        caregiverToken = loginAs(caregiverEmail);

        mockMvc.perform(post("/api/agencies/me/roster/invite")
                        .header("Authorization", "Bearer " + agencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","message":"Join our roster"}
                                """.formatted(caregiverEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED"));

        MvcResult invites = mockMvc.perform(get("/api/caregivers/me/roster-invites")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andReturn();
        String rosterInviteId = objectMapper.readTree(invites.getResponse().getContentAsString())
                .get(0).get("id").asText();
        String caregiverProfileId = objectMapper.readTree(invites.getResponse().getContentAsString())
                .get(0).get("caregiverProfileId").asText();

        mockMvc.perform(post("/api/caregivers/me/roster-invites/" + rosterInviteId + "/accept")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        MvcResult created = mockMvc.perform(post("/api/home/shift-requests")
                        .header("Authorization", "Bearer " + homeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requiredQualification":"PCA",
                                  "startDate":"2026-10-01",
                                  "startTime":"09:00:00",
                                  "endTime":"13:00:00",
                                  "addressLine":"12 Main St",
                                  "city":"Salem",
                                  "state":"MA",
                                  "zip":"01970",
                                  "notes":"Morning PCA",
                                  "agencyIds":["%s"]
                                }
                                """.formatted(agency.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();

        MvcResult inbox = mockMvc.perform(get("/api/agencies/me/shift-requests")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn();
        String inboxId = objectMapper.readTree(inbox.getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(post("/api/agencies/me/shift-requests/" + inboxId + "/accept")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        MvcResult shifts = mockMvc.perform(get("/api/agencies/me/shifts")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstShift = objectMapper.readTree(shifts.getResponse().getContentAsString()).get(0);
        String shiftId = firstShift.hasNonNull("id")
                ? firstShift.get("id").asText()
                : firstShift.get("shift").get("id").asText();

        mockMvc.perform(post("/api/agencies/me/shifts/" + shiftId + "/assign")
                        .header("Authorization", "Bearer " + agencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId":"%s"}
                                """.formatted(caregiverProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caregiverProfileId").value(caregiverProfileId));

        mockMvc.perform(get("/api/agencies/me/shifts")
                        .header("Authorization", "Bearer " + agencyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignments[0].caregiverProfileId").value(caregiverProfileId));

        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/agencies/me/shifts/" + shiftId + "/unassign")
                        .header("Authorization", "Bearer " + agencyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId":"%s"}
                                """.formatted(caregiverProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private void setCaregiverQualifications(String token, String... qualifications) throws Exception {
        StringBuilder quals = new StringBuilder();
        for (String q : qualifications) {
            if (quals.length() > 0) {
                quals.append(',');
            }
            quals.append('"').append(q).append('"');
        }
        mockMvc.perform(put("/api/caregivers/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Test","lastName":"User","qualifications":[%s]}
                                """.formatted(quals)))
                .andExpect(status().isOk());
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
