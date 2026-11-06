package com.githubProjects.authShield.entity;

import com.githubProjects.authShield.dto.OtpPurpose;
import com.githubProjects.authShield.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpChallenge() {
    }

    public OtpChallenge(AppUser user, String otpHash, OtpPurpose purpose, Instant expiresAt,
            Instant resendAvailableAt) {
        this.user = user;
        this.otpHash = otpHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getOtpHash() { return otpHash; }
    public OtpPurpose getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getResendAvailableAt() { return resendAvailableAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isActiveAt(Instant now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && failedAttempts < maxAttempts;
    }

    public void recordFailure(int maxAttempts, Instant now) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            consumedAt = now;
        }
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}