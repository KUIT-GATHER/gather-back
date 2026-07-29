package com.gather.gather.domain.auth.kakao.token;

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

@Component
public class SocialSignupTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int ENCODED_TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public String hashToken(String token) {
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
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }

        try {
            if (Base64.getUrlDecoder().decode(token).length != TOKEN_BYTE_LENGTH) {
                throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID);
        }
    }
}
