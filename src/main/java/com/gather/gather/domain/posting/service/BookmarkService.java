package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.dto.PostingSummaryResponse;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.entity.Posting;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.domain.region.entity.Region;
import com.gather.gather.domain.region.repository.RegionRepository;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostingRepository postingRepository;
    private final RegionRepository regionRepository;

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

    /** 정렬은 항상 북마크한 시각(최신) 순으로 고정한다 — 클라이언트가 보낸 sort는 반영하지 않는다(페이지/사이즈만 사용). */
    @Transactional(readOnly = true)
    public PageResponse<PostingSummaryResponse> getBookmarkedPostings(
            PostingCategory category, String keyword, Pageable pageable) {
        Long userId = SecurityUtil.getCurrentUserId();
        Pageable unsortedPageable =
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Posting> postings =
                bookmarkRepository.findBookmarkedPostings(
                        userId, category, keyword, unsortedPageable);

        Map<Long, String> regionNames = findRegionNames(postings);

        Page<PostingSummaryResponse> responses =
                postings.map(
                        posting ->
                                PostingSummaryResponse.from(
                                        posting, regionNames.get(posting.getRegionId())));

        return PageResponse.from(responses);
    }

    private Map<Long, String> findRegionNames(Page<Posting> postings) {
        Set<Long> regionIds =
                postings.getContent().stream()
                        .map(Posting::getRegionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        return regionRepository.findAllById(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, Region::getName));
    }
}
