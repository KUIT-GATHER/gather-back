package com.gather.gather.domain.meeting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.meeting.dto.PostingMeetingResponse;
import com.gather.gather.domain.meeting.enums.MeetingStatus;
import com.gather.gather.domain.meeting.service.MeetingService;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.global.common.PageResponse;
import com.gather.gather.global.exception.BusinessException;
import com.gather.gather.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostingMeetingController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostingMeetingControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MeetingService meetingService;

    @Test
    @DisplayName("공고 기반 모임 목록을 조회하면 200과 모임 목록을 반환한다")
    void getMeetingsByPosting_returns200WithMeetings() throws Exception {
        PostingMeetingResponse response =
                new PostingMeetingResponse(
                        12L,
                        "한강공원 플로깅팀",
                        Set.of(PostingCategory.ENVIRONMENT),
                        12,
                        20,
                        3L,
                        "강남구",
                        MeetingStatus.RECRUITING,
                        true,
                        false);

        when(meetingService.getMeetingsByPosting(eq(10L), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(response), 1, 1, 0, 10));

        mockMvc.perform(get("/api/v1/postings/10/meetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].meetingId").value(12))
                .andExpect(jsonPath("$.data.content[0].name").value("한강공원 플로깅팀"))
                .andExpect(jsonPath("$.data.content[0].categories").isArray())
                .andExpect(jsonPath("$.data.content[0].categories[0]").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.content[0].currentMemberCount").value(12))
                .andExpect(jsonPath("$.data.content[0].maxMember").value(20))
                .andExpect(jsonPath("$.data.content[0].regionId").value(3))
                .andExpect(jsonPath("$.data.content[0].regionName").value("강남구"))
                .andExpect(jsonPath("$.data.content[0].status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.content[0].member").value(true))
                .andExpect(jsonPath("$.data.content[0].host").value(false))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    @Test
    @DisplayName("연결된 모임이 없으면 200과 빈 목록을 반환한다")
    void getMeetingsByPosting_returns200WithEmptyContent() throws Exception {
        when(meetingService.getMeetingsByPosting(eq(10L), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 0, 10));

        mockMvc.perform(get("/api/v1/postings/10/meetings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 공고이면 404를 반환한다")
    void getMeetingsByPosting_returns404WhenPostingDoesNotExist() throws Exception {
        when(meetingService.getMeetingsByPosting(eq(999L), any(Pageable.class)))
                .thenThrow(new BusinessException(ErrorCode.POSTING_NOT_FOUND));

        mockMvc.perform(get("/api/v1/postings/999/meetings"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POSTING_NOT_FOUND"));
    }

    @Test
    @DisplayName("페이지 번호와 크기를 서비스에 전달한다")
    void getMeetingsByPosting_bindsPageable() throws Exception {
        when(meetingService.getMeetingsByPosting(eq(10L), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 1, 5));

        mockMvc.perform(get("/api/v1/postings/10/meetings").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        verify(meetingService).getMeetingsByPosting(eq(10L), captor.capture());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }
}
