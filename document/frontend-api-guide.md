# 🎨 프론트엔드 API 연동 가이드 — JWT 인증

백엔드에 JWT 인증이 적용되었습니다. 이 문서는 프론트엔드에서 API를 연동할 때 알아야 할 전부입니다.

---

## 0️⃣ API 주소와 쿠키 옵션

- 운영 API Base URL: `https://api.gathernow.kr`
- 운영 프론트 Origin: `https://gathernow.kr`
- Refresh Token은 응답 body가 아니라 `HttpOnly` 쿠키(`gather_refresh_token`)로 내려갑니다. 프론트에서 `localStorage` 등에 저장하지 마세요.
- 쿠키 송수신을 위해 API client에 credentials 옵션을 켜야 합니다.

```ts
// axios
axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
});

// fetch
fetch(url, { credentials: "include" });
```

## 1️⃣ 모든 보호 API에 헤더 필수

`/api/v1/auth/**`, 지역 조회(`GET /api/v1/regions/**`), 카테고리 조회(`GET /api/v1/categories`) 등의 공개 API 외 **모든 API 요청에 아래 헤더가 필요합니다.**

```
Authorization: Bearer <accessToken>
```

헤더가 없으면 401이 반환됩니다.

## 1-1️⃣ 회원가입 활동 지역

- 회원가입 요청은 `activityRegionId` 단일 값을 보냅니다.
- `activityRegionId`는 시도(`level === 1`) 또는 시군구(`level === 2`) 단위 지역 1개입니다.
- 이 값은 향후 공고/모임 검색 화면의 기본 지역 필터 초기값으로 사용합니다.

## 1-2️⃣ 회원가입 휴대폰 인증

- 일반·카카오 회원가입 모두 먼저 `POST /api/v1/auth/phone-verifications`에 `purpose: "SIGNUP"`을 보내 휴대폰 인증 세션을 생성합니다. `purpose`는 필수입니다.
- 모바일은 응답의 `receiverNumber`와 `messageText`로 문자 앱을 열고, PC는 `POST /api/v1/auth/phone-verifications/{verificationId}/qr-code`로 받은 QR 이미지를 표시합니다.
- 문자 전송 뒤 `POST /api/v1/auth/phone-verifications/{verificationId}/confirm`을 호출합니다. `PENDING`은 아직 확인되지 않은 정상 응답이고 `VERIFIED`가 인증 완료입니다.
- 인증 완료 후 30분 안에 동일한 `phoneNumber`와 응답에서 받은 `verificationId`를 회원가입 body의 `phoneVerificationId`로 제출해야 합니다. 인증 결과는 한 번만 사용할 수 있습니다.
- 인증이 없거나 ID·전화번호가 다르거나 만료·소비된 경우 `400 PHONE_VERIFICATION_REQUIRED`입니다. 요청 제한은 `429 PHONE_VERIFICATION_RATE_LIMITED`, 인증 제공자 장애는 `503 PHONE_VERIFICATION_PROVIDER_UNAVAILABLE`로 처리합니다.
- 별도의 전화번호 중복확인 API는 제공하지 않습니다. `SIGNUP` 목적의 OCTOMO 인증 완료 시 서버가 중복 여부를 확인합니다.

## 1-2-1️⃣ 아이디 찾기

- `purpose: "FIND_ACCOUNT"`로 휴대폰 인증을 완료한 뒤 `POST /api/v1/auth/account-recoveries/email`에 `phoneVerificationId`만 보냅니다.
- 서버는 인증 세션에 저장된 전화번호를 사용하므로 아이디 찾기 요청에 전화번호를 다시 보내지 않습니다.
- 이메일 계정은 `loginType: "EMAIL"`과 가입 이메일을, 카카오 전용 계정은 `loginType: "KAKAO"`와 `email: null`을 반환합니다.
- 복구 가능한 계정이 없으면 `404 ACCOUNT_NOT_FOUND`이며 이 정상 결과에서도 인증 세션은 소비됩니다.

## 1-2-2️⃣ 비밀번호 찾기(재설정)

