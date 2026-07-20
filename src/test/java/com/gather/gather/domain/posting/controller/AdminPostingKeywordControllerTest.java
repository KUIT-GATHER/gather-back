package com.gather.gather.domain.posting.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.service.PostingKeywordRecommendationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminPostingKeywordController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminPostingKeywordControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingKeywordRecommendationService postingKeywordRecommendationService;

    @Test
    @DisplayName(
            "POST /api/v1/admin/postings/keywords/aggregate returns 200 with the aggregated count")
    void aggregateKeywords_returns200WithCount() throws Exception {
        when(postingKeywordRecommendationService.aggregate()).thenReturn(10);

        mockMvc.perform(post("/api/v1/admin/postings/keywords/aggregate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(10));
    }
}
