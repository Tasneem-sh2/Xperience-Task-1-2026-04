package com.xperience.hero.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateInvitationRequest(@NotBlank String email) {
}
