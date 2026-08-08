package com.gather.gather.domain.posting.controller;

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
        when(vmsPostingSyncService.syncRecentPostings())
                .thenReturn(new PostingSyncResult(10, 3, 6, 1, 0));

        mockMvc.perform(post("/api/v1/admin/postings/vms-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scanned").value(10))
                .andExpect(jsonPath("$.data.inserted").value(3))
                .andExpect(jsonPath("$.data.updated").value(6))
                .andExpect(jsonPath("$.data.failed").value(1));
    }
}
