package com.gather.gather.domain.posting.service;

import static org.mockito.Mockito.verify;

import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingParticipationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostingWithdrawalListenerTest {

    private static final Long USER_ID = 1L;

    @Mock private BookmarkRepository bookmarkRepository;

    @Mock private PostingParticipationRepository postingParticipationRepository;

    @Test
    @DisplayName("탈퇴 이벤트를 받으면 북마크와 참여 신청을 전량 정리한다")
    void cleanUp_deletesBookmarksAndParticipations() {
        PostingWithdrawalListener listener =
                new PostingWithdrawalListener(bookmarkRepository, postingParticipationRepository);

        listener.cleanUp(new UserWithdrawnEvent(USER_ID));

        verify(bookmarkRepository).deleteAllByUserId(USER_ID);
        verify(postingParticipationRepository).deleteAllByUserId(USER_ID);
    }
}
