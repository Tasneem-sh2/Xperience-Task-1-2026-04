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
import com.xperience.hero.exception.EventNotFoundException;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
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
    void createEvent_invalidRequest_missingStartTime_returns400() throws Exception {
        String body = """
                {"title":"Offsite","description":"desc","location":"HQ"}
                """;

        mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_invalidRequest_missingLocation_returns400() throws Exception {
        String body = """
                {"title":"Offsite","description":"desc","startTime":"2027-01-01T09:00:00Z"}
                """;

        mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_invalidRequest_nonPositiveMaxCapacity_returns400() throws Exception {
        String zeroBody = """
                {"title":"Offsite","description":"desc","startTime":"2027-01-01T09:00:00Z","location":"HQ","maxCapacity":0}
                """;
        mockMvc.perform(post("/api/events").contentType("application/json").content(zeroBody))
                .andExpect(status().isBadRequest());

        String negativeBody = """
                {"title":"Offsite","description":"desc","startTime":"2027-01-01T09:00:00Z","location":"HQ","maxCapacity":-3}
                """;
        mockMvc.perform(post("/api/events").contentType("application/json").content(negativeBody))
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
    void closeEvent_invalidLifecycleTransition_returns409() throws Exception {
        Event event = sampleEvent(1L, EventStatus.CANCELLED);
        when(eventService.findEventForHost(1L, "host-token")).thenReturn(Optional.of(event));
        when(eventService.closeEvent(1L))
                .thenThrow(new InvalidEventStateTransitionException("Cannot close a cancelled event"));

        mockMvc.perform(post("/api/events/1/close").header("X-Host-Token", "host-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("Cannot close a cancelled event")));
    }

    @Test
    void cancelEvent_missingHostToken_returns400() throws Exception {
        mockMvc.perform(post("/api/events/1/cancel"))
                .andExpect(status().isBadRequest());
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
    void getEventForHost_missingHostToken_returns400() throws Exception {
        mockMvc.perform(get("/api/events/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEventForHost_wrongHostToken_returns404_withoutRevealingEventExistence() throws Exception {
        when(eventService.findEventForHost(1L, "wrong-token")).thenReturn(Optional.empty());
        when(eventService.findEventForHost(999L, "any-token")).thenReturn(Optional.empty());

        // A wrong token on a real event and a token on a nonexistent event must produce
        // the same error shape and the same generic message pattern - neither response
        // may reveal whether the event actually exists.
        mockMvc.perform(get("/api/events/1").header("X-Host-Token", "wrong-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Event not found: 1")));

        mockMvc.perform(get("/api/events/999").header("X-Host-Token", "any-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Event not found: 999")));
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

    @Test
    void createInvitation_wrongHostToken_returns404() throws Exception {
        when(rsvpService.createInvitation(1L, "wrong-token", "a@example.com"))
                .thenThrow(new EventNotFoundException("Event not found: 1"));

        String body = """
                {"email":"a@example.com"}
                """;

        mockMvc.perform(post("/api/events/1/invitations")
                        .header("X-Host-Token", "wrong-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInvitation_nonexistentEvent_returns404() throws Exception {
        when(rsvpService.createInvitation(999L, "host-token", "a@example.com"))
                .thenThrow(new EventNotFoundException("Event not found: 999"));

        String body = """
                {"email":"a@example.com"}
                """;

        mockMvc.perform(post("/api/events/999/invitations")
                        .header("X-Host-Token", "host-token")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createInvitation_closedEvent_returns409() throws Exception {
        when(rsvpService.createInvitation(1L, "host-token", "a@example.com"))
                .thenThrow(new InvalidEventStateTransitionException("Cannot invite to a CLOSED event"));

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
    void createInvitation_cancelledEvent_returns409() throws Exception {
        when(rsvpService.createInvitation(1L, "host-token", "a@example.com"))
                .thenThrow(new InvalidEventStateTransitionException("Cannot invite to a CANCELLED event"));

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
    void unexpectedServiceFailure_returns500_withoutLeakingInternalDetails() throws Exception {
        when(eventService.createEvent(anyString(), anyString(), any(Instant.class), anyString(), eq(20)))
                .thenThrow(new RuntimeException("jdbc.exceptions.SomeInternalDriverDetail: connection to 10.0.0.5 failed"));

        String body = """
                {"title":"Offsite","description":"desc","startTime":"2027-01-01T09:00:00Z","location":"HQ","maxCapacity":20}
                """;

        mockMvc.perform(post("/api/events").contentType("application/json").content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("An unexpected error occurred")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("jdbc"))));
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
