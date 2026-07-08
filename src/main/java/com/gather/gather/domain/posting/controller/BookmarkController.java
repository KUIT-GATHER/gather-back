package com.gather.gather.domain.posting.controller;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.service.BookmarkService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmark", description = "봉사공고 북마크 API")
@RestController
@RequestMapping("/api/v1/postings/{postingId}/bookmark")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @Operation(summary = "봉사공고 북마크 추가", description = "로그인한 사용자가 봉사공고를 북마크에 추가합니다.")
    @PostMapping
    public ApiResponse<BookmarkResponse> addBookmark(@PathVariable Long postingId) {
        return ApiResponse.success(bookmarkService.addBookmark(postingId));
    }

    @Operation(summary = "봉사공고 북마크 삭제", description = "로그인한 사용자가 봉사공고의 북마크를 삭제합니다.")
    @DeleteMapping
    public ApiResponse<BookmarkResponse> removeBookmark(@PathVariable Long postingId) {
        return ApiResponse.success(bookmarkService.removeBookmark(postingId));
    }
}
