package com.gather.gather.domain.auth.service;

/** Outcome counts for one pending Kakao unlink cleanup run. */
public record UnlinkRetrySummary(
        int resolvedCount,
        int noLinkedAccountCount,
        int retryPendingCount,
        int failedCount,
        int forcedDeletionCount) {

    public int attemptedCount() {
        return resolvedCount
                + noLinkedAccountCount
                + retryPendingCount
                + failedCount
                + forcedDeletionCount;
    }
}
