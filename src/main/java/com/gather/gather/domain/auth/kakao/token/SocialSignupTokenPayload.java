package com.gather.gather.domain.auth.kakao.token;

import com.gather.gather.domain.auth.entity.SocialProvider;

public record SocialSignupTokenPayload(SocialProvider provider, String providerUserId) {}
