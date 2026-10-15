package com.finanzia.api.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String code,
        @NotBlank String newPassword) {
}