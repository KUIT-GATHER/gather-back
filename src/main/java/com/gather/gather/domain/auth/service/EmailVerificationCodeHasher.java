package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.config.EmailVerificationHmacProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 코드를 HMAC-SHA256으로 저장하기 위한 컴포넌트.
 *
 * <p>6자리 코드는 경우의 수가 100만뿐이라 키 없는 해시는 DB 덤프만으로 전수 대입된다. 애플리케이션 전용 시크릿을 키로 사용해 DB 유출 단독으로는 원본 코드를 복원할
 * 수 없게 한다. 메시지에 verificationId를 묶어 다른 발송 세대의 해시를 재사용할 수 없게 한다.
 */
@Component
public class EmailVerificationCodeHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String MESSAGE_PREFIX = "EMAIL_VERIFICATION:v1:";
    private static final String MESSAGE_DELIMITER = ":";
    private static final Pattern CODE_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private final SecretKeySpec secretKey;

    public EmailVerificationCodeHasher(EmailVerificationHmacProperties properties) {
        this.secretKey =
                new SecretKeySpec(Base64.getDecoder().decode(properties.secret()), HMAC_ALGORITHM);
    }

    public String hash(String verificationId, String rawCode) {
        return HexFormat.of().formatHex(digest(verificationId, rawCode));
    }

    /** 저장된 해시가 없거나 형식이 깨졌으면 인증을 통과시키지 않고 실패로 판정한다. */
    public boolean verify(String verificationId, String rawCode, String storedCodeHash) {
        if (!isStoredCodeHashFormatValid(storedCodeHash) || rawCode == null || rawCode.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                digest(verificationId, rawCode), HexFormat.of().parseHex(storedCodeHash));
    }

    public static boolean isStoredCodeHashFormatValid(String storedCodeHash) {
        return storedCodeHash != null && CODE_HASH_PATTERN.matcher(storedCodeHash).matches();
    }

    private byte[] digest(String verificationId, String rawCode) {
        if (verificationId == null || verificationId.isBlank()) {
            throw new IllegalArgumentException("인증 식별자는 필수입니다.");
        }
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("인증 코드는 필수입니다.");
        }
        String message = MESSAGE_PREFIX + verificationId + MESSAGE_DELIMITER + rawCode;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256을 사용할 수 없습니다.", exception);
        }
    }
}
