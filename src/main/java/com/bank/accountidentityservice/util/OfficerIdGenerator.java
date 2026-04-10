package com.bank.accountidentityservice.util;

import com.bank.accountidentityservice.repository.ComplianceOfficerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class OfficerIdGenerator {

    private final ComplianceOfficerRepository complianceOfficerRepository;

    /**
     * Generates a unique officer ID in the format: CO{YEAR}{4-digit-seq}
     * e.g. CO20260001, CO20260002, ...
     */
    public synchronized String generate() {
        String year = String.valueOf(LocalDate.now().getYear());
        long count = complianceOfficerRepository.count() + 1;
        return "CO" + year + String.format("%04d", count);
    }
}
