package com.bank.accountidentityservice.dto.response;

import com.bank.accountidentityservice.entity.ComplianceOfficer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ComplianceOfficerResponse {
    private String officerId;
    private String fullName;
    private LocalDate dob;
    private String email;
    private String status;
    private boolean firstLogin;
    private String rejectionReason;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;

    public static ComplianceOfficerResponse from(ComplianceOfficer officer) {
        return ComplianceOfficerResponse.builder()
                .officerId(officer.getOfficerId())
                .fullName(officer.getFullName())
                .dob(officer.getDob())
                .email(officer.getEmail())
                .status(officer.getStatus().name())
                .firstLogin(officer.isFirstLogin())   // ← now included
                .rejectionReason(officer.getRejectionReason())
                .approvedBy(officer.getApprovedBy())
                .approvedAt(officer.getApprovedAt())
                .createdAt(officer.getCreatedAt())
                .build();
    }
}
