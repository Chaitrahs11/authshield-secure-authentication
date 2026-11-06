package com.githubProjects.authShield.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.verified = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isVerified() { return verified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void verify() {
        this.verified = true;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
        this.updatedAt = Instant.now();
    }
}