package com.gather.gather.domain.posting.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.PostingSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PostingSyncController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "posting.sync.manual-endpoint-enabled=true")
class PostingSyncControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PostingSyncService postingSyncService;

    @Test
    @DisplayName("POST /api/v1/postings/sync returns 200 with the sync result counts")
    void sync_returns200WithResultCounts() throws Exception {
        when(postingSyncService.syncRecentPostings())
                .thenReturn(new PostingSyncResult(10, 3, 6, 1));

        mockMvc.perform(post("/api/v1/postings/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scanned").value(10))
                .andExpect(jsonPath("$.data.inserted").value(3))
                .andExpect(jsonPath("$.data.updated").value(6))
                .andExpect(jsonPath("$.data.failed").value(1));
    }
}
