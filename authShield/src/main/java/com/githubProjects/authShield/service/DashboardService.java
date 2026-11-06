package com.githubProjects.authShield.service;

import com.githubProjects.authShield.common.ApiException;
import com.githubProjects.authShield.dto.AuthResponses;
import com.githubProjects.authShield.entity.AppUser;
import com.githubProjects.authShield.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final UserRepository userRepository;

    public DashboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AuthResponses.DashboardOverview getOverview(String authenticatedEmail) {
        AppUser user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));

        return new AuthResponses.DashboardOverview(
                user.getEmail(),
                user.isVerified() ? "VERIFIED" : "UNVERIFIED",
                user.isVerified(),
                "JWT",
                user.getCreatedAt(),
                "PROTECTED",
                "Your account is protected.",
                List.of("BCrypt password hashing", "Email verification", "JWT-protected API access"));
    }
}