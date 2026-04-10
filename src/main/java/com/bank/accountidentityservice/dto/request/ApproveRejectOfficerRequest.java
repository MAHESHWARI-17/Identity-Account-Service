package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApproveRejectOfficerRequest {

    @NotBlank(message = "Officer email or ID is required.")
    private String officerEmail;

    // Required only when action = REJECT
    private String rejectionReason;
}
