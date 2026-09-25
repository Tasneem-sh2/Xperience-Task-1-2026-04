package com.xperience.hero.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real HTTP + real database tests for the capacity/waitlist/promotion
 * business rules, exercised through the actual REST layer rather than the
 * service layer directly (RsvpServiceTest already covers those rules at the
 * unit level; RsvpServiceConcurrencyTest already covers the real concurrent
 * scenario - this class deliberately does not repeat either).
 *
 * @Transactional here is intentional and safe: unlike the concurrency test,
 * these scenarios are single-threaded and sequential, so wrapping each test
 * in one transaction gives automatic rollback-based cleanup with no manual
 * deletes, while still exercising the real pessimistic-lock code path (the
 * lock is acquired within the same already-open transaction).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RsvpBusinessRulesApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void capacityOne_firstYesConfirmed_secondYesWaitlisted_capacityNeverExceeded() throws Exception {
        JsonNode event = createEvent(1);
        Long eventId = event.get("id").asLong();
        String hostToken = event.get("hostToken").asText();

        String token1 = createInvitation(eventId, hostToken, "racer1@example.com").get("rsvpToken").asText();
        String token2 = createInvitation(eventId, hostToken, "racer2@example.com").get("rsvpToken").asText();

        JsonNode first = submitRsvp(token1, "YES");
        JsonNode second = submitRsvp(token2, "YES");

        assertThat(first.get("attendanceStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(second.get("attendanceStatus").asText()).isEqualTo("WAITLISTED");
        assertThat(second.get("response").asText()).isEqualTo("YES");
    }

    @Test
    void confirmedYesToNo_promotesEarliestWaitlistedInvitee() throws Exception {
        JsonNode event = createEvent(1);
        Long eventId = event.get("id").asLong();
        String hostToken = event.get("hostToken").asText();

        String token1 = createInvitation(eventId, hostToken, "first@example.com").get("rsvpToken").asText();
        String token2 = createInvitation(eventId, hostToken, "second@example.com").get("rsvpToken").asText();

        submitRsvp(token1, "YES"); // CONFIRMED
        JsonNode waitlisted = submitRsvp(token2, "YES"); // WAITLISTED
        assertThat(waitlisted.get("attendanceStatus").asText()).isEqualTo("WAITLISTED");

        submitRsvp(token1, "NO"); // frees the confirmed slot

        JsonNode promoted = getRsvp(token2);
        assertThat(promoted.get("response").asText()).isEqualTo("YES");
        assertThat(promoted.get("attendanceStatus").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmedYesToMaybe_promotesEarliestWaitlistedInvitee() throws Exception {
        JsonNode event = createEvent(1);
        Long eventId = event.get("id").asLong();
        String hostToken = event.get("hostToken").asText();

        String token1 = createInvitation(eventId, hostToken, "first@example.com").get("rsvpToken").asText();
        String token2 = createInvitation(eventId, hostToken, "second@example.com").get("rsvpToken").asText();

        submitRsvp(token1, "YES"); // CONFIRMED
        JsonNode waitlisted = submitRsvp(token2, "YES"); // WAITLISTED
        assertThat(waitlisted.get("attendanceStatus").asText()).isEqualTo("WAITLISTED");

        submitRsvp(token1, "MAYBE"); // frees the confirmed slot (Phase 2 decision E)

        JsonNode promoted = getRsvp(token2);
        assertThat(promoted.get("response").asText()).isEqualTo("YES");
        assertThat(promoted.get("attendanceStatus").asText()).isEqualTo("CONFIRMED");
    }

    private JsonNode createEvent(int maxCapacity) throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
            put("title", "Business Rules Test Event");
            put("description", "temporary test data");
            put("startTime", Instant.now().plus(7, ChronoUnit.DAYS).toString());
            put("location", "HQ");
            put("maxCapacity", maxCapacity);
        }});

        String response = mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createInvitation(Long eventId, String hostToken, String email) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email));
        String response = mockMvc.perform(post("/api/events/" + eventId + "/invitations")
                        .header("X-Host-Token", hostToken)
                        .contentType("application/json")
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode submitRsvp(String rsvpToken, String response) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("response", response));
        String content = mockMvc.perform(post("/api/rsvp/" + rsvpToken).contentType("application/json").content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    private JsonNode getRsvp(String rsvpToken) throws Exception {
        String content = mockMvc
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rsvp/" + rsvpToken))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }
}
