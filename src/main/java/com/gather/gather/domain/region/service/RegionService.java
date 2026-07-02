package com.gather.gather.domain.region.service;

import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.repository.RegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;

    @Transactional(readOnly = true)
    public List<RegionResponse> getRegions() {
        return regionRepository.findAll().stream().map(RegionResponse::from).toList();
    }
}
