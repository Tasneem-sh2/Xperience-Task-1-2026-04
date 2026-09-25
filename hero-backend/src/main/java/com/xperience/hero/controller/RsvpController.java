package com.xperience.hero.controller;

import com.xperience.hero.domain.Event;
import com.xperience.hero.domain.Invitee;
import com.xperience.hero.dto.RsvpResponseDto;
import com.xperience.hero.dto.SubmitRsvpRequest;
import com.xperience.hero.service.RsvpService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rsvp")
public class RsvpController {

    private final RsvpService rsvpService;

    public RsvpController(RsvpService rsvpService) {
        this.rsvpService = rsvpService;
    }

    @GetMapping("/{rsvpToken}")
    public ResponseEntity<RsvpResponseDto> getRsvp(@PathVariable String rsvpToken) {
        Invitee invitee = rsvpService.getInviteeByToken(rsvpToken);
        return ResponseEntity.ok(toDto(invitee));
    }

    @PostMapping("/{rsvpToken}")
    public ResponseEntity<RsvpResponseDto> submitRsvp(
            @PathVariable String rsvpToken, @Valid @RequestBody SubmitRsvpRequest request) {
        Invitee invitee = rsvpService.submitRsvpResponse(rsvpToken, request.response());
        return ResponseEntity.ok(toDto(invitee));
    }

    private RsvpResponseDto toDto(Invitee invitee) {
        Event event = invitee.getEvent();
        return new RsvpResponseDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getStatus().name(),
                invitee.getEmail(),
                invitee.getResponse() == null ? null : invitee.getResponse().name(),
                invitee.getAttendanceStatus() == null ? null : invitee.getAttendanceStatus().name());
    }
}
