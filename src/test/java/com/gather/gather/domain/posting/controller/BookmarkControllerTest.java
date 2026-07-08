package com.gather.gather.domain.posting.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.dto.BookmarkResponse;
import com.gather.gather.domain.posting.service.BookmarkService;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookmarkController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookmarkControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BookmarkService bookmarkService;

    @Test
    @DisplayName("POST /api/v1/postings/{id}/bookmark returns 200 with bookmarked=true")
    void addBookmark_returns200WithBookmarkedTrue() throws Exception {
        when(bookmarkService.addBookmark(1L)).thenReturn(BookmarkResponse.of(1L, true));

        mockMvc.perform(post("/api/v1/postings/1/bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postingId").value(1))
                .andExpect(jsonPath("$.data.bookmarked").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/postings/{id}/bookmark returns 200 with bookmarked=false")
    void removeBookmark_returns200WithBookmarkedFalse() throws Exception {
        when(bookmarkService.removeBookmark(1L)).thenReturn(BookmarkResponse.of(1L, false));

        mockMvc.perform(delete("/api/v1/postings/1/bookmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postingId").value(1))
                .andExpect(jsonPath("$.data.bookmarked").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/bookmark returns 404 when the posting does not exist")
    void addBookmark_returns404_whenPostingMissing() throws Exception {
        when(bookmarkService.addBookmark(999L))
                .thenThrow(new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        mockMvc.perform(post("/api/v1/postings/999/bookmark"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/bookmark returns 409 when already bookmarked")
    void addBookmark_returns409_whenDuplicate() throws Exception {
        when(bookmarkService.addBookmark(1L))
                .thenThrow(new BusinessException(ErrorCode.BOOKMARK_DUPLICATE));

        mockMvc.perform(post("/api/v1/postings/1/bookmark"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BOOKMARK_DUPLICATE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/postings/{id}/bookmark returns 404 when no bookmark exists")
    void removeBookmark_returns404_whenMissing() throws Exception {
        when(bookmarkService.removeBookmark(1L))
                .thenThrow(new BusinessException(ErrorCode.BOOKMARK_NOT_FOUND));

        mockMvc.perform(delete("/api/v1/postings/1/bookmark"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BOOKMARK_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/postings/{id}/bookmark returns 400 when postingId is not numeric")
    void addBookmark_returns400_whenPostingIdNotNumeric() throws Exception {
        mockMvc.perform(post("/api/v1/postings/abc/bookmark"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
