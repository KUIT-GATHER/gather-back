package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.RefreshToken;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Access Token 발급과 Refresh Token 저장. 일반 로그인과 카카오 로그인이 같은 토큰 파이프라인을 쓴다. */
@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public TokenIssueResult issue(User user) {
        String accessToken = tokenProvider.createAccessToken(user);
        String refreshToken = tokenProvider.generateToken();
        String refreshTokenHash = tokenProvider.hashToken(refreshToken);

        refreshTokenRepository.save(
                RefreshToken.create(refreshTokenHash, user, tokenProvider.refreshTokenExpiresAt()));
        return new TokenIssueResult(accessToken, refreshToken);
    }
}
