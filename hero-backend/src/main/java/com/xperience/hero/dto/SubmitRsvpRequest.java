package com.xperience.hero.dto;

import com.xperience.hero.domain.RsvpResponse;
import jakarta.validation.constraints.NotNull;

public record SubmitRsvpRequest(@NotNull RsvpResponse response) {
}
