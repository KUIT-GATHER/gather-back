package com.gather.gather.domain.auth.service;

import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 토큰 생성과 hash 변환.
 *
 * <p>DB에는 hash만 저장하므로, 형식이 어긋난 토큰은 조회 전에 거부해 불필요한 DB 접근을 만들지 않는다.
 */
@Component
public class PasswordResetTokenCodec {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int ENCODED_TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String validateAndHash(String token) {
        validateToken(token);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private void validateToken(String token) {
        if (token == null
                || token.length() != ENCODED_TOKEN_LENGTH
                || !TOKEN_PATTERN.matcher(token).matches()) {
            throw invalid();
        }

        try {
            if (Base64.getUrlDecoder().decode(token).length != TOKEN_BYTE_LENGTH) {
                throw invalid();
            }
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}
