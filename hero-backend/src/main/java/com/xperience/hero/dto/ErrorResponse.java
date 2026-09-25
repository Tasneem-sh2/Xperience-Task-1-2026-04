package com.xperience.hero.dto;

public record ErrorResponse(int status, String error, String message) {
}
