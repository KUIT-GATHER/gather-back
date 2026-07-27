package com.gather.gather.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전화번호 중복 확인 응답")
public record PhoneNumberAvailabilityResponse(
        @Schema(description = "정규화된 전화번호", example = "01012345678") String phoneNumber,
        @Schema(description = "true면 사용 가능, false면 이미 사용 중입니다.", example = "true")
                boolean available,
        @Schema(
                        description =
                                "사용할 수 없는 이유. available이 true면 null입니다. "
                                        + "WITHDRAWN_COOLDOWN은 탈퇴 후 유예 기간이라 나중에 다시 쓸 수 있다는 뜻입니다.",
                        example = "IN_USE")
                PhoneNumberUnavailableReason reason) {

    public static PhoneNumberAvailabilityResponse available(String phoneNumber) {
        return new PhoneNumberAvailabilityResponse(phoneNumber, true, null);
    }

    public static PhoneNumberAvailabilityResponse unavailable(
            String phoneNumber, PhoneNumberUnavailableReason reason) {
        return new PhoneNumberAvailabilityResponse(phoneNumber, false, reason);
    }
}
