package com.gather.gather.domain.user.dto;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "마이페이지 프로필 수정 요청. 회원가입과 동일한 필드 집합이며 이메일·전화번호·비밀번호는 포함하지 않는다.")
public record UserProfileUpdateRequest(
        @Schema(description = "이름. 완성형 한글 2~10자 또는 영문 2~20자만 허용합니다.", example = "홍길동")
                @NotBlank
                @Size(min = 2, max = 20)
                String name,
        @Schema(description = "닉네임. 완성형 한글 2~10자 또는 영문 2~20자만 허용합니다.", example = "길동")
                @NotBlank
                @Size(min = 2, max = 20)
                String nickname,
        @Schema(description = "소개글. 최대 50자입니다.", example = "함께 봉사하고 싶습니다.") @Size(max = 50)
                String introduction,
        @Schema(description = "생년월일. 미래 날짜는 허용하지 않습니다.", example = "2002-03-15")
                @NotNull
                @PastOrPresent
                LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") @NotNull Gender gender,
        @Schema(description = "활동 지역 ID. 시군구(level 2) 단위 1개만 선택합니다.", example = "123") @NotNull
                Long activityRegionId,
        @Schema(
                        description = "관심 카테고리 목록. 중복 없이 1개 이상입니다.",
                        example = "[\"WELFARE\", \"EDUCATION\"]")
                @NotNull
                List<PostingCategory> interestCategories) {}
