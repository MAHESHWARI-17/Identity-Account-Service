package com.bank.accountidentityservice.controller;

import com.bank.accountidentityservice.dto.request.*;
import com.bank.accountidentityservice.dto.response.AccountResponse;
import com.bank.accountidentityservice.dto.response.ApiResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────
    // INTERNAL ENDPOINTS — called by transaction-service only
    // These are authenticated via the same JWT the user holds.
    // ─────────────────────────────────────────────────────────────

    // GET /accounts/balance/{accountNumber}
    // Returns balance for a valid ACTIVE account.
    // 403 if PENDING_PIN, FROZEN, or CLOSED.
    @GetMapping("/balance/{accountNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBalance(
            @PathVariable String accountNumber) {
        Map<String, Object> data = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Balance fetched.", data));
    }

    // PUT /accounts/balance/update
    // Updates the balance after a successful transaction.
    @PutMapping("/balance/update")
    public ResponseEntity<ApiResponse<Void>> updateBalance(
            @Valid @RequestBody UpdateBalanceRequest request) {
        accountService.updateBalance(request.getAccountNumber(), request.getNewBalance());
        return ResponseEntity.ok(ApiResponse.success("Balance updated successfully."));
    }

    // POST /accounts/verify-pin
    // Verifies a PIN and returns the current balance.
    // 401 if PIN is wrong, 403 if account is not ACTIVE.
    @PostMapping("/verify-pin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyPin(
            @Valid @RequestBody VerifyPinRequest request) {
        Map<String, Object> data =
                accountService.verifyPinAndGetBalance(request.getAccountNumber(), request.getPin());
        return ResponseEntity.ok(ApiResponse.success("PIN verified.", data));
    }

    // GET /accounts/info/{accountNumber}
    // Returns account metadata including createdAt (for new-recipient check).
    @GetMapping("/info/{accountNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccountInfo(
            @PathVariable String accountNumber) {
        Map<String, Object> info = accountService.getAccountInfo(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account info fetched.", info));
    }

    // GET /accounts/holder/{accountNumber}
    // Returns the customer name and email for notification emails.
    @GetMapping("/holder/{accountNumber}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAccountHolder(
            @PathVariable String accountNumber) {
        Map<String, String> holder = accountService.getAccountHolder(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account holder fetched.", holder));
    }

    // GET /accounts/owner/{accountNumber}
    // Returns the customerId that owns the account (used by audit service).
    @GetMapping("/owner/{accountNumber}")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAccountOwner(
            @PathVariable String accountNumber) {
        Map<String, String> owner = accountService.getAccountOwner(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account owner fetched.", owner));
    }

    // ─────────────────────────────────────────────────────────────
    // USER-FACING ENDPOINTS
    // ─────────────────────────────────────────────────────────────

    // GET /accounts/profile
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal String customerId) {
        UserProfileResponse profile = accountService.getProfile(customerId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully.", profile));
    }

    // POST /accounts/add
    @PostMapping("/add")
    public ResponseEntity<ApiResponse<AccountResponse>> addAccount(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody AddAccountRequest request) {
        AccountResponse account = accountService.addAccount(customerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Account added successfully. Please set your PIN to activate it.",
                        account));
    }

    // POST /accounts/pin/request-otp
    @PostMapping("/pin/request-otp")
    public ResponseEntity<ApiResponse<Void>> requestPinOtp(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody RequestPinOtpRequest request) {
        accountService.requestPinOtp(customerId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "OTP sent to your registered email. Valid for 10 minutes."));
    }

    // POST /accounts/pin/set
    @PostMapping("/pin/set")
    public ResponseEntity<ApiResponse<AccountResponse>> setPin(
            @AuthenticationPrincipal String customerId,
            @Valid @RequestBody SetPinRequest request) {
        AccountResponse account = accountService.setPin(customerId, request);
        return ResponseEntity.ok(ApiResponse.success(
                "PIN set successfully. Your account is now ACTIVE and ready to use.", account));
    }
}
