package com.gather.gather.domain.auth.service;

import com.gather.gather.domain.auth.entity.AccountRejoinBlockIdentifierType;

public record RejoinBlockIdentifier(
        AccountRejoinBlockIdentifierType type, String hash, int keyVersion) {}
