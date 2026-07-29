package com.gather.gather.domain.mypage.dto;

import com.gather.gather.domain.auth.entity.User;
import com.gather.gather.domain.region.dto.RegionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "마이페이지 홈 응답")
public record MyPageHomeResponse(
        @Schema(description = "닉네임", example = "길동") String nickname,
        @Schema(description = "프로필 이미지 공개 URL. 등록된 이미지가 없으면 null입니다.", nullable = true)
                String profileImageUrl,
        @Schema(description = "생년월일", example = "2002-03-15") LocalDate birthDate,
        @Schema(description = "활동 지역") RegionResponse activityRegion,
        @Schema(description = "봉사공고 또는 관심모임 북마크 보유 여부", example = "true") boolean hasBookmark) {

    public static MyPageHomeResponse of(User user, String profileImageUrl, boolean hasBookmark) {
        return new MyPageHomeResponse(
                user.getNickname(),
                profileImageUrl,
                user.getBirthDate(),
                user.getActivityRegion() == null
                        ? null
                        : RegionResponse.from(user.getActivityRegion()),
                hasBookmark);
    }
}
