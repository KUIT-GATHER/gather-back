package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.UserRole;

/** Access Token(JWT) 파싱 결과. */
public record AccessTokenPayload(Long userId, UserRole role) {}
