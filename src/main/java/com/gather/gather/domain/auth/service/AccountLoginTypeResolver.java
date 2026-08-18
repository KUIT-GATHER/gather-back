package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.AccountLoginType;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.entity.UserStatus;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountLoginTypeResolver {

    private final SocialAccountRepository socialAccountRepository;

    /** 계정 복구·비밀번호 재설정처럼 ACTIVE 계정만 대상으로 하는 흐름에서 사용한다. */
    public Optional<AccountLoginType> resolve(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }

        return resolveCredentialType(user);
    }

    /**
     * 계정 상태와 무관하게 credential 구조만 판정한다.
     *
     * <p>마이페이지 loginType은 "비밀번호 변경이 가능한 계정 유형인가"를 뜻하므로, SUSPENDED처럼 보호 API 접근이 허용된 상태에서도 정상
     * credential을 그대로 분류해야 한다.
     */
    public Optional<AccountLoginType> resolveCredentialType(User user) {
        boolean hasEmail = user.getEmail() != null;
        boolean hasPassword = user.getPassword() != null;
        // 이메일 로그인 credential은 둘이 함께 존재해야 하며 부분 데이터는 소셜 계정으로 추정하지 않는다.
        if (hasEmail && hasPassword) {
            return Optional.of(AccountLoginType.EMAIL);
        }
        if (hasEmail || hasPassword) {
            return Optional.empty();
        }

        boolean hasLinkedKakao =
                socialAccountRepository.existsByUserIdAndProviderAndLinkStatus(
                        user.getId(), SocialProvider.KAKAO, SocialAccountLinkStatus.LINKED);
        return hasLinkedKakao ? Optional.of(AccountLoginType.KAKAO) : Optional.empty();
    }
}
