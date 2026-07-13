package com.gather.gather.domain.region.controller;

import com.gather.gather.domain.region.dto.RegionGroupResponse;
import com.gather.gather.domain.region.dto.RegionResponse;
import com.gather.gather.domain.region.service.RegionService;
import com.gather.gather.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Signup Support", description = "회원가입 보조 조회 API")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private static final String JSON = "application/json";

    private final RegionService regionService;

    @Operation(
            summary = "지역 목록 조회",
            description =
                    "시/도(level = 1), 시/군/구(level = 2), 읍/면/동(level = 4)을 포함한 전체 지역 목록을 평면 리스트로 조회합니다. "
                            + "회원가입 시 activityRegionId로 선택 가능한 값은 시/군/구(level = 2)만 해당합니다. 인증이 필요 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "지역 목록 조회 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": [
                                                            {
                                                              "id": 1,
                                                              "name": "서울특별시",
                                                              "level": 1,
                                                              "code": "6110000",
                                                              "parentId": null,
                                                              "regionGroupId": 1
                                                            },
                                                            {
                                                              "id": 18,
                                                              "name": "강남구",
                                                              "level": 2,
                                                              "code": "3220000",
                                                              "parentId": 1,
                                                              "regionGroupId": null
                                                            }
                                                          ],
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "INTERNAL_SERVER_ERROR",
                                                            "message": "서버 내부 오류가 발생했습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping
    public ApiResponse<List<RegionResponse>> getRegions() {
        return ApiResponse.success(regionService.getRegions());
    }

    @Operation(
            summary = "활동 지역 권역(9버튼) 목록 조회",
            description =
                    "회원가입/필터 화면에서 노출하는 9개 권역 버튼(서울/부산/인천/경기/강원/제주/경상/전라/충청)을 "
                            + "고정 노출 순서로 조회합니다. 경상/전라/충청처럼 여러 시도를 묶은 권역은 1365 행정구역 코드가 없어 "
                            + "서비스 내부 코드(code)가 내려갑니다. 인증이 필요 없습니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "권역 목록 조회 성공",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                                                        {
                                                          "success": true,
                                                          "data": [
                                                            {
                                                              "id": 1,
                                                              "code": "GRP_SEOUL",
                                                              "name": "서울"
                                                            },
                                                            {
                                                              "id": 7,
                                                              "code": "GRP_GYEONGSANG",
                                                              "name": "경상"
                                                            }
                                                          ],
                                                          "error": null
                                                        }
                                                        """))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 내부 오류",
                content =
                        @Content(
                                mediaType = JSON,
                                examples =
                                        @ExampleObject(
                                                name = "INTERNAL_SERVER_ERROR",
                                                value =
                                                        """
                                                        {
                                                          "success": false,
                                                          "data": null,
                                                          "error": {
                                                            "code": "INTERNAL_SERVER_ERROR",
                                                            "message": "서버 내부 오류가 발생했습니다."
                                                          }
                                                        }
                                                        """)))
    })
    @GetMapping("/groups")
    public ApiResponse<List<RegionGroupResponse>> getRegionGroups() {
        return ApiResponse.success(regionService.getRegionGroups());
    }
}
