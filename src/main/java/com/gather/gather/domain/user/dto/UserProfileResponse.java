package com.gather.gather.domain.user.dto;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.posting.entity.PostingCategory;
import com.gather.gather.domain.region.dto.RegionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "마이페이지 프로필 조회 응답")
public record UserProfileResponse(
        @Schema(description = "사용자 ID", example = "1") Long id,
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "닉네임", example = "길동") String nickname,
        @Schema(description = "소개글", example = "함께 봉사하고 싶습니다.") String introduction,
        @Schema(description = "생년월일", example = "2002-03-15") LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") Gender gender,
        @Schema(description = "활동 지역") RegionResponse activityRegion,
        @Schema(description = "관심 카테고리 목록", example = "[\"WELFARE\", \"EDUCATION\"]")
                List<PostingCategory> interestCategories) {

    public static UserProfileResponse of(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getNickname(),
                user.getIntroduction(),
                user.getBirthDate(),
                user.getGender(),
                RegionResponse.from(user.getActivityRegion()),
                user.getInterestCategories());
    }
}
