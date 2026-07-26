package com.gather.gather.domain.post.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.repository.PostRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostWithdrawalListenerTest {

    private static final Long USER_ID = 1L;

    @Mock private PostRepository postRepository;

    @Test
    @DisplayName("탈퇴 이벤트를 받으면 작성한 게시글을 전부 소프트 삭제한다")
    void cleanUp_softDeletesAllPostsByUser() {
        Post post1 = mock(Post.class);
        Post post2 = mock(Post.class);
        when(postRepository.findAllByUserIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(List.of(post1, post2));
        PostWithdrawalListener listener = new PostWithdrawalListener(postRepository);

        listener.cleanUp(new UserWithdrawnEvent(USER_ID));

        verify(post1).delete();
        verify(post2).delete();
    }
}
