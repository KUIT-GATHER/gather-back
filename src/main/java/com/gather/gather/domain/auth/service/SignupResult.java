package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.dto.SignupResponse;

public record SignupResult(SignupResponse response, String refreshToken) {}
