package com.gather.gather.domain.post.service;

import com.gather.gather.domain.auth.event.UserWithdrawnEvent;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 회원 탈퇴 시 작성한 게시글을 소프트 삭제한다. */
@Component
@RequiredArgsConstructor
public class PostWithdrawalListener {

    private final PostRepository postRepository;

    @EventListener
    @Transactional
    public void cleanUp(UserWithdrawnEvent event) {
        postRepository.findAllByUserIdAndDeletedAtIsNull(event.userId()).forEach(Post::delete);
    }
}
