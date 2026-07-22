package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostingRepository postingRepository;
    private final RegionNameResolver regionNameResolver;

    @Transactional
    public BookmarkResponse addBookmark(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (!postingRepository.existsById(postingId)) {
            throw new BusinessException(ErrorCode.POSTING_NOT_FOUND);
        }
        if (bookmarkRepository.existsByUserIdAndPostingId(userId, postingId)) {
            throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATE);
        }

        try {
            bookmarkRepository.saveAndFlush(Bookmark.create(userId, postingId));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATE);
        }
        return BookmarkResponse.of(postingId, true);
    }

    @Transactional
    public BookmarkResponse removeBookmark(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        Bookmark bookmark =
                bookmarkRepository
                        .findByUserIdAndPostingId(userId, postingId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND));

        bookmarkRepository.delete(bookmark);
        return BookmarkResponse.of(postingId, false);
    }

    /**
     * 정렬은 항상 북마크한 시각(최신) 순으로 고정한다 — 개인 북마크 목록에 별도 정렬 옵션을 둘 이유가 없고, 클라이언트 정렬을 그대로 JPQL에 흘려보내면 프로퍼티명이
     * 검증되지 않아 깨진 쿼리로 이어질 수 있다. 그래서 sort를 조용히 무시하는 대신, sort가 지정된 요청은 400으로 명시적으로 거부한다(page/size만
     * 받는다).
     */
    @Transactional(readOnly = true)
    public PageResponse<PostingSummaryResponse> getBookmarkedPostings(
            PostingCategory category, String keyword, Pageable pageable) {
        rejectSort(pageable.getSort());
        Long userId = SecurityUtil.getCurrentUserId();
        Pageable unsortedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Posting> postings =
                bookmarkRepository.findBookmarkedPostings(
                        userId, category, keyword, unsortedPageable);

        Map<Long, String> regionNames = regionNameResolver.resolve(postings);

        Page<PostingSummaryResponse> responses =
                postings.map(
                        posting ->
                                PostingSummaryResponse.from(
                                        posting, regionNames.get(posting.getRegionId())));

        return PageResponse.from(responses);
    }

    private void rejectSort(Sort sort) {
        if (sort.isSorted()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
