package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // 번호를 쥔 계정의 상태에 따라 안내가 달라져야 해서 존재 여부만으로는 부족하다(탈퇴자면 재가입 유예 안내).
    Optional<User> findByPhoneNumber(String phoneNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    /**
     * 유예가 끝났는데 아직 익명화되지 않은 탈퇴 계정. 완료 여부는 별도 컬럼 없이 전화번호 접두사로 판정한다 — 세 컬럼을 한 UPDATE로 바꾸므로 부분 완료 상태가
     * 생기지 않는다. {@code withdrawnAt}이 없는 과거 탈퇴 계정은 유예 시작 시점을 알 수 없어 대상에서 빠진다.
     */
    @Query(
            "select u from User u"
                    + " where u.status = :status"
                    + " and u.withdrawnAt <= :threshold"
                    + " and u.phoneNumber not like 'wd!_%' escape '!'")
    List<User> findAnonymizationTargets(
            UserStatus status, LocalDateTime threshold, Pageable pageable);
}
