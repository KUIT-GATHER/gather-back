package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴 시 posting 도메인(북마크·참여 신청) 데이터를 정리한다. 탈퇴 트랜잭션과 같은 트랜잭션에서 동작해 실패하면 탈퇴 자체가 롤백된다. */
@Component
@RequiredArgsConstructor
public class PostingWithdrawalListener {

    private final BookmarkRepository bookmarkRepository;
    private final PostingParticipationRepository postingParticipationRepository;

    @EventListener
    @Transactional
    public void cleanUp(UserWithdrawnEvent event) {
        bookmarkRepository.deleteAllByUserId(event.userId());
        postingParticipationRepository.deleteAllByUserId(event.userId());
    }
}