- `purpose: "RESET_PASSWORD"`로 휴대폰 인증을 완료한 뒤 `POST /api/v1/auth/account-recoveries/password`에 `phoneVerificationId`만 보냅니다. 전화번호·이메일은 다시 보내지 않습니다.
- 응답의 `passwordResetToken`은 **이때 한 번만 내려오고 10분간 유효**합니다. 재설정 화면까지 클라이언트가 보관하세요.
- 카카오 전용 계정은 `409 PASSWORD_RESET_NOT_AVAILABLE`이므로 카카오 로그인을 안내합니다. 복구 가능한 계정이 없으면 `404 ACCOUNT_NOT_FOUND`이며, 두 결과 모두 인증 세션은 소비됩니다.
- 이어서 `POST /api/v1/auth/account-recoveries/password/reset`에 `passwordResetToken`, `password`, `passwordConfirm`을 보냅니다. 비밀번호는 회원가입과 같은 정책(공백 없이 6~12자)이며 정책 위반은 `400 VALIDATION_ERROR`, 확인 불일치는 `400 PASSWORD_MISMATCH`입니다.
- 재설정 성공 응답은 `data: null`이고 **새 토큰을 발급하지 않습니다.** 기존 Refresh Token은 모두 폐기되므로 로그인 화면으로 이동해 새 비밀번호로 다시 로그인시켜야 합니다.
- `401 PASSWORD_RESET_TOKEN_INVALID`(형식 오류·없음·이미 사용·파기됨)와 `401 PASSWORD_RESET_TOKEN_EXPIRED`(10분 경과)는 모두 `RESET_PASSWORD` 본인인증부터 다시 시작시키면 됩니다.

## 1-2-3️⃣ 마이페이지 로그인 유형과 비밀번호 변경

- `GET /api/v1/users/me` 응답에 `loginType`이 포함됩니다. **현재 세션에서 어떤 로그인 수단을 썼는지가 아니라 계정이 가진 credential 유형**입니다.
  - `EMAIL`: 이메일·비밀번호 credential 보유. 비밀번호 변경 UI를 노출합니다. (카카오도 함께 연결된 계정이면 `EMAIL`이 우선입니다.)
  - `KAKAO`: 카카오 전용 계정으로 비밀번호가 없습니다. 비밀번호 변경 UI를 노출하지 않습니다.
- `PATCH /api/v1/users/me`(프로필 수정) 응답에도 같은 `loginType`이 내려오며, 프로필 수정은 credential을 건드리지 않으므로 값이 바뀌지 않습니다.
- 로그인 상태에서 비밀번호를 바꿀 때는 Access Token과 함께 `PATCH /api/v1/users/me/password`에 `currentPassword`, `password`, `passwordConfirm`을 보냅니다.
  - 새 비밀번호 정책은 회원가입·재설정과 동일(공백 없이 6~12자)하며 정책 위반은 `400 VALIDATION_ERROR`, 확인 불일치는 `400 PASSWORD_MISMATCH`, 현재 비밀번호 오류는 `400 CURRENT_PASSWORD_MISMATCH`입니다. **현재 비밀번호 오류는 401이 아니므로 토큰 재발급 인터셉터를 태우지 마세요.**
  - 카카오 전용 계정은 `409 PASSWORD_CHANGE_NOT_AVAILABLE`이며, 계정 상태에 따라 `403 SUSPENDED_USER`, `403 WITHDRAWAL_PENDING_USER`, `403 WITHDRAWN_USER`가 내려올 수 있습니다.
- 성공 응답은 `data: null`이고 **새 Access/Refresh Token을 발급하지 않습니다.** 서버는 발급된 비밀번호 재설정 토큰과 모든 기기의 Refresh Token을 폐기하고 현재 Refresh 쿠키도 만료시킵니다.
- 기존 Access Token은 stateless JWT라 남은 만료 시간 동안 유효할 수 있으므로, 성공 직후 **클라이언트가 Access Token을 삭제하고 로그인 화면으로 이동해 새 비밀번호로 다시 로그인**시켜야 합니다.

## 1-3️⃣ 이메일 회원가입 자동 로그인과 프로필 이미지

- `POST /api/v1/auth/signup` 성공 시 기존 회원 정보와 함께 `accessToken`, `tokenType: "Bearer"`가 응답 body에 내려옵니다.
- Refresh Token은 로그인과 동일하게 `HttpOnly` 쿠키로만 발급됩니다. 회원가입 요청도 `withCredentials: true` 또는 `credentials: "include"`로 호출해야 쿠키가 저장됩니다.
- Access Token은 기존 로그인과 동일한 방식으로 관리하고, 프로필 이미지를 선택한 경우 `POST /api/v1/users/me/profile-image/presigned-url` → S3 PUT → `PATCH /api/v1/users/me/profile-image` 순서로 호출합니다.
- 프로필 이미지는 선택사항입니다. 이미지 처리에 실패해도 회원가입과 로그인 상태는 유지되며 기본 이미지를 사용하면 됩니다.

## 2️⃣ 401 응답 처리 — `error.code`로 분기

