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

공개 API(회원가입/로그인/재발급/로그아웃, 지역/카테고리 조회) 외 **모든 API 요청에 아래 헤더가 필요합니다.**

```
Authorization: Bearer <accessToken>
```

헤더가 없으면 401이 반환됩니다.

## 1-1️⃣ 회원가입 활동 지역

- 회원가입 요청은 `activityRegionId` 단일 값을 보냅니다.
- `activityRegionId`는 시군구(`level === 2`) 단위 지역 1개입니다.
- 이 값은 향후 공고/모임 검색 화면의 기본 지역 필터 초기값으로 사용합니다.

## 1-2️⃣ 이메일 회원가입 자동 로그인과 프로필 이미지

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

- API 명세는 서버의 `/swagger-ui.html`에서 확인할 수 있고, 테스트 계정은 회원가입 API로 직접 생성하면 됩니다
- Access Token 형식이 랜덤 문자열에서 JWT(`eyJ...`)로 바뀌어 길이가 길어졌습니다 — 토큰을 문자열 그대로 저장/전달한다면 코드 수정은 불필요합니다
- `/signup`, `/login`, `/reissue`, `/logout` 요청은 모두 credentials 옵션이 필요합니다
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
