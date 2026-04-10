package com.bank.accountidentityservice.controller;

import com.bank.accountidentityservice.dto.request.*;
import com.bank.accountidentityservice.dto.response.*;
import com.bank.accountidentityservice.service.ComplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;

    // ── POST /compliance/login ────────────────────────────────────
    // Returns role=COMPLIANCE_SETUP if firstLogin=true (must set password)
    // Returns role=COMPLIANCE if normal login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ComplianceLoginResponse>> login(
            @Valid @RequestBody ComplianceOfficerLoginRequest request) {
        ComplianceLoginResponse response = complianceService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    // ── POST /compliance/login/set-password ───────────────────────
    // Called after first login with temp password.
    // Officer submits: officerId, newPassword, confirmPassword.
    // Returns full COMPLIANCE token on success.
    @PostMapping("/login/set-password")
    public ResponseEntity<ApiResponse<ComplianceLoginResponse>> setFirstPassword(
            @Valid @RequestBody OfficerSetPasswordRequest request) {
        ComplianceLoginResponse response = complianceService.setFirstPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Password set successfully. You are now logged in.", response));
    }

    // ── POST /compliance/accounts/freeze ─────────────────────────
    @PostMapping("/accounts/freeze")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @Valid @RequestBody AccountActionRequest request) {
        AccountResponse response = complianceService.freezeAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account frozen successfully.", response));
    }

    // ── POST /compliance/accounts/unfreeze ────────────────────────
    @PostMapping("/accounts/unfreeze")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @Valid @RequestBody AccountActionRequest request) {
        AccountResponse response = complianceService.unfreezeAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account unfrozen successfully.", response));
    }

    // ── POST /compliance/users/{customerId}/lock ──────────────────
    @PostMapping("/users/{customerId}/lock")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> lockUser(
            @PathVariable String customerId) {
        UserProfileResponse response = complianceService.lockUser(customerId);
        return ResponseEntity.ok(ApiResponse.success("User account locked.", response));
    }

    // ── POST /compliance/users/{customerId}/unlock ────────────────
    @PostMapping("/users/{customerId}/unlock")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> unlockUser(
            @PathVariable String customerId) {
        UserProfileResponse response = complianceService.unlockUser(customerId);
        return ResponseEntity.ok(ApiResponse.success("User account unlocked.", response));
    }

    // ── GET /compliance/users ─────────────────────────────────────
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers() {
        List<UserProfileResponse> users = complianceService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("All users fetched.", users));
    }

    // ── GET /compliance/users/{customerId} ────────────────────────
    @GetMapping("/users/{customerId}")
    @PreAuthorize("hasAnyRole('COMPLIANCE','ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable String customerId) {
        UserProfileResponse profile = complianceService.getUserProfile(customerId);
        return ResponseEntity.ok(ApiResponse.success("User profile fetched.", profile));
    }
}
