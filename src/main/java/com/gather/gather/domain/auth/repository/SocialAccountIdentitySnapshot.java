package com.gather.gather.domain.auth.repository;

import com.gather.gather.domain.auth.entity.SocialAccountLinkStatus;
import com.gather.gather.domain.auth.entity.SocialProvider;

public record SocialAccountIdentitySnapshot(
        Long id,
        SocialProvider provider,
        String providerUserKey,
        Integer providerUserKeyVersion,
        SocialAccountLinkStatus linkStatus,
        Long generation) {}
