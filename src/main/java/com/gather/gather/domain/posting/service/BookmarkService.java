package com.gather.gather.domain.posting.service;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.entity.Bookmark;
import com.gather.gather.domain.posting.repository.BookmarkRepository;
import com.gather.gather.domain.posting.repository.PostingRepository;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import com.gather.gather.global.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostingRepository postingRepository;

    @Transactional
    public BookmarkResponse addBookmark(Long postingId) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (!postingRepository.existsById(postingId)) {
            throw new BusinessException(ErrorCode.POSTING_NOT_FOUND);
        }
        if (bookmarkRepository.existsByUserIdAndPostingId(userId, postingId)) {
            throw new BusinessException(ErrorCode.BOOKMARK_DUPLICATE);
        }

        bookmarkRepository.save(Bookmark.create(userId, postingId));
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
}
