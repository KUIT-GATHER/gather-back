package com.gather.gather.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.SocialAccount;
import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifierHasher;
import com.gather.gather.domain.auth.service.SocialAccountIdentityService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SocialAccountLegacyMigrationIntegrationTest {

    private static final String PROVIDER_USER_ID = "legacy-kakao-20260729";

    @Autowired private EntityManager entityManager;
    @Autowired private UserRepository userRepository;
    @Autowired private SocialAccountRepository socialAccountRepository;
    @Autowired private SocialAccountIdentityService identityService;
    @Autowired private RejoinBlockIdentifierHasher identifierHasher;

    @Test
    @DisplayName("기존 평문 SocialAccount row는 로그인 조회에서 HMAC·암호문·생명주기로 backfill된다")
    void findKakaoAccount_existingLegacyRow_backfillsNewColumns() {
        User user =
                userRepository.saveAndFlush(
                        User.createSocial(
                                "홍길동",
                                LocalDate.of(2002, 3, 15),
                                Gender.MALE,
                                "01092000001",
                                "legacysocialaccount",
                                null,
                                true,
                                true,
                                false,
                                null,
                                List.of(PostingCategory.WELFARE)));
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 10, 0);
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO social_account (
                            user_id, provider, provider_user_id, created_at, updated_at
                        ) VALUES (
                            :userId, 'KAKAO', :providerUserId, :createdAt, :createdAt
                        )
                        """)
                .setParameter("userId", user.getId())
                .setParameter("providerUserId", PROVIDER_USER_ID)
                .setParameter("createdAt", createdAt)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        RejoinBlockIdentifier identifier = identifierHasher.hashKakao(PROVIDER_USER_ID);

        identityService.findKakaoAccount(PROVIDER_USER_ID, identifier).orElseThrow();
        entityManager.flush();
        entityManager.clear();

        SocialAccount migrated =
                socialAccountRepository
                        .findByProviderAndProviderUserKey(SocialProvider.KAKAO, identifier.hash())
                        .orElseThrow();
        assertThat(migrated.getProviderUserKeyVersion()).isEqualTo(identifier.keyVersion());
        assertThat(migrated.getProviderUserIdCiphertext()).isNotBlank();
        assertThat(migrated.getEncryptionKeyVersion()).isEqualTo(1);
        assertThat(migrated.getLinkStatus()).isEqualTo(SocialAccountLinkStatus.LINKED);
        assertThat(migrated.getGeneration()).isEqualTo(1L);
        assertThat(migrated.getConnectedAt()).isEqualTo(createdAt);
    }
}
