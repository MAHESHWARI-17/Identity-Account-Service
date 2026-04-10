package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginRequest {

    @NotBlank(message = "Admin ID is required.")
    private String adminId;

    @NotBlank(message = "Password is required.")
    private String password;
}
