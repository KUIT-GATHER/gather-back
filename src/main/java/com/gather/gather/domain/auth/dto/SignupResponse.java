package com.gather.gather.domain.auth.dto;

import com.gather.gather.domain.auth.entity.User;

public record SignupResponse(Long id, String email, String nickname) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
