package com.bank.accountidentityservice.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplianceLoginResponse {
    private String officerId;
    private String fullName;
    private String email;
    private String role;
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresInMs;
}
