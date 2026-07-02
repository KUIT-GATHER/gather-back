package com.gather.gather.domain.region.controller;

import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.service.RegionService;
import com.gather.gather.global.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ApiResponse<List<RegionResponse>> getRegions() {
        return ApiResponse.success(regionService.getRegions());
    }
}
