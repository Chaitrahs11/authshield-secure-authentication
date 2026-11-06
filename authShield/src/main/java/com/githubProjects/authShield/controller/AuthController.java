package com.githubProjects.authShield.controller;

import com.githubProjects.authShield.dto.AuthRequests;
import com.githubProjects.authShield.dto.AuthResponses;
import com.githubProjects.authShield.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponses.Message> register(@Valid @RequestBody AuthRequests.Register request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponses.Message> verifyEmail(@Valid @RequestBody AuthRequests.Otp request) {
        boolean verified = authService.verifyEmail(request);
        return ResponseEntity.status(verified ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(new AuthResponses.Message(verified ? "Email verified. You can now sign in."
                        : "The verification code is invalid or expired."));
    }

    @PostMapping("/resend-verification")
    public AuthResponses.Message resendVerification(@Valid @RequestBody AuthRequests.EmailOnly request) {
        return authService.resendVerification(request);
    }

    @PostMapping("/login")
    public AuthResponses.Token login(@Valid @RequestBody AuthRequests.Login request) {
        return authService.login(request);
    }

    @PostMapping("/forgot-password")
    public AuthResponses.Message forgotPassword(@Valid @RequestBody AuthRequests.EmailOnly request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponses.Message> resetPassword(
            @Valid @RequestBody AuthRequests.PasswordReset request) {
        boolean reset = authService.resetPassword(request);
        return ResponseEntity.status(reset ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                .body(new AuthResponses.Message(reset ? "Password updated successfully."
                        : "The reset code is invalid or expired."));
    }

    @GetMapping("/me")
    public AuthResponses.Profile profile(Authentication authentication) {
        return authService.getProfile(authentication.getName());
    }
}