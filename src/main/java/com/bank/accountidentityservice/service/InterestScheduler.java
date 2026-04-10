package com.bank.accountidentityservice.service;

import com.bank.accountidentityservice.entity.Account;
import com.bank.accountidentityservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class InterestScheduler {

    private final AccountRepository accountRepository;

    // Interest rate: 1% per month
    private static final BigDecimal INTEREST_RATE = new BigDecimal("0.01");


    @Scheduled(cron = "0 1 0 1 * *")
    @Transactional
    public void applyMonthlyInterest() {
        log.info("=== Monthly Interest Job Started ===");

        // Fetch all ACTIVE SAVINGS accounts
        List<Account> savingsAccounts = accountRepository
                .findByAccountTypeAndStatus(
                        Account.AccountType.SAVINGS,
                        Account.AccountStatus.ACTIVE);

        if (savingsAccounts.isEmpty()) {
            log.info("No active savings accounts found. Interest job complete.");
            return;
        }

        int count = 0;
        BigDecimal totalInterestPaid = BigDecimal.ZERO;

        for (Account account : savingsAccounts) {
            BigDecimal currentBalance = account.getBalance();

            // Skip zero balance accounts — no interest on empty account
            if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Skipping zero balance account: {}", account.getAccountNumber());
                continue;
            }

            // Calculate interest — rounded to 2 decimal places
            BigDecimal interest = currentBalance
                    .multiply(INTEREST_RATE)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newBalance = currentBalance.add(interest);

            account.setBalance(newBalance);
            accountRepository.save(account);

            totalInterestPaid = totalInterestPaid.add(interest);
            count++;

            log.info("Interest applied — Account: {} | Balance: ₹{} → ₹{} | Interest: ₹{}",
                    account.getAccountNumber(),
                    currentBalance,
                    newBalance,
                    interest);
        }

        log.info("=== Monthly Interest Job Complete === Accounts credited: {} | Total interest paid: ₹{}",
                count, totalInterestPaid);
    }
}
