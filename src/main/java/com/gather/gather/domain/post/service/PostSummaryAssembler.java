package com.gather.gather.domain.post.service;

import com.gather.gather.domain.post.dto.PostSummaryResponse;
import com.gather.gather.domain.post.entity.Post;
import com.gather.gather.domain.post.repository.PostLikeRepository;
import com.gather.gather.global.common.PageResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * 게시글 목록 Page를 이미지·좋아요 정보로 채워 요약 응답 페이지로 조립한다. 게시판 목록과 "나의 활동" 목록에서 공통으로 쓴다. 이미지/좋아요는 N+1을 피해
 * postId 배치로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class PostSummaryAssembler {

    private final PostImageService postImageService;
    private final PostLikeRepository postLikeRepository;

    public PageResponse<PostSummaryResponse> assemble(Page<Post> page, Long viewerUserId) {
        List<Post> posts = page.getContent();
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, List<String>> imagesByPost = postImageService.resolveUrlsByPostIds(postIds);
        Set<Long> likedPostIds =
                postIds.isEmpty()
                        ? Set.of()
                        : new HashSet<>(
                                postLikeRepository.findLikedPostIds(viewerUserId, postIds));

        List<PostSummaryResponse> content =
                posts.stream()
                        .map(
                                post ->
                                        PostSummaryResponse.from(
                                                post,
                                                imagesByPost.getOrDefault(post.getId(), List.of()),
                                                likedPostIds.contains(post.getId())))
                        .toList();

        return new PageResponse<>(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
