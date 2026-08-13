package com.gather.gather.domain.posting.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.posting.service.PostingSyncResult;
import com.gather.gather.domain.posting.service.VmsPostingSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VmsPostingSyncController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "vms.crawl.scheduler-enabled=true")
class VmsPostingSyncControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private VmsPostingSyncService vmsPostingSyncService;

    @Test
    @DisplayName("POST /api/v1/admin/postings/vms-sync returns 200 with the sync result counts")
    void sync_returns200WithResultCounts() throws Exception {
        when(vmsPostingSyncService.syncRecentPostings(isNull(), isNull()))
                .thenReturn(new PostingSyncResult(10, 3, 6, 1, 0));

        mockMvc.perform(post("/api/v1/admin/postings/vms-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scanned").value(10))
                .andExpect(jsonPath("$.data.inserted").value(3))
                .andExpect(jsonPath("$.data.updated").value(6))
                .andExpect(jsonPath("$.data.failed").value(1));
    }

    @Test
    @DisplayName("maxPages/maxDetailLookups 쿼리 파라미터를 그대로 서비스에 전달한다")
    void sync_passesQueryParams_toService() throws Exception {
        when(vmsPostingSyncService.syncRecentPostings(eq(1), eq(5)))
                .thenReturn(new PostingSyncResult(5, 5, 0, 0, 0));

        mockMvc.perform(
                        post("/api/v1/admin/postings/vms-sync")
                                .param("maxPages", "1")
                                .param("maxDetailLookups", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(5));

        verify(vmsPostingSyncService).syncRecentPostings(1, 5);
    }
}
