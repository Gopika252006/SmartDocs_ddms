package com.smartdocs.repository;

import com.smartdocs.entity.OTPVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OTPVerificationRepository extends JpaRepository<OTPVerification, Long> {

    Optional<OTPVerification> findByEmail(String email);

    Optional<OTPVerification> findByEmailAndPurpose(String email, String purpose);

    void deleteByEmail(String email);
}