401 응답은 공통 에러 포맷 그대로입니다.

```json
{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }
```

| code | 의미 | 프론트 대응 |
|---|---|---|
| `UNAUTHORIZED` | 토큰 없음 / 헤더 형식 오류 | 로그인 화면으로 이동 |
| `EXPIRED_TOKEN` | Access Token 만료 | **재발급 후 원 요청 재시도** |
| `INVALID_TOKEN` | 무효 토큰 (서명 오류 등) | 토큰 폐기 후 재로그인 유도 |

## 3️⃣ 토큰 수명과 재발급 플로우

- Access Token: **30분** / Refresh Token: **14일**
- `EXPIRED_TOKEN` 401 수신 → `POST /api/v1/auth/reissue`(Refresh Token 쿠키 자동 전송) → 새 Access Token 응답 + 새 Refresh Token 쿠키 발급 → 원 요청 재시도, 하는 인터셉터 구현을 권장합니다
- ⚠️ **재발급하면 Refresh Token도 새것으로 교체됩니다 (rotation).** 새 Refresh Token은 `Set-Cookie`로만 전달됩니다. 프론트는 응답 body에서 refreshToken을 찾거나 저장하면 안 됩니다
- 재발급도 실패하면 (Refresh 만료 등) 재로그인을 유도하세요

## 4️⃣ 기타

- API 명세는 서버의 `/swagger-ui.html`에서 확인할 수 있고, 테스트 계정은 이메일·휴대폰 인증을 완료한 뒤 회원가입 API로 생성하면 됩니다
- Access Token 형식이 랜덤 문자열에서 JWT(`eyJ...`)로 바뀌어 길이가 길어졌습니다 — 토큰을 문자열 그대로 저장/전달한다면 코드 수정은 불필요합니다
- `/signup`, `/login`, `/reissue`, `/logout`, `/kakao/login`, `/kakao/signup` 요청은 모두 credentials 옵션이 필요합니다
- 개발 중 30분 만료가 불편하면 백엔드에 요청하세요 — 서버 설정으로 늘릴 수 있습니다
- JWT payload(`sub`=userId, `role`)는 디코딩해 볼 수 있지만 **표시 용도로만** 사용하고, 권한 판단의 근거로 신뢰하지 마세요

## 5️⃣ 회원 탈퇴

```http
DELETE /api/v1/users/me
Authorization: Bearer <accessToken>
```

- 요청 body는 보내지 않습니다.
- 쿠키를 포함할 수 있도록 `withCredentials: true` 또는 `credentials: "include"`를 사용합니다.
- `200 COMPLETED`와 `202 ACCEPTED`를 모두 탈퇴 요청 성공으로 처리하고, 메모리·상태 저장소의 Access Token을 즉시 폐기한 뒤 로그아웃 화면으로 이동합니다.
- `200 COMPLETED`는 일반 회원의 동기 탈퇴 완료 또는 이미 완료된 요청의 멱등 결과입니다.
- `202 ACCEPTED`는 카카오 연결 해제 작업이 안전하게 접수됐거나 이미 접수된 요청의 멱등 결과입니다. 카카오 연결 해제는 서버에서 비동기로 처리됩니다.
- 성공 응답의 `Set-Cookie`가 Refresh Token 쿠키를 만료시키므로 프론트에서 Refresh Token 값을 직접 다루지 않습니다.
- 탈퇴 상태 polling API는 제공하지 않습니다.
- `401`이면 `error.code`에 따라 인증 실패를 처리합니다. 탈퇴 성공 뒤에는 재발급을 시도하지 않습니다.
- `409 ACCOUNT_TERMINATION_STATE_CONFLICT`이면 로그아웃 성공으로 간주하지 말고 오류 안내 후 고객지원 또는 재시도를 안내합니다.

응답 예시:

```json
{
  "success": true,
  "data": {
    "status": "ACCEPTED",
    "occurredAt": "2026-08-01T14:00:00Z"
  },
  "error": null
}
```

## 🔍 문제 발생 시 빠른 진단표

| 증상 | 원인 | 해결 |
|---|---|---|
| API가 전부 401 | 토큰 없음/만료/헤더 오타 | 헤더 형식과 `error.code` 확인 |
| reissue 실패 | credentials 옵션 누락, Refresh Token 쿠키 없음, 또는 이전 쿠키 재사용(rotation) | `withCredentials`/`credentials: "include"`와 쿠키 저장 여부 확인 |
| 로그인 직후에도 401 | `Bearer ` 접두사 누락 또는 오타 | `Authorization: Bearer <token>` 형식 확인 |
