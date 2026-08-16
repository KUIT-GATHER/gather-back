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

    public Optional<AccountLoginType> resolve(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }

        boolean hasEmail = user.getEmail() != null;
        boolean hasPassword = user.getPassword() != null;
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
