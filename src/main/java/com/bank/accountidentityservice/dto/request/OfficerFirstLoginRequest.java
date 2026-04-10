package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OfficerFirstLoginRequest {

    @NotBlank(message = "Officer ID is required.")
    private String officerId;

    @NotBlank(message = "OTP is required.")
    private String otp;
}
