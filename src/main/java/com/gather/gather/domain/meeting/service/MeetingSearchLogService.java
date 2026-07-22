package com.gather.gather.domain.meeting.service;

import com.gather.gather.domain.meeting.entity.MeetingSearchLog;
import com.gather.gather.domain.meeting.repository.MeetingSearchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingSearchLogService {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private final MeetingSearchLogRepository meetingSearchLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String keyword) {
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            log.warn("모임 검색어 로깅 생략. keyword 길이={} (최대 {})", keyword.length(), MAX_KEYWORD_LENGTH);
            return;
        }

        try {
            meetingSearchLogRepository.save(MeetingSearchLog.builder().keyword(keyword).build());
        } catch (RuntimeException e) {
            log.warn("모임 검색어 로깅 실패. keyword 길이={}", keyword.length(), e);
        }
    }
}
