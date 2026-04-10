package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ComplianceOfficerLoginRequest {

    @NotBlank(message = "Officer ID is required.")
    private String officerId;

    @NotBlank(message = "Password is required.")
    private String password;
}
