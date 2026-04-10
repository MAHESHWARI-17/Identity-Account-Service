package com.bank.accountidentityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "compliance_officers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplianceOfficer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "officer_id", unique = true, length = 15)
    private String officerId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "dob", nullable = false)
    private LocalDate dob;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    // Stores BCrypt hash of the temporary password initially.
    // After officer sets their own password, stores the new password hash.
    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OfficerStatus status = OfficerStatus.APPROVED;

    // true  = officer logging in for first time with temp password → must change password
    // false = officer has set their own password → normal login
    @Column(name = "first_login", nullable = false)
    @Builder.Default
    private boolean firstLogin = true;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approved_by", length = 15)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum OfficerStatus {
        APPROVED,
        SUSPENDED
    }
}
