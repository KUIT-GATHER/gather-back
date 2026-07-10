package com.gather.gather.domain.region.dto;

import com.gather.gather.domain.region.entity.Region;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "활동 지역 응답")
public record RegionResponse(
        @Schema(description = "지역 ID", example = "1") Long id,
        @Schema(description = "지역 표시명", example = "서울특별시") String name,
        @Schema(description = "지역 단계 (1=시도, 2=시군구)", example = "1") Integer level,
        @Schema(description = "1365 행정구역 코드와 매핑되는 지역 식별 코드", example = "6110000") String code,
        @Schema(description = "상위 지역 ID (최상위 지역이면 null)", example = "null") Long parentId,
        @Schema(
                        description = "소속 권역(9버튼) ID. 시도(level=1)에만 존재하며, 시군구(level=2)는 항상 null.",
                        example = "1")
                Long regionGroupId) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getId(),
                region.getName(),
                region.getLevel(),
                region.getCode(),
                region.getParent() != null ? region.getParent().getId() : null,
                region.getRegionGroup() != null ? region.getRegionGroup().getId() : null);
    }
}
