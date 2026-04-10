package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccountActionRequest {

    @NotBlank(message = "Account number is required.")
    private String accountNumber;

    private String reason;
}
