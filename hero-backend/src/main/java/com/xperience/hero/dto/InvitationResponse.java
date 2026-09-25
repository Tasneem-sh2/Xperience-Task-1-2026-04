package com.xperience.hero.dto;

public record InvitationResponse(
        Long id,
        Long eventId,
        String email,
        String rsvpToken,
        String rsvpLink) {
}
