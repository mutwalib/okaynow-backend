package com.okaynow.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class FacilityProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void facilityRegistersWithAddressAndCanUpdateEditableFields() throws Exception {
        String email = "facility+" + System.nanoTime() + "@example.com";
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "phone": "617-555-0199",
                                  "role": "FACILITY",
                                  "firstName": "Pat",
                                  "lastName": "Manager",
                                  "facilityName": "Harbor Adult Day",
                                  "addressLine": "50 Harbor Ave",
                                  "city": "Quincy",
                                  "state": "MA",
                                  "zip": "02169"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(register.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/facilities/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.facilityName").value("Harbor Adult Day"))
                .andExpect(jsonPath("$.contactFirstName").value("Pat"))
                .andExpect(jsonPath("$.addressLine").value("50 Harbor Ave"))
                .andExpect(jsonPath("$.city").value("Quincy"));

        mockMvc.perform(put("/api/facilities/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contactFirstName": "Alex",
                                  "contactLastName": "Director",
                                  "phone": "617-555-0111",
                                  "addressLine": "75 Harbor Ave",
                                  "city": "Quincy",
                                  "state": "MA",
                                  "zip": "02170",
                                  "notes": "Use side entrance"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityName").value("Harbor Adult Day"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.contactFirstName").value("Alex"))
                .andExpect(jsonPath("$.addressLine").value("75 Harbor Ave"))
                .andExpect(jsonPath("$.zip").value("02170"))
                .andExpect(jsonPath("$.phone").value("617-555-0111"))
                .andExpect(jsonPath("$.notes").value("Use side entrance"));
    }

    @Test
    void facilityRegistrationRequiresAddress() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "nofacility+%s@example.com",
                                  "password": "password123",
                                  "role": "FACILITY",
                                  "firstName": "Pat",
                                  "lastName": "Manager",
                                  "facilityName": "Missing Address LLC"
                                }
                                """.formatted(System.nanoTime())))
                .andExpect(status().isBadRequest());
    }
}
