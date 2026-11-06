package com.githubProjects.authShield.dto;

import java.time.Instant;
import java.util.List;

public final class AuthResponses {
    private AuthResponses() {
    }

    public record Message(String message) { }
    public record Token(String accessToken, String tokenType, long expiresInSeconds, String email) { }
    public record Profile(String email, boolean verified) { }
    public record DashboardOverview(String email, String accountStatus, boolean emailVerified,
            String authenticationMethod, Instant accountCreatedAt, String securityStatus,
            String message, List<String> securityFeatures) { }
}