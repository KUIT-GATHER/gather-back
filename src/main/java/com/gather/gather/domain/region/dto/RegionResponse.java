package com.gather.gather.domain.region.dto;

import com.gather.gather.domain.region.entity.Region;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "활동 지역 응답")
public record RegionResponse(
        @Schema(description = "지역 ID", example = "1") Long id,
        @Schema(description = "지역 표시명", example = "서울") String name,
        @Schema(description = "지역 단계 (1=도, 2=시, 3=구, 4=동)", example = "1") Integer level,
        @Schema(
                        description =
                                "지역 식별 코드입니다. 단일 시도는 1365 행정구역 코드와 매핑될 수 있으며, "
                                        + "경상/전라/충청 같은 광역권은 서비스 내부 코드가 사용될 수 있습니다.",
                        example = "11")
                String code,
        @Schema(description = "상위 지역 ID (최상위 지역이면 null)", example = "null") Long parentId) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(
                region.getId(),
                region.getName(),
                region.getLevel(),
                region.getCode(),
                region.getParent() != null ? region.getParent().getId() : null);
    }
}
