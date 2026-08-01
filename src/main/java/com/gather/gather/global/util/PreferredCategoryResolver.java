package com.gather.gather.global.util;

import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.posting.entity.PostingCategory;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 로그인 사용자의 선호 카테고리 조회(봉사공고/모임 추천이 공유).
 *
 * <p>인증은 됐으나(userId != null) 실제 User row가 없는 경우(탈퇴 직후, 오래된 토큰 등)는 비로그인과 동일하게 빈 선호 카테고리로 처리하되, 데이터
 * 정합성 문제를 조용히 놓치지 않도록 경고 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreferredCategoryResolver {

    private final UserRepository userRepository;

    public Set<PostingCategory> resolve(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return userRepository
                .findById(userId)
                .map(user -> Set.copyOf(user.getInterestCategories()))
                .orElseGet(
                        () -> {
                            log.warn(
                                    "인증된 userId={}에 해당하는 회원 정보를 찾을 수 없어 선호 카테고리를 빈 값으로 처리합니다.",
                                    userId);
                            return Set.of();
                        });
    }
}
