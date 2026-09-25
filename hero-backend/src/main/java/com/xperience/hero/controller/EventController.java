package com.xperience.hero.controller;

import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.CreateInvitationRequest;
import com.xperience.hero.dto.EventCreatedResponse;
import com.xperience.hero.dto.EventResponse;
import com.xperience.hero.dto.InvitationResponse;
import com.xperience.hero.exception.EventNotFoundException;
import com.xperience.hero.service.EventService;
import com.xperience.hero.service.RsvpService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final RsvpService rsvpService;

    public EventController(EventService eventService, RsvpService rsvpService) {
        this.eventService = eventService;
        this.rsvpService = rsvpService;
    }

    @PostMapping
    public ResponseEntity<EventCreatedResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = eventService.createEvent(
                request.title(), request.description(), request.startTime(), request.location(), request.maxCapacity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toCreatedResponse(event));
    }

    @PostMapping("/{eventId}/close")
    public ResponseEntity<EventResponse> closeEvent(
            @PathVariable Long eventId, @RequestHeader("X-Host-Token") String hostToken) {
        authorize(eventId, hostToken);
        Event event = eventService.closeEvent(eventId);
        return ResponseEntity.ok(toResponse(event));
    }

    @PostMapping("/{eventId}/cancel")
    public ResponseEntity<EventResponse> cancelEvent(
            @PathVariable Long eventId, @RequestHeader("X-Host-Token") String hostToken) {
        authorize(eventId, hostToken);
        Event event = eventService.cancelEvent(eventId);
        return ResponseEntity.ok(toResponse(event));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEventForHost(
            @PathVariable Long eventId, @RequestHeader("X-Host-Token") String hostToken) {
        Event event = authorize(eventId, hostToken);
        return ResponseEntity.ok(toResponse(event));
    }

    @PostMapping("/{eventId}/invitations")
    public ResponseEntity<InvitationResponse> createInvitation(
            @PathVariable Long eventId,
            @RequestHeader("X-Host-Token") String hostToken,
            @Valid @RequestBody CreateInvitationRequest request) {
        Invitee invitee = rsvpService.createInvitation(eventId, hostToken, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(toInvitationResponse(invitee));
    }

    private Event authorize(Long eventId, String hostToken) {
        return eventService.findEventForHost(eventId, hostToken)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getStatus().name());
    }

    private EventCreatedResponse toCreatedResponse(Event event) {
        return new EventCreatedResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getStatus().name(),
                event.getHostToken());
    }

    private InvitationResponse toInvitationResponse(Invitee invitee) {
        return new InvitationResponse(
                invitee.getId(),
                invitee.getEvent().getId(),
                invitee.getEmail(),
                invitee.getRsvpToken(),
                "/api/rsvp/" + invitee.getRsvpToken());
    }
}
