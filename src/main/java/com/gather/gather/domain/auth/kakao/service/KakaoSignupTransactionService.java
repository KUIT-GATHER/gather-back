package com.gather.gather.domain.auth.kakao.service;

import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.kakao.token.SocialSignupTokenPayload;
import com.gather.gather.domain.auth.repository.SocialAccountRepository;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.domain.auth.service.SignupValidator;
import com.gather.gather.domain.auth.service.SocialAccountConstraint;
import com.gather.gather.domain.auth.service.SocialAccountConstraintResolver;
import com.gather.gather.domain.auth.service.SocialAccountProviderIdCipher;
import com.gather.gather.domain.auth.service.SocialAccountProviderKeyConflictException;
import com.gather.gather.domain.auth.service.TokenIssueResult;
import com.gather.gather.domain.auth.service.TokenIssuer;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoSignupTransactionService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SignupValidator signupValidator;
    private final TokenIssuer tokenIssuer;
    private final SocialAccountProviderIdCipher providerIdCipher;
    private final SocialAccountConstraintResolver constraintResolver;

    @Transactional
    public TokenIssueResult createAccount(
            User user, SocialSignupTokenPayload payload, String phoneNumber, String nickname) {
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw signupValidator.resolveDuplicateException(exception, null, phoneNumber, nickname);
        }

        String legacyProviderUserId = providerIdCipher.decrypt(payload.encryptedProviderUserId());
        try {
            socialAccountRepository.saveAndFlush(
                    SocialAccount.createLinked(
                            savedUser,
                            payload.provider(),
                            legacyProviderUserId,
                            payload.identifier().hash(),
                            payload.identifier().keyVersion(),
                            payload.encryptedProviderUserId(),
                            LocalDateTime.now()));
        } catch (DataIntegrityViolationException exception) {
            SocialAccountConstraint constraint = constraintResolver.resolve(exception);
            if (constraint == SocialAccountConstraint.PROVIDER_USER_KEY
                    || constraint == SocialAccountConstraint.LEGACY_PROVIDER_USER_ID) {
                throw new SocialAccountProviderKeyConflictException(exception);
            }
            throw exception;
        }

        return tokenIssuer.issue(savedUser);
    }
}
