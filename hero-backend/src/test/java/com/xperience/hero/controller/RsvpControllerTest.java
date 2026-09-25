package com.xperience.hero.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.xperience.hero.domain.AttendanceStatus;
import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.EventStatus;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.domain.RsvpResponse;
import com.xperience.hero.exception.InvalidEventStateTransitionException;
import com.xperience.hero.exception.InviteeNotFoundException;
import com.xperience.hero.exception.RsvpWindowClosedException;
import com.xperience.hero.service.RsvpService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RsvpController.class)
class RsvpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RsvpService rsvpService;

    @Test
    void getRsvp_validToken_returns200() throws Exception {
        Invitee invitee = pendingInvitee();
        when(rsvpService.getInviteeByToken("token")).thenReturn(invitee);

        mockMvc.perform(get("/api/rsvp/token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("a@example.com")))
                .andExpect(jsonPath("$.eventTitle", is("Offsite")));
    }

    @Test
    void getRsvp_unknownToken_returns404() throws Exception {
        when(rsvpService.getInviteeByToken("missing"))
                .thenThrow(new InviteeNotFoundException("Invitee not found for token"));

        mockMvc.perform(get("/api/rsvp/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitRsvp_yes_returns200() throws Exception {
        Invitee invitee = confirmedInvitee();
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.YES)).thenReturn(invitee);

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"YES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("YES")))
                .andExpect(jsonPath("$.attendanceStatus", is("CONFIRMED")));
    }

    @Test
    void submitRsvp_no_returns200() throws Exception {
        Invitee invitee = pendingInvitee();
        invitee.setResponse(RsvpResponse.NO);
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.NO)).thenReturn(invitee);

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"NO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("NO")));
    }

    @Test
    void submitRsvp_maybe_returns200() throws Exception {
        Invitee invitee = pendingInvitee();
        invitee.setResponse(RsvpResponse.MAYBE);
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.MAYBE)).thenReturn(invitee);

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"MAYBE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("MAYBE")));
    }

    @Test
    void submitRsvp_invalidValue_returns400() throws Exception {
        mockMvc.perform(post("/api/rsvp/token")
                        .contentType("application/json")
                        .content("{\"response\":\"MAYBE_NOT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitRsvp_closedEvent_returns409() throws Exception {
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .thenThrow(new InvalidEventStateTransitionException("RSVP not allowed: event is CLOSED"));

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"YES\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("RSVP not allowed: event is CLOSED")));
    }

    @Test
    void submitRsvp_cancelledEvent_returns409() throws Exception {
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .thenThrow(new InvalidEventStateTransitionException("RSVP not allowed: event is CANCELLED"));

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"YES\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("RSVP not allowed: event is CANCELLED")));
    }

    @Test
    void submitRsvp_afterEventStart_returns409() throws Exception {
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.YES))
                .thenThrow(new RsvpWindowClosedException("RSVP not allowed: event has already started"));

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"YES\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void submitRsvp_changingExistingRsvp_reflectsLatestValue() throws Exception {
        Invitee confirmedYes = confirmedInvitee();
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.YES)).thenReturn(confirmedYes);

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"YES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("YES")))
                .andExpect(jsonPath("$.attendanceStatus", is("CONFIRMED")));

        Invitee changedToNo = pendingInvitee();
        changedToNo.setResponse(RsvpResponse.NO);
        when(rsvpService.submitRsvpResponse("token", RsvpResponse.NO)).thenReturn(changedToNo);

        mockMvc.perform(post("/api/rsvp/token").contentType("application/json").content("{\"response\":\"NO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("NO")))
                .andExpect(jsonPath("$.attendanceStatus").doesNotExist());
    }

    private Invitee pendingInvitee() {
        Event event = new Event();
        event.setId(1L);
        event.setTitle("Offsite");
        event.setDescription("desc");
        event.setStartTime(Instant.now().plus(7, ChronoUnit.DAYS));
        event.setLocation("HQ");
        event.setStatus(EventStatus.OPEN);
        event.setHostToken("host-token");

        Invitee invitee = new Invitee();
        invitee.setId(1L);
        invitee.setEvent(event);
        invitee.setEmail("a@example.com");
        invitee.setRsvpToken("token");
        return invitee;
    }

    private Invitee confirmedInvitee() {
        Invitee invitee = pendingInvitee();
        invitee.setResponse(RsvpResponse.YES);
        invitee.setAttendanceStatus(AttendanceStatus.CONFIRMED);
        return invitee;
    }
}
