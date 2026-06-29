package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.SignupRequest;
import com.gather.gather.domain.auth.dto.SignupResponse;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.auth.repository.UserRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    // BCrypt는 72바이트를 넘는 입력을 잘라내므로, 입력 글자가 무시되지 않도록 상한 설정
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    private static final int MIN_NICKNAME_LENGTH = 2;
    private static final int MAX_NICKNAME_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {

        String email = normalizeEmail(request.email());
        String password = request.password();
        validatePassword(password);

        String nickname = request.nickname().trim();
        validateNickname(nickname);

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(password);
        User user = User.create(email, encodedPassword, nickname);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        return SignupResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
    }

    private void validateNickname(String nickname) {
        if (nickname.length() < MIN_NICKNAME_LENGTH || nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_NICKNAME);
        }
    }
}
