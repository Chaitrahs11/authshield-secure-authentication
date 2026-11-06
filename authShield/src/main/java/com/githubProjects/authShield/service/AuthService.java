package com.githubProjects.authShield.service;

import com.githubProjects.authShield.common.ApiException;
import com.githubProjects.authShield.dto.AuthRequests;
import com.githubProjects.authShield.dto.AuthResponses;
import com.githubProjects.authShield.dto.OtpPurpose;
import com.githubProjects.authShield.entity.AppUser;
import com.githubProjects.authShield.entity.OtpChallenge;
import com.githubProjects.authShield.repository.OtpChallengeRepository;
import com.githubProjects.authShield.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OtpChallengeRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailService emailService;
    private final JwtService jwtService;
    private final long otpExpirationMinutes;
    private final long resendCooldownSeconds;
    private final int maxAttempts;
    private final int maxSends;
    private final long sendWindowMinutes;

    public AuthService(UserRepository userRepository, OtpChallengeRepository otpRepository,
            PasswordEncoder passwordEncoder, OtpEmailService emailService, JwtService jwtService,
            @Value("${authshield.otp.expiration-minutes}") long otpExpirationMinutes,
            @Value("${authshield.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
            @Value("${authshield.otp.max-attempts}") int maxAttempts,
            @Value("${authshield.otp.max-sends}") int maxSends,
            @Value("${authshield.otp.send-window-minutes}") long sendWindowMinutes) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
        this.maxSends = maxSends;
        this.sendWindowMinutes = sendWindowMinutes;
    }

    @Transactional
    public AuthResponses.Message register(AuthRequests.Register request) {
        LOGGER.info("Registration started");
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            LOGGER.info("Registration rejected because the email is already registered");
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        AppUser user = userRepository.save(new AppUser(email, passwordEncoder.encode(request.password())));
        LOGGER.info("Registration user record persisted; creating verification challenge");
        createAndSendChallenge(user, OtpPurpose.VERIFY_EMAIL);
        LOGGER.info("Registration and verification email completed");
        return new AuthResponses.Message("Account created. Check your email for the verification code.");
    }

    @Transactional
    public boolean verifyEmail(AuthRequests.Otp request) {
        AppUser user = userRepository.findByEmailForUpdate(normalizeEmail(request.email())).orElse(null);
        if (user == null || user.isVerified()) {
            return false;
        }
        OtpChallenge challenge = findLatestChallenge(user, OtpPurpose.VERIFY_EMAIL);
        if (!matchesChallenge(challenge, request.code())) {
            return false;
        }
        challenge.consume(Instant.now());
        user.verify();
        return true;
    }

    @Transactional
    public AuthResponses.Message resendVerification(AuthRequests.EmailOnly request) {
        AppUser user = userRepository.findByEmailForUpdate(normalizeEmail(request.email()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account was not found"));
        if (user.isVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already verified");
        }
        createAndSendChallenge(user, OtpPurpose.VERIFY_EMAIL);
        return new AuthResponses.Message("A new verification code has been sent.");
    }

    public AuthResponses.Token login(AuthRequests.Login request) {
        AppUser user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        }
        if (!user.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Verify your email before signing in");
        }
        return new AuthResponses.Token(jwtService.issueToken(user), "Bearer",
                jwtService.getExpirationSeconds(), user.getEmail());
    }

    @Transactional
    public AuthResponses.Message requestPasswordReset(AuthRequests.EmailOnly request) {
        userRepository.findByEmailForUpdate(normalizeEmail(request.email())).ifPresent(user -> {
            if (user.isVerified()) {
                createAndSendChallenge(user, OtpPurpose.RESET_PASSWORD);
            }
        });
        return new AuthResponses.Message("If an eligible account exists, a reset code has been sent.");
    }

    @Transactional
    public boolean resetPassword(AuthRequests.PasswordReset request) {
        AppUser user = userRepository.findByEmailForUpdate(normalizeEmail(request.email())).orElse(null);
        if (user == null || !user.isVerified()) {
            return false;
        }
        OtpChallenge challenge = findLatestChallenge(user, OtpPurpose.RESET_PASSWORD);
        if (!matchesChallenge(challenge, request.code())) {
            return false;
        }
        challenge.consume(Instant.now());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return true;
    }

    @Transactional(readOnly = true)
    public AuthResponses.Profile getProfile(String email) {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));
        return new AuthResponses.Profile(user.getEmail(), user.isVerified());
    }

    private void createAndSendChallenge(AppUser user, OtpPurpose purpose) {
        Instant now = Instant.now();
        List<OtpChallenge> current = otpRepository.findLatestUnconsumedForUpdate(
                user.getId(), purpose, PageRequest.of(0, 1));
        if (!current.isEmpty()) {
            OtpChallenge latest = current.get(0);
            if (latest.getResendAvailableAt().isAfter(now)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code");
            }
        }
        Instant windowStart = now.minus(sendWindowMinutes, ChronoUnit.MINUTES);
        if (otpRepository.findCreatedSince(user.getId(), purpose, windowStart).size() >= maxSends) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Try again later.");
        }
        current.forEach(challenge -> challenge.consume(now));

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge(user, passwordEncoder.encode(code), purpose,
                now.plus(otpExpirationMinutes, ChronoUnit.MINUTES),
                now.plus(resendCooldownSeconds, ChronoUnit.SECONDS));
        otpRepository.save(challenge);
        LOGGER.info("OTP challenge persisted for purpose {}", purpose);
        emailService.send(user, code, purpose, otpExpirationMinutes);
    }

    private OtpChallenge findLatestChallenge(AppUser user, OtpPurpose purpose) {
        return otpRepository.findLatestUnconsumedForUpdate(user.getId(), purpose, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    private boolean matchesChallenge(OtpChallenge challenge, String code) {
        if (challenge == null || !challenge.isActiveAt(Instant.now(), maxAttempts)) {
            return false;
        }
        if (!passwordEncoder.matches(code, challenge.getOtpHash())) {
            challenge.recordFailure(maxAttempts, Instant.now());
            return false;
        }
        return true;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}