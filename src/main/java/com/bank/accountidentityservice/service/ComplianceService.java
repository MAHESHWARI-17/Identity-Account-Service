package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.dto.request.AccountActionRequest;
import com.bank.accountidentityservice.dto.request.ComplianceOfficerLoginRequest;
import com.bank.accountidentityservice.dto.request.OfficerSetPasswordRequest;
import com.bank.accountidentityservice.dto.response.AccountResponse;
import com.bank.accountidentityservice.dto.response.ComplianceLoginResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.entity.Account;
import com.bank.accountidentityservice.entity.ComplianceOfficer;
import com.bank.accountidentityservice.entity.User;
import com.bank.accountidentityservice.exception.CustomExceptions.*;
import com.bank.accountidentityservice.repository.AccountRepository;
import com.bank.accountidentityservice.repository.ComplianceOfficerRepository;
import com.bank.accountidentityservice.repository.RefreshTokenRepository;
import com.bank.accountidentityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    private final ComplianceOfficerRepository complianceOfficerRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthService authService;

    // ── Login ─────────────────────────────────────────────────────
    // If firstLogin=true and password matches temp password:
    //   → returns response with role=COMPLIANCE_SETUP
    //   → frontend must redirect to set-password page
    // If firstLogin=false and password matches:
    //   → normal login, returns COMPLIANCE role token
    public ComplianceLoginResponse login(ComplianceOfficerLoginRequest request) {
        ComplianceOfficer officer = complianceOfficerRepository
                .findByOfficerId(request.getOfficerId())
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Invalid Officer ID or password."));

        if (officer.getStatus() == ComplianceOfficer.OfficerStatus.SUSPENDED) {
            throw new AccountLockedOrDisabledException(
                    "Your account has been suspended. Please contact admin.");
        }

        if (!passwordEncoder.matches(request.getPassword(), officer.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid Officer ID or password.");
        }

        // First login — temporary password matched → must set permanent password
        if (officer.isFirstLogin()) {
            // Generate a short-lived setup token
            String setupToken = jwtService.generateAccessToken(
                    officer.getOfficerId(), List.of("COMPLIANCE_SETUP"));

            log.info("Compliance officer first login — password reset required: {}",
                    officer.getOfficerId());

            return ComplianceLoginResponse.builder()
                    .officerId(officer.getOfficerId())
                    .fullName(officer.getFullName())
                    .email(officer.getEmail())
                    .role("COMPLIANCE_SETUP")   // signals frontend to show set-password page
                    .accessToken(setupToken)
                    .refreshToken("")
                    .accessTokenExpiresInMs(jwtService.getAccessTokenExpiryMs())
                    .build();
        }

        // Normal login
        String accessToken = jwtService.generateAccessToken(
                officer.getOfficerId(), List.of("COMPLIANCE"));

        log.info("Compliance officer logged in: {}", officer.getOfficerId());

        return ComplianceLoginResponse.builder()
                .officerId(officer.getOfficerId())
                .fullName(officer.getFullName())
                .email(officer.getEmail())
                .role("COMPLIANCE")
                .accessToken(accessToken)
                .refreshToken("")
                .accessTokenExpiresInMs(jwtService.getAccessTokenExpiryMs())
                .build();
    }

    // ── Set Permanent Password ────────────────────────────────────
    // Called after first login — officer sets their own password.
    // No OTP required — the setup token from login() authenticates this call.
    @Transactional
    public ComplianceLoginResponse setFirstPassword(OfficerSetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new PinMismatchException("Passwords do not match. Please re-enter.");
        }

        ComplianceOfficer officer = complianceOfficerRepository
                .findByOfficerId(request.getOfficerId())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid Officer ID."));

        if (!officer.isFirstLogin()) {
            throw new IllegalStateException(
                    "Password has already been set. Please use normal login.");
        }

        // Save new password and mark first login complete
        officer.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        officer.setFirstLogin(false);
        complianceOfficerRepository.save(officer);

        // Generate full COMPLIANCE access token — officer is now fully logged in
        String accessToken = jwtService.generateAccessToken(
                officer.getOfficerId(), List.of("COMPLIANCE"));

        log.info("Compliance officer password set, fully activated: {}",
                officer.getOfficerId());

        return ComplianceLoginResponse.builder()
                .officerId(officer.getOfficerId())
                .fullName(officer.getFullName())
                .email(officer.getEmail())
                .role("COMPLIANCE")
                .accessToken(accessToken)
                .refreshToken("")
                .accessTokenExpiresInMs(jwtService.getAccessTokenExpiryMs())
                .build();
    }

    // ── Freeze an account ─────────────────────────────────────────
    @Transactional
    public AccountResponse freezeAccount(AccountActionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));

        if (account.getStatus() == Account.AccountStatus.FROZEN) {
            throw new IllegalStateException("Account is already frozen.");
        }

        account.setStatus(Account.AccountStatus.FROZEN);
        accountRepository.save(account);
        log.info("Account frozen by compliance: {}", request.getAccountNumber());
        return authService.mapAccount(account);
    }

    // ── Unfreeze an account ───────────────────────────────────────
    @Transactional
    public AccountResponse unfreezeAccount(AccountActionRequest request) {
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));

        if (account.getStatus() != Account.AccountStatus.FROZEN) {
            throw new IllegalStateException("Account is not currently frozen.");
        }

        account.setStatus(Account.AccountStatus.ACTIVE);
        accountRepository.save(account);
        log.info("Account unfrozen by compliance: {}", request.getAccountNumber());
        return authService.mapAccount(account);
    }

    // ── Lock a user ───────────────────────────────────────────────
    @Transactional
    public UserProfileResponse lockUser(String customerId) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + customerId));
        user.setStatus(User.UserStatus.LOCKED);
        userRepository.save(user);
        log.info("User locked by compliance: {}", customerId);
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return authService.buildProfileResponse(user, accounts);
    }

    // ── Unlock a user ─────────────────────────────────────────────
    @Transactional
    public UserProfileResponse unlockUser(String customerId) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + customerId));

        if (user.getStatus() != User.UserStatus.LOCKED) {
            throw new IllegalStateException("User account is not currently locked.");
        }

        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("User unlocked by compliance: {}", customerId);
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return authService.buildProfileResponse(user, accounts);
    }

    // ── View user profile ─────────────────────────────────────────
    public UserProfileResponse getUserProfile(String customerId) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + customerId));
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return authService.buildProfileResponse(user, accounts);
    }

    // ── List all users ────────────────────────────────────────────
    public List<UserProfileResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> {
                    List<Account> accounts =
                            accountRepository.findByCustomerId(user.getCustomerId());
                    return authService.buildProfileResponse(user, accounts);
                })
                .collect(Collectors.toList());
    }
}
