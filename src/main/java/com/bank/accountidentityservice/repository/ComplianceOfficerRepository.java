package com.bank.accountidentityservice.repository;

import com.bank.accountidentityservice.entity.ComplianceOfficer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplianceOfficerRepository extends JpaRepository<ComplianceOfficer, UUID> {

    Optional<ComplianceOfficer> findByEmail(String email);

    boolean existsByEmail(String email);

    List<ComplianceOfficer> findByStatus(ComplianceOfficer.OfficerStatus status);

    // Lookup by the business key (assigned after approval)
    Optional<ComplianceOfficer> findByOfficerId(String officerId);

    long countByStatus(ComplianceOfficer.OfficerStatus status);
}
