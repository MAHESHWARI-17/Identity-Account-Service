package com.bank.accountidentityservice.controller;

import com.bank.accountidentityservice.dto.request.*;
import com.bank.accountidentityservice.dto.response.*;
import com.bank.accountidentityservice.service.AdminService;
import com.bank.accountidentityservice.service.ComplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ComplianceService complianceService;

    // ── POST /admin/login ─────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ComplianceLoginResponse>> adminLogin(
            @Valid @RequestBody AdminLoginRequest request) {
        ComplianceLoginResponse response = adminService.adminLogin(request);
        return ResponseEntity.ok(ApiResponse.success("Admin login successful.", response));
    }

    // ── POST /admin/officers/add ──────────────────────────────────
    // Admin creates officer → system generates officerId + temp password
    // Officer receives email with both credentials
    @PostMapping("/officers/add")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceOfficerResponse>> addOfficer(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody AddOfficerRequest request) {
        ComplianceOfficerResponse response = adminService.addOfficer(adminId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Officer created. Credentials sent via email.", response));
    }

    // ── POST /admin/officers/{officerId}/resend-credentials ───────
    // Resends a new temp password email if officer hasn't logged in yet
    @PostMapping("/officers/{officerId}/resend-credentials")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resendCredentials(
            @PathVariable String officerId) {
        adminService.resendCredentials(officerId);
        return ResponseEntity.ok(ApiResponse.success(
                "New temporary credentials sent to officer's email."));
    }

    // ── GET /admin/officers ───────────────────────────────────────
    @GetMapping("/officers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ComplianceOfficerResponse>>> getAllOfficers() {
        List<ComplianceOfficerResponse> list = adminService.getAllOfficers();
        return ResponseEntity.ok(ApiResponse.success("All compliance officers fetched.", list));
    }

    // ── DELETE /admin/officers/{officerId} ────────────────────────
    @DeleteMapping("/officers/{officerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceOfficerResponse>> removeOfficer(
            @PathVariable String officerId) {
        ComplianceOfficerResponse response = adminService.removeOfficer(officerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Compliance officer suspended successfully.", response));
    }

    // ── POST /admin/officers/{officerId}/reactivate ───────────────
    @PostMapping("/officers/{officerId}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplianceOfficerResponse>> reactivateOfficer(
            @PathVariable String officerId) {
        ComplianceOfficerResponse response = adminService.reactivateOfficer(officerId);
        return ResponseEntity.ok(ApiResponse.success("Compliance officer reactivated.", response));
    }

    // ── POST /admin/accounts/freeze ───────────────────────────────
    @PostMapping("/accounts/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @Valid @RequestBody AccountActionRequest request) {
        AccountResponse response = complianceService.freezeAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account frozen by admin.", response));
    }

    // ── POST /admin/accounts/unfreeze ─────────────────────────────
    @PostMapping("/accounts/unfreeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @Valid @RequestBody AccountActionRequest request) {
        AccountResponse response = complianceService.unfreezeAccount(request);
        return ResponseEntity.ok(ApiResponse.success("Account unfrozen by admin.", response));
    }

    // ── POST /admin/users/{customerId}/lock ───────────────────────
    @PostMapping("/users/{customerId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> lockUser(
            @PathVariable String customerId) {
        UserProfileResponse response = complianceService.lockUser(customerId);
        return ResponseEntity.ok(ApiResponse.success("User account locked by admin.", response));
    }

    // ── POST /admin/users/{customerId}/unlock ─────────────────────
    @PostMapping("/users/{customerId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> unlockUser(
            @PathVariable String customerId) {
        UserProfileResponse response = complianceService.unlockUser(customerId);
        return ResponseEntity.ok(ApiResponse.success("User account unlocked.", response));
    }

    // ── GET /admin/users ──────────────────────────────────────────
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers() {
        List<UserProfileResponse> users = complianceService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("All users fetched.", users));
    }

    // ── GET /admin/users/{customerId} ─────────────────────────────
    @GetMapping("/users/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable String customerId) {
        UserProfileResponse profile = complianceService.getUserProfile(customerId);
        return ResponseEntity.ok(ApiResponse.success("User profile fetched.", profile));
    }
}
