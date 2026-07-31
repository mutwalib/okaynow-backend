package com.okaynow.booking;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerTest {

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
    private String caregiverEmail;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = createAdminAndLogin("admin+" + System.nanoTime() + "@example.com");
        caregiverEmail = "cg+" + System.nanoTime() + "@example.com";
        caregiverToken = registerCaregiver(caregiverEmail, "CNA");
    }

    // ---------------------------------------------------------------- helpers

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

    /**
     * Registers a caregiver and sets their qualifications through the profile endpoint.
     * Pass no qualifications to create an unqualified caregiver.
     */
    private String registerCaregiver(String email, String... qualifications) throws Exception {
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

        if (qualifications.length > 0) {
            StringBuilder quals = new StringBuilder();
            for (String q : qualifications) {
                if (quals.length() > 0) quals.append(",");
                quals.append("\"").append(q).append("\"");
            }
            mockMvc.perform(put("/api/caregivers/me")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"firstName": "Care", "lastName": "Giver", "qualifications": [%s]}
                                    """.formatted(quals)))
                    .andExpect(status().isOk());
        }
        return token;
    }

    private String createShift(String startTime, String endTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shifts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requiredQualification": "CNA",
                                  "date": "2026-08-01",
                                  "startTime": "%s",
                                  "endTime": "%s",
                                  "addressLine": "123 Main St",
                                  "city": "Boston",
                                  "state": "MA",
                                  "zip": "02108",
                                  "payRate": 22.00,
                                  "billRate": 34.00
                                }
                                """.formatted(startTime, endTime)))
                .andExpect(status().isCreated())
                .andReturn();
        String shiftId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shifts").get(0).get("id").asText();
        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return shiftId;
    }

    private String createShift() throws Exception {
        return createShift("09:00", "17:00");
    }

    private String claim(String shiftId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String shiftStatus(String shiftId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/shifts/" + shiftId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("status").asText();
    }

    // ------------------------------------------------------------ claim tests

    @Test
    void caregiverCanClaimOpenShift() throws Exception {
        String shiftId = createShift();

        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.source").value("MARKETPLACE"))
                .andExpect(jsonPath("$.claimedAt", notNullValue()))
                .andExpect(jsonPath("$.caregiverEmail").value(caregiverEmail))
                .andExpect(jsonPath("$.shift.id").value(shiftId))
                .andExpect(jsonPath("$.shift.status").value("CLAIMED"));

        assertEquals("CLAIMED", shiftStatus(shiftId));
    }

    @Test
    void claimedShiftAppearsInMyClaims() throws Exception {
        String shiftId = createShift();
        claim(shiftId, caregiverToken);

        mockMvc.perform(get("/api/claims/me")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].shift.id").value(shiftId))
                .andExpect(jsonPath("$.content[0].shift.city").value("Boston"));
    }

    @Test
    void unqualifiedCaregiverCannotClaim() throws Exception {
        String shiftId = createShift();
        String unqualifiedToken = registerCaregiver("unqualified+" + System.nanoTime() + "@example.com", "PCA");

        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + unqualifiedToken))
                .andExpect(status().isBadRequest());

        assertEquals("OPEN", shiftStatus(shiftId));
    }

    @Test
    void secondCaregiverClaimingSameShiftGetsConflict() throws Exception {
        String shiftId = createShift();
        claim(shiftId, caregiverToken);

        String secondToken = registerCaregiver("second+" + System.nanoTime() + "@example.com", "CNA");
        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isConflict());
    }

    @Test
    void overlappingShiftClaimIsRejected() throws Exception {
        String morning = createShift("09:00", "17:00");
        String overlapping = createShift("16:00", "22:00");
        String backToBack = createShift("17:00", "21:00");

        claim(morning, caregiverToken);

        mockMvc.perform(post("/api/shifts/" + overlapping + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isConflict());

        // Adjacent (non-overlapping) shift on the same day is fine.
        mockMvc.perform(post("/api/shifts/" + backToBack + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------- release tests

    @Test
    void caregiverCanReleaseAndShiftCanBeReclaimed() throws Exception {
        String shiftId = createShift();
        claim(shiftId, caregiverToken);

        mockMvc.perform(post("/api/shifts/" + shiftId + "/release")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.releasedAt", notNullValue()));

        assertEquals("OPEN", shiftStatus(shiftId));

        // Another caregiver can now pick it up.
        String secondToken = registerCaregiver("reclaimer+" + System.nanoTime() + "@example.com", "CNA");
        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isCreated());
    }

    @Test
    void nonOwnerCannotReleaseClaim() throws Exception {
        String shiftId = createShift();
        claim(shiftId, caregiverToken);

        String otherToken = registerCaregiver("other+" + System.nanoTime() + "@example.com", "CNA");
        mockMvc.perform(post("/api/shifts/" + shiftId + "/release")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertEquals("CLAIMED", shiftStatus(shiftId));
    }

    @Test
    void confirmedClaimCannotBeReleasedByCaregiver() throws Exception {
        String shiftId = createShift();
        String claimId = claim(shiftId, caregiverToken);

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/shifts/" + shiftId + "/release")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------- admin tests

    @Test
    void adminCanListAllClaimsWithCaregiverDetails() throws Exception {
        String shiftId = createShift();
        claim(shiftId, caregiverToken);

        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].caregiverEmail", notNullValue()))
                .andExpect(jsonPath("$.content[0].caregiverFirstName", notNullValue()))
                .andExpect(jsonPath("$.content[0].shift", notNullValue()));
    }

    @Test
    void caregiverCannotAccessAdminClaimEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/claims")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanConfirmPendingClaim() throws Exception {
        String shiftId = createShift();
        String claimId = claim(shiftId, caregiverToken);

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.shift.status").value("CONFIRMED"));

        // Confirming twice conflicts.
        mockMvc.perform(post("/api/admin/claims/" + claimId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanCancelActiveClaimAndShiftReopens() throws Exception {
        String shiftId = createShift();
        String claimId = claim(shiftId, caregiverToken);

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals("OPEN", shiftStatus(shiftId));

        // Cancelling an already-cancelled claim conflicts.
        mockMvc.perform(post("/api/admin/claims/" + claimId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------ lifecycle tests

    @Test
    void fullLifecycleClaimConfirmStartComplete() throws Exception {
        String shiftId = createShift();
        String claimId = claim(shiftId, caregiverToken);

        // Cannot start before confirmation.
        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/start")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/claims/" + claimId + "/confirm")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Cannot complete before starting.
        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/complete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/start")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertEquals("IN_PROGRESS", shiftStatus(shiftId));

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/complete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertEquals("COMPLETED", shiftStatus(shiftId));

        // Claim also transitions to COMPLETED.
        mockMvc.perform(get("/api/claims/me")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void claimingNonExistentShiftReturns404() throws Exception {
        mockMvc.perform(post("/api/shifts/00000000-0000-0000-0000-000000000000/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanAssignCaregiverToOpenShift() throws Exception {
        String shiftId = createShift();

        MvcResult me = mockMvc.perform(get("/api/caregivers/me")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andReturn();
        String caregiverProfileId = objectMapper.readTree(me.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/assign")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caregiverProfileId": "%s"}
                                """.formatted(caregiverProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.source").value("ASSIGNED"))
                .andExpect(jsonPath("$.shift.status").value("CONFIRMED"));

        assertEquals("CONFIRMED", shiftStatus(shiftId));

        // Shift is no longer free for marketplace claim.
        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotClaimShift() throws Exception {
        String shiftId = createShift();
        mockMvc.perform(post("/api/shifts/" + shiftId + "/claim")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCancelAnOpenShift() throws Exception {
        String shiftId = createShift();

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertEquals("CANCELLED", shiftStatus(shiftId));

        mockMvc.perform(post("/api/admin/shifts/" + shiftId + "/cancel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }
}
