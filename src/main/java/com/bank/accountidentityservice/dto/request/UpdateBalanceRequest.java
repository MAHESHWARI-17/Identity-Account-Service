package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateBalanceRequest {

    @NotBlank(message = "Account number is required.")
    private String accountNumber;

    @NotNull(message = "New balance is required.")
    private BigDecimal newBalance;
}
