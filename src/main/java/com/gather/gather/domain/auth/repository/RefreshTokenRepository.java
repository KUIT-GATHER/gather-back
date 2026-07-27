package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 탈퇴는 계정 자체가 끝나므로 로그아웃과 달리 revoke가 아니라 전량 삭제한다.
    void deleteByUser(User user);
}
