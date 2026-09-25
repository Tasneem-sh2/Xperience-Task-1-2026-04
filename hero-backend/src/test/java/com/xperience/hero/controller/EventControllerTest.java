package com.xperience.hero.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.exception.DuplicateInvitationException;
import com.xperience.hero.service.EventService;
import com.xperience.hero.service.RsvpService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private RsvpService rsvpService;

    @Test
    void createEvent_succeeds() throws Exception {
        Event event = sampleEvent(1L, EventStatus.OPEN);
        when(eventService.createEvent(anyString(), anyString(), any(Instant.class), anyString(), eq(20)))
                .thenReturn(event);

        String body = """
                {"title":"Offsite","description":"desc","startTime":"%s","location":"HQ","maxCapacity":20}
                """.formatted(event.getStartTime());

        mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.hostToken", is("host-token")))
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    @Test
    void createEvent_invalidRequest_missingTitle_returns400() throws Exception {
        String body = """
                {"description":"desc","startTime":"2027-01-01T09:00:00Z","location":"HQ"}
                """;

        mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeEvent_withValidHostToken_succeeds() throws Exception {
        Event event = sampleEvent(1L, EventStatus.OPEN);
        Event closed = sampleEvent(1L, EventStatus.CLOSED);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(eventService.closeEvent(1L)).thenReturn(closed);

        mockMvc.perform(post("/api/events/1/close").header("X-Host-Token", "host-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.hostToken").doesNotExist());
    }

    @Test
    void cancelEvent_withValidHostToken_succeeds() throws Exception {
        Event event = sampleEvent(1L, EventStatus.OPEN);
        Event cancelled = sampleEvent(1L, EventStatus.CANCELLED);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(eventService.cancelEvent(1L)).thenReturn(cancelled);

        mockMvc.perform(post("/api/events/1/cancel").header("X-Host-Token", "host-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void closeEvent_missingHostToken_returns400() throws Exception {
        mockMvc.perform(post("/api/events/1/close"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void closeEvent_wrongHostToken_returns404() throws Exception {
        when(eventService.findEventForHost(1L, "wrong-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/events/1/close").header("X-Host-Token", "wrong-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEventForHost_succeeds_withoutExposingHostToken() throws Exception {
        Event event = sampleEvent(1L, EventStatus.OPEN);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/events/1").header("X-Host-Token", "host-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.hostToken").doesNotExist());
    }

    @Test
    void createInvitation_succeeds() throws Exception {
        Event event = sampleEvent(1L, EventStatus.OPEN);
        Invitee invitee = sampleInvitee(event);
        when(rsvpService.createInvitation(1L, "host-token", "a@example.com")).thenReturn(invitee);

        String body = """
                {"email":"a@example.com"}
                """;

        mockMvc.perform(post("/api/events/1/invitations")
                        .header("X-Host-Token", "host-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rsvpToken", is("rsvp-token")))
                .andExpect(jsonPath("$.rsvpLink", notNullValue()));
    }

    @Test
    void createInvitation_duplicateEmail_returns409() throws Exception {
        when(rsvpService.createInvitation(eq(1L), eq("host-token"), eq("a@example.com")))
                .thenThrow(new DuplicateInvitationException("Email already invited to this event: a@example.com"));

        String body = """
                {"email":"a@example.com"}
                """;

        mockMvc.perform(post("/api/events/1/invitations")
                        .header("X-Host-Token", "host-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvitation_missingHostToken_returns400() throws Exception {
        String body = """
                {"email":"a@example.com"}
                """;

        mockMvc.perform(post("/api/events/1/invitations").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    private Event sampleEvent(Long id, EventStatus status) {
        Event event = new Event();
        event.setId(id);
        event.setTitle("Offsite");
        event.setDescription("desc");
        event.setStartTime(Instant.now().plus(7, ChronoUnit.DAYS));
        event.setLocation("HQ");
        event.setMaxCapacity(20);
        event.setStatus(status);
        event.setHostToken("host-token");
        return event;
    }

    private Invitee sampleInvitee(Event event) {
        Invitee invitee = new Invitee();
        invitee.setId(1L);
        invitee.setEvent(event);
        invitee.setEmail("a@example.com");
        invitee.setRsvpToken("rsvp-token");
        return invitee;
    }
}
