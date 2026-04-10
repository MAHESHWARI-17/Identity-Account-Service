package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.config.AppConfig;
import com.bank.accountidentityservice.dto.request.AddAccountRequest;
import com.bank.accountidentityservice.dto.request.RequestPinOtpRequest;
import com.bank.accountidentityservice.dto.request.SetPinRequest;
import com.bank.accountidentityservice.dto.response.AccountResponse;
import com.bank.accountidentityservice.dto.response.UserProfileResponse;
import com.bank.accountidentityservice.entity.Account;
import com.bank.accountidentityservice.entity.OtpStore;
import com.bank.accountidentityservice.entity.User;
import com.bank.accountidentityservice.exception.CustomExceptions.*;
import com.bank.accountidentityservice.repository.AccountRepository;
import com.bank.accountidentityservice.repository.OtpStoreRepository;
import com.bank.accountidentityservice.repository.UserRepository;
import com.bank.accountidentityservice.util.AccountNumberGenerator;
import com.bank.accountidentityservice.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final OtpStoreRepository otpStoreRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final OtpGenerator otpGenerator;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AppConfig appConfig;
    private final AuthService authService;

    // ── Get customer profile ──────────────────────────────────────
    public UserProfileResponse getProfile(String customerId) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        return authService.buildProfileResponse(user, accounts);
    }

    // ── GET /accounts/balance/{accountNumber} ─────────────────────
    // Called internally by transaction-service to fetch current balance.
    // Returns 403 if account is not ACTIVE or FROZEN status blocks.
    public Map<String, Object> getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        if (account.getStatus() == Account.AccountStatus.PENDING_PIN) {
            throw new AccountLockedOrDisabledException(
                    "Account PIN has not been set. Please set your PIN first.");
        }
        if (account.getStatus() == Account.AccountStatus.FROZEN) {
            throw new AccountLockedOrDisabledException(
                    "Account " + accountNumber + " is frozen. Contact support.");
        }
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new AccountLockedOrDisabledException(
                    "Account " + accountNumber + " is closed.");
        }

        return Map.of(
                "accountNumber", account.getAccountNumber(),
                "balance",       account.getBalance(),
                "status",        account.getStatus().name()
        );
    }

    // ── PUT /accounts/balance/update ──────────────────────────────
    // Called internally by transaction-service to persist new balance after a transaction.
    @Transactional
    public void updateBalance(String accountNumber, BigDecimal newBalance) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
        account.setBalance(newBalance);
        accountRepository.save(account);
        log.info("Balance updated: {} → ₹{}", accountNumber, newBalance);
    }

    // ── POST /accounts/verify-pin ─────────────────────────────────
    // Called internally by transaction-service for withdraw/transfer.
    // Returns balance if PIN is correct; throws 401 if wrong.
    public Map<String, Object> verifyPinAndGetBalance(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));

        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new AccountLockedOrDisabledException(
                    "Account is not active. Status: " + account.getStatus());
        }
        if (account.getPinHash() == null) {
            throw new AccountLockedOrDisabledException("Account PIN has not been set.");
        }
        if (!passwordEncoder.matches(pin, account.getPinHash())) {
            throw new InvalidCredentialsException("Incorrect PIN. Please try again.");
        }

        return Map.of(
                "accountNumber", account.getAccountNumber(),
                "balance",       account.getBalance(),
                "status",        account.getStatus().name()
        );
    }

    // ── GET /accounts/info/{accountNumber} ────────────────────────
    // Returns account metadata including createdAt — used for new-recipient check.
    public Map<String, Object> getAccountInfo(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
        return Map.of(
                "accountNumber", account.getAccountNumber(),
                "customerId",    account.getCustomerId(),
                "accountType",   account.getAccountType().name(),
                "status",        account.getStatus().name(),
                "createdAt",     account.getCreatedAt().toString()
        );
    }

    // ── GET /accounts/holder/{accountNumber} ─────────────────────
    // Returns customer name & email — used by transaction-service for email notifications.
    public Map<String, String> getAccountHolder(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
        User user = userRepository.findByCustomerId(account.getCustomerId())
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found for account: " + accountNumber));
        return Map.of(
                "customerId", user.getCustomerId(),
                "fullName",   user.getFullName(),
                "email",      user.getEmail()
        );
    }

    // ── GET /accounts/owner/{accountNumber} ───────────────────────
    // Used by audit query to validate account ownership.
    public Map<String, String> getAccountOwner(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
        return Map.of(
                "customerId",    account.getCustomerId(),
                "accountNumber", accountNumber
        );
    }

    // ── Add a new account ─────────────────────────────────────────
    @Transactional
    public AccountResponse addAccount(String customerId, AddAccountRequest request) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException("User not found."));

        long existingCount = accountRepository
                .countByCustomerIdAndAccountType(customerId, request.getAccountType());
        if (existingCount >= 2)
            throw new DuplicateAccountTypeException(
                    "You already have the maximum of 2 " + request.getAccountType()
                    + " accounts allowed per customer.");

        String accountNumber = accountNumberGenerator.generate(customerId, request.getAccountType());
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customerId(customerId)
                .accountType(request.getAccountType())
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.PENDING_PIN)
                .build();
        accountRepository.save(account);

        emailService.sendNewAccountEmail(
                user.getEmail(), user.getFullName(),
                accountNumber, request.getAccountType().name());

        log.info("New account added — customerId: {}, accountNumber: {}", customerId, accountNumber);
        return authService.mapAccount(account);
    }

    // ── Request PIN setup OTP ─────────────────────────────────────
    @Transactional
    public void requestPinOtp(String customerId, RequestPinOtpRequest request) {
        User user = userRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));
        if (!account.getCustomerId().equals(customerId))
            throw new AccountNotFoundException("This account does not belong to your profile.");

        otpStoreRepository.invalidateAll(customerId, OtpStore.OtpPurpose.PIN_SETUP);
        String rawOtp = otpGenerator.generate();
        OtpStore otpRecord = OtpStore.builder()
                .customerId(customerId)
                .otpHash(passwordEncoder.encode(rawOtp))
                .purpose(OtpStore.OtpPurpose.PIN_SETUP)
                .refValue(request.getAccountNumber())
                .expiresAt(LocalDateTime.now().plusMinutes(appConfig.getOtpExpiryMinutes()))
                .build();
        otpStoreRepository.save(otpRecord);
        emailService.sendPinSetupOtp(
                user.getEmail(), user.getFullName(),
                request.getAccountNumber(), rawOtp);
        log.info("PIN setup OTP sent — customerId: {}, accountNumber: {}",
                customerId, request.getAccountNumber());
    }

    // ── Set PIN and activate account ──────────────────────────────
    @Transactional
    public AccountResponse setPin(String customerId, SetPinRequest request) {
        if (!request.getPin().equals(request.getConfirmPin()))
            throw new PinMismatchException("PIN and Confirm PIN do not match. Please re-enter.");

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + request.getAccountNumber()));
        if (!account.getCustomerId().equals(customerId))
            throw new AccountNotFoundException("This account does not belong to your profile.");

        List<OtpStore> validOtps = otpStoreRepository
                .findValidOtps(customerId, OtpStore.OtpPurpose.PIN_SETUP)
                .stream()
                .filter(o -> request.getAccountNumber().equals(o.getRefValue()))
                .toList();
        if (validOtps.isEmpty())
            throw new OtpExpiredException("OTP has expired or not found. Please request a new OTP.");

        OtpStore otpRecord = validOtps.get(0);
        if (!passwordEncoder.matches(request.getOtp(), otpRecord.getOtpHash()))
            throw new InvalidOtpException("Incorrect OTP. Please check and try again.");

        otpRecord.setUsedAt(LocalDateTime.now());
        otpStoreRepository.save(otpRecord);
        account.setPinHash(passwordEncoder.encode(request.getPin()));
        account.setStatus(Account.AccountStatus.ACTIVE);
        accountRepository.save(account);

        log.info("PIN set successfully — accountNumber: {}", request.getAccountNumber());
        return authService.mapAccount(account);
    }
}
