package com.bank.accountidentityservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AddOfficerRequest {

    @NotBlank(message = "Full name is required.")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotNull(message = "Date of birth is required.")
    @Past(message = "Date of birth must be in the past.")
    private LocalDate dob;

    @NotBlank(message = "Email is required.")
    @Email(message = "Please provide a valid email address.")
    private String email;
}
