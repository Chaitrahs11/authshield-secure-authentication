package com.githubProjects.authShield.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record Register(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 72) String password) { }

    public record Login(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank String password) { }

    public record EmailOnly(@NotBlank @Email @Size(max = 320) String email) { }

    public record Otp(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) { }

    public record PasswordReset(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @NotBlank @Size(min = 12, max = 72) String newPassword) { }
}