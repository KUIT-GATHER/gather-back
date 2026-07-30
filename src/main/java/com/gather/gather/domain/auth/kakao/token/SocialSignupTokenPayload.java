package com.gather.gather.domain.auth.kakao.token;

import com.gather.gather.domain.auth.entity.EncryptedProviderUserId;
import com.gather.gather.domain.auth.entity.SocialProvider;
import com.gather.gather.domain.auth.service.RejoinBlockIdentifier;

public record SocialSignupTokenPayload(
        SocialProvider provider,
        RejoinBlockIdentifier identifier,
        EncryptedProviderUserId encryptedProviderUserId) {}
