package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.config.AppConfig;
import com.bank.accountidentityservice.dto.request.AddOfficerRequest;
import com.bank.accountidentityservice.dto.request.AdminLoginRequest;
import com.bank.accountidentityservice.dto.response.ComplianceLoginResponse;
import com.bank.accountidentityservice.dto.response.ComplianceOfficerResponse;
import com.bank.accountidentityservice.entity.ComplianceOfficer;
import com.bank.accountidentityservice.exception.CustomExceptions.*;
import com.bank.accountidentityservice.repository.ComplianceOfficerRepository;
import com.bank.accountidentityservice.repository.RefreshTokenRepository;
import com.bank.accountidentityservice.util.OfficerIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final ComplianceOfficerRepository complianceOfficerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final OfficerIdGenerator officerIdGenerator;
    private final AppConfig appConfig;

    @Value("${app.admin.id}")
    private String adminId;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.name:System Administrator}")
    private String adminName;

    // Characters used for temporary password generation
    // Mix of upper, lower, digits — easy to type but not guessable
    private static final String TEMP_PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Admin Login ───────────────────────────────────────────────
    public ComplianceLoginResponse adminLogin(AdminLoginRequest request) {
        if (!adminId.equals(request.getAdminId())) {
            throw new InvalidCredentialsException("Invalid admin credentials.");
        }
        if (!adminPassword.equals(request.getPassword())) {
            throw new InvalidCredentialsException("Invalid admin credentials.");
        }

        String accessToken = jwtService.generateAccessToken(adminId, List.of("ADMIN"));

        log.info("Admin logged in: {}", adminId);

        return ComplianceLoginResponse.builder()
                .officerId(adminId)
                .fullName(adminName)
                .email("admin@securebank.com")
                .role("ADMIN")
                .accessToken(accessToken)
                .refreshToken("")
                .accessTokenExpiresInMs(jwtService.getAccessTokenExpiryMs())
                .build();
    }

    // ── Add a new compliance officer ──────────────────────────────
    // Flow:
    // 1. Admin enters fullName, dob, email
    // 2. System generates officerId + temporary 8-char password
    // 3. Saves officer as APPROVED with firstLogin=true
    // 4. Emails officer their ID + temporary password
    // 5. Officer logs in with temp password → redirected to set-password page
    // 6. Officer sets permanent password → firstLogin set to false
    @Transactional
    public ComplianceOfficerResponse addOfficer(String adminCustomerId,
                                                AddOfficerRequest request) {
        if (complianceOfficerRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "A compliance officer with this email already exists.");
        }

        String officerId    = officerIdGenerator.generate();
        String tempPassword = generateTempPassword();

        ComplianceOfficer officer = ComplianceOfficer.builder()
                .officerId(officerId)
                .fullName(request.getFullName())
                .dob(request.getDob())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .status(ComplianceOfficer.OfficerStatus.APPROVED)
                .firstLogin(true)
                .approvedBy(adminCustomerId)
                .approvedAt(LocalDateTime.now())
                .build();

        complianceOfficerRepository.save(officer);

        // Email officer their credentials
        emailService.sendOfficerCreatedEmail(
                officer.getEmail(),
                officer.getFullName(),
                officerId,
                tempPassword);

        log.info("Compliance officer created: {} → officerId={}", officer.getEmail(), officerId);
        return ComplianceOfficerResponse.from(officer);
    }

    // ── List all compliance officers ──────────────────────────────
    public List<ComplianceOfficerResponse> getAllOfficers() {
        return complianceOfficerRepository.findAll()
                .stream()
                .map(ComplianceOfficerResponse::from)
                .collect(Collectors.toList());
    }

    // ── Suspend a compliance officer ──────────────────────────────
    @Transactional
    public ComplianceOfficerResponse removeOfficer(String officerId) {
        ComplianceOfficer officer = complianceOfficerRepository.findByOfficerId(officerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "No compliance officer found with ID: " + officerId));

        officer.setStatus(ComplianceOfficer.OfficerStatus.SUSPENDED);
        complianceOfficerRepository.save(officer);
        refreshTokenRepository.revokeAllByCustomerId(officerId);

        log.info("Compliance officer suspended: {}", officerId);
        return ComplianceOfficerResponse.from(officer);
    }

    // ── Reactivate a suspended officer ────────────────────────────
    @Transactional
    public ComplianceOfficerResponse reactivateOfficer(String officerId) {
        ComplianceOfficer officer = complianceOfficerRepository.findByOfficerId(officerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "No compliance officer found with ID: " + officerId));

        if (officer.getStatus() != ComplianceOfficer.OfficerStatus.SUSPENDED) {
            throw new IllegalStateException("Officer is not currently suspended.");
        }

        officer.setStatus(ComplianceOfficer.OfficerStatus.APPROVED);
        complianceOfficerRepository.save(officer);

        log.info("Compliance officer reactivated: {}", officerId);
        return ComplianceOfficerResponse.from(officer);
    }

    // ── Resend credentials email ──────────────────────────────────
    // Generates a new temporary password and emails it again.
    // Only works if officer has not yet changed their password (firstLogin=true).
    @Transactional
    public void resendCredentials(String officerId) {
        ComplianceOfficer officer = complianceOfficerRepository.findByOfficerId(officerId)
                .orElseThrow(() -> new UserNotFoundException(
                        "No compliance officer found with ID: " + officerId));

        if (!officer.isFirstLogin()) {
            throw new IllegalStateException(
                    "Officer has already set their permanent password.");
        }

        String tempPassword = generateTempPassword();
        officer.setPasswordHash(passwordEncoder.encode(tempPassword));
        complianceOfficerRepository.save(officer);

        emailService.sendOfficerCreatedEmail(
                officer.getEmail(), officer.getFullName(), officerId, tempPassword);

        log.info("Credentials resent for officer: {}", officerId);
    }

    // ── Generate a random 8-character temporary password ─────────
    // Uses SecureRandom — cryptographically safe
    // Excludes confusing characters: 0, O, I, l, 1
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(TEMP_PWD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PWD_CHARS.length())));
        }
        return sb.toString();
    }
}
