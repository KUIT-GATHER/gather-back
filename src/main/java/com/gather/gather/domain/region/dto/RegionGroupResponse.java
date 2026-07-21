package com.gather.gather.domain.region.dto;

import com.gather.gather.domain.region.entity.RegionGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "활동 지역 권역(9버튼) 응답")
public record RegionGroupResponse(
        @Schema(description = "권역 ID", example = "7") Long id,
        @Schema(description = "권역 코드. 1365 행정구역 코드가 아닌 서비스 내부 코드입니다.", example = "GRP_GYEONGSANG")
                String code,
        @Schema(description = "권역 표시명", example = "경상") String name) {

    public static RegionGroupResponse from(RegionGroup regionGroup) {
        return new RegionGroupResponse(
                regionGroup.getId(), regionGroup.getCode(), regionGroup.getName());
    }
}
