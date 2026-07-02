package com.gather.gather.domain.region.dto;

import com.gather.gather.domain.region.entity.Region;

public record RegionResponse(Long id, String name, Integer level, String code, Long parentId) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getId(),
                region.getName(),
                region.getLevel(),
                region.getCode(),
                region.getParent() != null ? region.getParent().getId() : null);
    }
}
