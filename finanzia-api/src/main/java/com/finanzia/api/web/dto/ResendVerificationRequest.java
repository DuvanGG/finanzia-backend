package com.finanzia.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(@NotBlank String email) {
}