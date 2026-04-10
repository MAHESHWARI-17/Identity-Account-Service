
package com.bank.accountidentityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling   // ← Required for @Scheduled in InterestScheduler to work
public class AccountIdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountIdentityServiceApplication.class, args);
    }
}
