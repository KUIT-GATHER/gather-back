package com.gather.gather.domain.region.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.service.RegionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private RegionService regionService;

    @Test
    @DisplayName("GET /api/v1/regions returns 200 with flat list")
    void getRegions_returns200WithFlatList() throws Exception {
        when(regionService.getRegions())
                .thenReturn(
                        List.of(
                                new RegionResponse(1L, "서울특별시", 1, "11", null),
                                new RegionResponse(2L, "강남구", 3, "11680", 1L)));

        mockMvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].parentId").value(nullValue()))
                .andExpect(jsonPath("$.data[1].parentId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/regions returns 200 with empty list when no regions")
    void getRegions_returns200WithEmptyList_whenNoRegions() throws Exception {
        when(regionService.getRegions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
