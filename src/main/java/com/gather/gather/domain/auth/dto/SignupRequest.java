package com.gather.gather.domain.auth.dto;

import com.gather.gather.domain.auth.entity.Gender;
import com.gather.gather.domain.posting.entity.PostingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "회원가입 요청")
public record SignupRequest(
        @Schema(description = "이름. 완성형 한글 2~10자 또는 영문 2~20자만 허용합니다.", example = "홍길동")
                @NotBlank
                @Size(min = 2, max = 20)
                String name,
        @Schema(description = "생년월일. 미래 날짜는 허용하지 않습니다.", example = "2002-03-15")
                @NotNull
                @PastOrPresent
                LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") @NotNull Gender gender,
        @Schema(description = "전화번호. 하이픈과 공백은 서버에서 제거합니다.", example = "01012345678")
                @NotBlank
                @Size(max = 20)
                String phoneNumber,
        @Schema(
                        description = "회원가입에 사용할 휴대폰 인증 세션 ID",
                        format = "uuid",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID phoneVerificationId,
        @Schema(description = "이메일. 서버에서 앞뒤 공백 제거 및 소문자화합니다.", example = "test@example.com")
                @NotBlank
                @Email
                @Size(max = 255)
                String email,
        @Schema(
                        description = "회원가입에 사용할 이메일 인증 결과 ID",
                        format = "uuid",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                UUID emailVerificationId,
        @Schema(description = "비밀번호. 6자 이상 12자 이하입니다.", example = "password123!")
                @NotBlank
                @Size(min = 6, max = 12)
                String password,
        @Schema(description = "비밀번호 확인", example = "password123!")
                @NotBlank
                @Size(min = 6, max = 12)
                String passwordConfirm,
        @Schema(description = "닉네임. 완성형 한글 2~10자 또는 영문 2~20자만 허용합니다.", example = "길동")
                @NotBlank
                @Size(min = 2, max = 20)
                String nickname,
        @Schema(description = "소개글. 최대 50자입니다.", example = "함께 봉사하고 싶습니다.") @Size(max = 50)
                String introduction,
        @Schema(
                        description = "활동 지역 ID. 시도(level 1) 또는 시군구(level 2) 단위 1개를 선택합니다.",
                        example = "123")
                @NotNull
                Long activityRegionId,
        @Schema(
                        description = "관심 카테고리 목록. 중복 없이 1개 이상입니다.",
                        example = "[\"WELFARE\", \"EDUCATION\"]")
                @NotNull
                List<PostingCategory> interestCategories,
        @Schema(description = "서비스 이용약관 동의 여부. true 필수입니다.", example = "true") @NotNull
                Boolean serviceTermsAgreed,
        @Schema(description = "개인정보 처리방침 동의 여부. true 필수입니다.", example = "true") @NotNull
                Boolean privacyPolicyAgreed,
        @Schema(description = "맞춤형 봉사/이벤트 알림 수신 동의 여부", example = "false") @NotNull
                Boolean marketingAgreed) {}
