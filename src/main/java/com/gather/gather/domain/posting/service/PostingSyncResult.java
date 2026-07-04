package com.gather.gather.domain.posting.service;

public record PostingSyncResult(int scanned, int inserted, int updated, int failed) {}
