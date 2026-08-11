package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.PhoneVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    Optional<PhoneVerification> findByVerificationId(String verificationId);

    Optional<PhoneVerification> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PhoneVerification p where p.verificationId = :verificationId")
    Optional<PhoneVerification> findByVerificationIdForUpdate(String verificationId);
}
