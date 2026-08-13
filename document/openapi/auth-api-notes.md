# Auth API 명세 참고사항 (프론트엔드 연동 가이드)

> 대상 명세: `document/openapi/openapi-auth.yaml` (Gather Auth API v1.0.0)
> 모든 API는 현재 **인증 헤더 없이** 호출 가능합니다.

## 1. 공통 응답 형식

모든 응답은 `{ success, data, error }` 구조입니다.


**성공 응답 예시**

```json
{ "success": true,  
  "data": { 
    "...": "..."
  }, 
  "error": null
}
```

**실패 응답 예시**

```json
{ "success": false, 
  "data": null, 
  "error": { 
    "code": "VALIDATION_ERROR", 
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

- 분기 처리는 **HTTP 상태코드 + `error.code`** 조합으로 해주세요. `message`는 표시용이며 변경될 수 있습니다.
- 요청 형식 오류(필드 누락, 타입 불일치, JSON 파싱 실패)는 전부 `400 VALIDATION_ERROR` 하나로 내려갑니다. **필드별 상세 오류는 내려가지 않으므로** 필드 단위 안내는 프론트 사전 검증으로 처리해야 합니다.

## 2. 회원가입 전체 흐름

```text
이메일 인증 발송 → 인증 코드 확인 → 휴대폰 문자 인증 → 회원가입(201) + 자동 로그인
```

- 회원가입 성공 시 Access Token은 응답 body로, Refresh Token은 HttpOnly 쿠키로 발급됩니다. 별도 로그인 호출은 필요하지 않습니다.
- 회원가입 요청도 기존 로그인과 동일하게 `withCredentials: true` 또는 `credentials: "include"`로 호출해야 Refresh Token 쿠키가 저장됩니다.
- 회원가입 성공은 `200`이 아니라 **`201 Created`** 입니다.

## 3. 엔드포인트 별 주의사항

### 3-1. 이메일 인증 발송 — `POST /api/v1/auth/email-verifications`

- 이미 가입된 이메일이면 `409 DUPLICATE_EMAIL` → 이 시점에 "이미 가입된 이메일" 안내 가능.
- 인증 코드는 **6자리 숫자, 유효시간 10분** (`expiresAt`으로 만료 시각 내려줌 → 타이머 표시에 사용). 이메일 인증 응답의 offset 없는 `expiresAt`·`resendAvailableAt`·`verifiedAt`은 UTC 기준입니다.
- **재발송하면 이전 코드는 무효**가 되고 인증 상태도 초기화됩니다.
- ⚠️ 기본 `log` 모드는 실제 메일을 발송하지 않으며, 개인정보와 인증정보 보호를 위해 백엔드 서버 로그에도 인증 코드를 출력하지 않습니다. 따라서 이 모드에서는 인증 코드 확인 API까지의 전체 연동을 완료할 수 없습니다.
- 로컬/개발에서 전체 이메일 인증 흐름을 검증하려면 `src/main/resources/application-secret.yml.example`을 참고해 팀에서 승인한 개발용 SMTP 계정과 `gather.email.mode: smtp`를 설정하세요. SMTP 자격 증명은 저장소에 커밋하지 마세요.

### 3-2. 인증 코드 확인 — `POST /api/v1/auth/email-verifications/confirm`

- 실패 구분: 
  - `400 INVALID_VERIFICATION_CODE`(코드 불일치)
  - `400 EXPIRED_VERIFICATION_CODE`(만료)
  - `404 EMAIL_VERIFICATION_NOT_FOUND`(발송 이력 없음) — 각각 다른 안내 문구 권장.
- 이메일은 서버에서 **trim + 소문자 정규화** 후 대조하므로, 발송 때와 확인 때 대소문자가 달라도 동일 이메일로 처리됩니다.

### 3-3. 휴대폰 문자 인증 — `POST /api/v1/auth/phone-verifications`

- 요청: `{ "phoneNumber": "01012345678" }`. 하이픈·공백은 제거하며 정규화 결과가 `010`으로 시작하는 11자리 숫자여야 합니다.
- 응답의 `verificationId`는 이후 QR·confirm 경로뿐 아니라 최종 회원가입 body의 `phoneVerificationId`로도 사용합니다. `receiverNumber`와 줄바꿈을 포함한 전체 SMS 본문인 `messageText`는 모바일에서 `sms:` URI를 구성할 때 그대로 사용합니다.
- 같은 번호의 인증 시작은 60초 간격으로 제한합니다.
- 문자 전송·확인 제한은 5분입니다. 인증문구는 서버가 안전한 난수로 만들며 프론트가 지정하거나 confirm 때 다시 보내지 않습니다.
- 모바일: `receiverNumber`/`messageText`를 별도 가공 없이 사용해 문자 앱을 열고 사용자가 전송한 뒤 confirm을 호출합니다.
- PC: `POST /api/v1/auth/phone-verifications/{verificationId}/qr-code`의 `qrCode` data URL을 이미지로 표시합니다. request body는 없습니다. 같은 세션은 10초 간격, 최대 3회로 제한합니다.
- 확인: `POST /api/v1/auth/phone-verifications/{verificationId}/confirm`. request body는 없으며, 문자가 아직 조회되지 않으면 오류가 아닌 `PENDING`, 성공하면 `VERIFIED`입니다. 같은 세션은 3초 간격, 최대 30회로 제한합니다.
- OCTOMO가 문자를 확인한 직후 서버가 현재 전화번호 중복과 탈퇴 후 재가입 제한을 확인합니다. 이는 빠른 안내를 위한 사전 검사이며, 최종 정합성은 가입 트랜잭션과 `users.phone_number` UNIQUE 제약이 보장합니다.
- 인증 완료 결과는 30분 안에 회원가입 body의 동일한 `phoneNumber`와 `phoneVerificationId`를 함께 제출해야 합니다. 한 번 가입에 사용된 인증은 재사용할 수 없으며, 가입 트랜잭션이 실패하면 소비도 함께 rollback됩니다. ID/번호 불일치, 만료, 이미 소비된 인증은 모두 `400 PHONE_VERIFICATION_REQUIRED`입니다.
- OCTOMO 장애는 `503 PHONE_VERIFICATION_PROVIDER_UNAVAILABLE`, 요청 제한은 `429 PHONE_VERIFICATION_RATE_LIMITED`로 변환됩니다.
- 자동 확인을 사용하더라도 1초 polling은 피하고 수 초 이상의 간격으로 호출하며 `expiresAt`에서 중단하세요. 휴대폰 인증 응답의 `expiresAt`은 UTC offset(`Z`)을 포함합니다.

### 3-3-1. 기존 전화번호 중복 확인(호환 유지) — `POST /api/v1/auth/phone-numbers/availability`

- HTTP 메서드는 **POST**입니다.
- 하이픈, 공백은 서버에서 제거 후 판단합니다 (`010-1234-5678` → `01012345678`).
- 이미 가입에 사용됐거나 탈퇴 후 재가입 제한 중이어도 HTTP는 `200`이고 `data.available: false`로 구분합니다.
  - **에러 응답이 아님에 주의.**
- 새 회원가입 화면에서는 별도 호출하지 않습니다. 기존 클라이언트 호환을 위해 endpoint만 유지하며, 휴대폰 인증 성공 처리 안에서 서버가 중복을 다시 확인합니다.

### 3-4. 회원가입 — `POST /api/v1/auth/signup`

**필드 규칙:**

| 필드 | 규칙 |
|---|---|
| `name` | 완성형 한글 2~10자 또는 영문 2~20자. 혼합·공백·숫자·특수문자 불가 |
| `birthDate` | `yyyy-MM-dd`, 미래 날짜 불가 |
| `gender` | `MALE` / `FEMALE` (그 외 값은 400) |
| `phoneNumber` | 숫자만 권장(하이픈은 서버가 제거) |
| `phoneVerificationId` | **필수 UUID**. 해당 `phoneNumber`를 VERIFIED로 만든 세션 ID |
| `email` | **인증 완료된 이메일**이어야 함, 최대 255자 |
| `password` / `passwordConfirm` | 6~12자, 두 값 일치 필수 |
| `nickname` | 완성형 한글 2~10자 또는 영문 2~20자. 혼합·공백·숫자·특수문자 불가 |
| `introduction` | 최대 50자, **선택**(생략/빈문자열 가능 — 빈문자열은 null 처리됨) |
| `activityRegionId` | **시도(level 1) 또는 시군구(level 2) 단위 활동 지역 1개**. 향후 공고/모임 검색의 기본 지역 필터 초기값으로 사용 |
| `interestCategories` | `PostingCategory` enum(ENVIRONMENT/EDUCATION/CULTURE/COMMUNITY/WELFARE/OVERSEAS) 값 배열, 1개 이상·중복 불가. 정의되지 않은 값은 400(`VALIDATION_ERROR`) |
| `serviceTermsAgreed` / `privacyPolicyAgreed` | **반드시 `true`** |
| `marketingAgreed` | `true`/`false` 모두 가능(선택 동의), 필드 자체는 필수 |

**에러코드 → 화면 매핑:**

| 상태 | code | 안내 위치 |
|---|---|---|
| 400 | `PASSWORD_MISMATCH` | 비밀번호 확인 필드 |
| 400 | `EMAIL_NOT_VERIFIED` | 이메일 인증 단계로 유도 |
| 400 | `PHONE_VERIFICATION_REQUIRED` | 휴대폰 인증 단계로 유도(ID/번호 불일치·30분 초과·이미 소비 포함) |
| 400 | `REQUIRED_TERMS_NOT_AGREED` | 약관 동의 |
| 400 | `INVALID_INTEREST_CATEGORY_COUNT` | 카테고리 선택 |
| 400 | `VALIDATION_ERROR` | 정의되지 않은 카테고리 enum 값 (정상 UI에선 미발생) |
| 404 | `REGION_NOT_FOUND` | 잘못된 지역 id (정상 UI에선 미발생) |
| 409 | `DUPLICATE_EMAIL` / `DUPLICATE_PHONE_NUMBER` / `DUPLICATE_NICKNAME` | 각 필드 |
| 409 | `ACCOUNT_REJOIN_BLOCKED` | 탈퇴 후 7일 재가입 제한 안내 |

- 사전 중복확인을 통과했어도 가입 시점에 `409`가 다시 날 수 있습니다(그 사이 다른 가입). **409 재처리 로직 필수.**
- 가입 시 서버가 `phoneVerificationId`의 행을 잠그고 요청 전화번호 일치, 30분 이내 `VERIFIED`, 미소비 상태를 확인한 뒤 같은 트랜잭션에서 한 번만 소비합니다.
- 성공 응답은 기존 회원 정보와 `{ accessToken, tokenType: "Bearer" }`를 함께 반환하고, Refresh Token은 body가 아닌 `Set-Cookie`로만 전달합니다.
- 응답의 Access Token은 기존 로그인과 동일한 방식으로 관리합니다. 프로필 이미지가 선택된 경우 기존 `/api/v1/users/me/profile-image/**` 플로우를 이어서 호출하며, 이미지 처리 실패는 이미 완료된 회원가입을 취소하지 않습니다.

### 3-5. 로그인 — `POST /api/v1/auth/login`

- 이메일 없음 / 비밀번호 틀림을 구분하지 않고 **동일하게 `401 INVALID_LOGIN`** (보안상 의도).
- `403 SUSPENDED_USER`(정지) / `403 WITHDRAWAL_PENDING_USER`(탈퇴 처리 중) / `403 WITHDRAWN_USER`(탈퇴)는 별도 안내 필요.
- 성공 시 응답 body는 `{ accessToken, tokenType: "Bearer" }`.
- Refresh Token은 `Set-Cookie: gather_refresh_token=...; HttpOnly; Path=/api/v1/auth; SameSite=Lax`로만 전달됩니다.

### 3-6. 토큰 재발급 — `POST /api/v1/auth/reissue`

- 요청 body는 없습니다. 브라우저가 `gather_refresh_token` 쿠키를 자동 전송해야 하므로 프론트 API client에 credentials 옵션을 켜야 합니다.
- **재발급 성공 시 기존 Refresh Token은 즉시 폐기**(rotation)됩니다. 응답 body의 Access Token을 교체하고, 새 Refresh Token은 `Set-Cookie`로 갱신됩니다. 기존 refresh를 다시 쓰면 `401 REVOKED_TOKEN`.
- 401 세부
  - `INVALID_TOKEN`(서버에 없음)
  - `EXPIRED_TOKEN`(만료)
  - `REVOKED_TOKEN`(폐기됨) — 어떤 코드든 **재로그인 유도**가 기본 처리.
- 계정 상태가 정지·탈퇴 처리 중·탈퇴이면 각각 `403 SUSPENDED_USER` / `WITHDRAWAL_PENDING_USER` / `WITHDRAWN_USER`로 재발급이 차단됩니다.
- Refresh Token 유효기간: **14일**.

### 3-7. 로그아웃 — `POST /api/v1/auth/logout`

- **Access Token 불필요**, 요청 body 없음. Refresh Token은 `gather_refresh_token` 쿠키로 전송됩니다.
- 이미 만료/폐기된 토큰이어도 서버에 기록이 있으면 **200 성공(멱등)** — 여러 번 눌러도 안전.
- 성공 시 서버가 `Max-Age=0` 삭제 쿠키를 내려 브라우저의 Refresh Token 쿠키를 제거합니다.
- 서버가 모르는 토큰이면 `401 INVALID_TOKEN`. 이 경우에도 프론트는 로컬 Access Token을 삭제하고 로그아웃 완료 처리하면 됩니다.

### 3-8. 지역 조회 — `GET /api/v1/regions`

- 응답 필드: `id, name, level, code, parentId, regionGroupId`.
- **회원가입 화면에서는 `id`, `name`, `level`을 사용**하면 됩니다. 활동 지역 후보는 `level === 1`인 시도와 `level === 2`인 시군구입니다.
- `code`는 지역 식별 코드입니다. 시도/시군구는 1365 행정구역 코드입니다. 예: 서울특별시=`"6110000"`, 강남구=`"3220000"` — 문자열임에 주의.
- `regionGroupId`는 시도(`level === 1`) 행에만 존재하고(시군구는 항상 `null`), 소속 권역(9버튼) `id`입니다.
- ⚠️ **응답 순서 보장 없음.** 필요하면 `id` 기준으로 정렬해서 쓰세요.

### 3-8-1. 활동 지역 권역(9버튼) 조회 — `GET /api/v1/regions/groups`

- 응답: `{ id, code, name }[]`. **`sort_order` 기준 고정 순서로 정렬되어 내려오므로 프론트에서 별도 정렬/고정 배열 매핑 불필요.**
- 9버튼: `서울 / 부산 / 인천 / 경기 / 강원 / 제주 / 경상 / 전라 / 충청`. `code`는 1365 코드가 아닌 서비스 내부 코드(`GRP_SEOUL` 등, §4-1 참고).
- 버튼 클릭 → 시군구 좁히기: `/regions` 목록에서 `level === 1 && regionGroupId === group.id`인 시도 id 집합을 구한 뒤, 그 집합을 `parentId`로 갖는 `level === 2` 행만 필터링.

### 3-8-2. 봉사공고 지역 필터 — `GET /api/v1/postings?regionId=` / `?regionGroupId=` (신규, 2026-07-10)

봉사공고 목록 조회에 지역 필터 파라미터 2개가 추가됐습니다. 둘 다 **선택(optional)**이고, **동시에 보낼 수 없습니다.**

| 파라미터 | 값 | 용도 |
|---|---|---|
| `regionId` | `/regions` 응답의 `id` (시도 또는 시군구) | 지도/드롭다운 등에서 **특정 지역 하나**를 골랐을 때 |
| `regionGroupId` | `/regions/groups` 응답의 `id` (9버튼) | **9버튼 중 하나**를 골랐을 때 (경상/전라/충청처럼 시도가 여러 개 묶인 버튼도 이 파라미터 하나로 처리됨) |

- 어느 쪽을 보내든 서버가 **그 지역의 하위 지역 공고까지 자동으로 포함**해서 반환합니다 (예: 서울 시도로 `regionId`를 보내면 서울 산하 모든 구 공고가, 경상 그룹으로 `regionGroupId`를 보내면 대구·울산·경북·경남 전체 공고가 포함됩니다). 프론트에서 하위 지역 id를 직접 모아서 여러 번 요청할 필요 없습니다.
- **`regionId`와 `regionGroupId`를 동시에 지정하면 `400 VALIDATION_ERROR`**가 납니다. UI 상태를 "9버튼 모드"와 "특정 지역 모드" 둘 중 하나로만 유지하세요 — 버튼을 눌렀다가 특정 지역을 다시 고르면 이전 파라미터는 지우고 새 파라미터만 보내야 합니다.
- 둘 다 생략하면 지역 필터 없이 전체 공고가 반환됩니다.
- 예시:
  - 서울 버튼(단일 시도) 선택: `GET /api/v1/postings?regionGroupId=1`
  - 경상 버튼(여러 시도 묶음) 선택: `GET /api/v1/postings?regionGroupId=7`
  - 특정 구(강남구 등)로 좁혀서 선택: `GET /api/v1/postings?regionId=18`
  - 둘 다 보낸 잘못된 예 (400 발생): `GET /api/v1/postings?regionId=18&regionGroupId=7`

### 3-9. 카테고리 조회 — `GET /api/v1/categories`

- 응답: `{ id, code, name }`, `id` 오름차순 정렬 보장.
- 용도 구분
  - **`id`** : 회원가입 요청에 보내는 값
  - **`code`** : 1365 기준 숫자 문자열 코드. 현재 16개 값이 seed되어 있으며 아이콘 · 색상 · 필터 칩 매핑 키로 사용할 수 있음
  - **`name`** : 화면 표시용.
- `id = 1은 생활편의` 식의 **id 하드코딩 금지** — 반드시 조회 결과의 id 사용.
- 현재 seed 기준 카테고리 코드 체계:

| code | name |
|---|---|
| `0100` | 생활편의 |
| `0200` | 주거환경 |
| `0300` | 상담·멘토링 |
| `0400` | 교육 |
| `0500` | 보건·의료 |
| `0600` | 농어촌 봉사 |
| `0700` | 문화·체육·예술·관광 |
| `0800` | 환경·생태계보호 |
| `0900` | 사무행정 |
| `1000` | 지역안전·보호 |
| `1100` | 인권·공익 |
| `1200` | 재난·재해 |
| `1300` | 국제협력·해외봉사 |
| `1500` | 기타 |
| `1700` | 자원봉사 기본교육 |
| `1900` | 온라인자원봉사 |

### 3-10. 카카오 로그인 — `POST /api/v1/auth/kakao/login`

- 요청 body: `{ authorizationCode, redirectUri }`. `redirectUri`는 인가 요청에 쓴 값 그대로이며, 서버 허용 목록과 **문자열까지 정확히 일치**해야 합니다(trailing slash 하나만 달라도 400).
- **OAuth `state` 검증은 프론트 책임입니다.** 카카오 인가 요청 시 난수를 만들어 `state`에 실어 보내고 `sessionStorage`에 저장한 뒤, 콜백에서 쿼리의 `state`와 **문자열 일치를 확인한 경우에만** 이 엔드포인트를 호출하세요. 불일치하면 호출하지 말고 로그인을 처음부터 다시 시작시키고, 확인 후에는 `sessionStorage` 값을 삭제하세요(`localStorage`는 탭 간 공유돼 1회용 성질이 깨지므로 쓰지 마세요). 이 검증이 없으면 공격자가 자신의 인가코드로 사용자를 공격자 계정에 로그인시킬 수 있습니다(로그인 CSRF).
- `state`는 **서버로 보내지 않습니다.** 요청 body는 위의 `{ authorizationCode, redirectUri }` 그대로이며, 이 항목 때문에 API 계약이 바뀌지는 않습니다. 서버 측 이중 검증안은 설계만 해두고 보류했습니다 — 배경과 재검토 조건은 [kakao-oauth-state-design.md](../kakao-oauth-state-design.md) 참고.
- 성공은 항상 `200`이고 `data.signupStatus`로 분기합니다. **둘 다 정상 응답이며 `ADDITIONAL_INFO_REQUIRED`는 에러가 아닙니다.**
  - `LOGIN_COMPLETED`(기존 회원): `data = { signupStatus, accessToken, tokenType: "Bearer" }` + Refresh Token 쿠키. 일반 로그인과 동일하게 처리하면 됩니다.
  - `ADDITIONAL_INFO_REQUIRED`(신규 회원): `data = { signupStatus, signupToken, profile: { nickname } }`. 쿠키는 내려가지 않습니다. `signupToken`은 일회성 opaque 값이며 메모리에 보관하고 추가정보 화면으로 이동하세요. `profile.nickname`은 초깃값 용도이며 `null`일 수 있습니다.
- `400`(인가 코드 무효·재사용, redirectUri 불일치), `500`(카카오 장애), `503 KAKAO_API_UNAVAILABLE`(카카오 요청 제한)은 **`error.code`를 보지 말고 전부 "카카오 로그인 다시 시작"**으로 처리하세요. 콜백 새로고침·뒤로가기로 인가 코드가 재사용되면 `400`이 나는 것이 정상입니다.
- **단, `403`과 `409`는 재시작 대상이 아닙니다.** 기존 카카오 회원이 정지·탈퇴 처리 중·탈퇴 상태면 일반 로그인과 동일하게 `403 SUSPENDED_USER` / `WITHDRAWAL_PENDING_USER` / `WITHDRAWN_USER`로 차단됩니다. `409 ACCOUNT_REJOIN_BLOCKED`는 탈퇴 후 7일 재가입 제한, `409 SOCIAL_ACCOUNT_NOT_LINKED`는 연결 해제된 소셜 계정이므로 재로그인을 반복시키지 말고 계정 상태를 안내해야 합니다.

### 3-11. 카카오 추가정보 가입 — `POST /api/v1/auth/kakao/signup`

- 로그인에서 받은 `signupToken`을 **`X-Signup-Token` 헤더**로 보냅니다(`Authorization` 아님). 헤더가 없거나 형식이 잘못됐거나 존재하지 않거나 만료·소비·취소된 세션이면 `401`입니다.
- 요청 body는 회원가입(`/signup`)에서 **`email`·`password`·`passwordConfirm`만 뺀** 형태입니다. `phoneVerificationId`는 동일하게 필수이며, 카카오 가입은 이메일·비밀번호를 받지 않습니다. 나머지 필드 규칙은 §3-4와 동일합니다.
- 일반 가입과 동일한 단일 서버 정책으로 `phoneVerificationId`를 검증하고 한 번만 소비합니다. 유효한 인증이 없으면 `400 PHONE_VERIFICATION_REQUIRED`이며 signupToken은 유지한 채 인증을 진행하면 됩니다.
- 성공은 `201`이며 `{ accessToken, tokenType: "Bearer" }` + Refresh Token 쿠키를 곧바로 내려줍니다(가입 후 자동 로그인). 별도 로그인 호출이 필요 없습니다.
- 에러코드 → 화면 매핑:

| 상태 | code | 처리 |
|---|---|---|
| 401 | `SIGNUP_TOKEN_EXPIRED` | signupToken 제거 후 카카오 로그인부터 재시작(15분 초과) |
| 401 | `SIGNUP_TOKEN_INVALID` | signupToken 제거 후 카카오 로그인부터 재시작(형식 오류·존재하지 않음·소비 또는 취소됨·헤더 누락) |
| 400 | `PHONE_VERIFICATION_REQUIRED` | 휴대폰 인증 단계로 유도(signupToken은 유지) |
| 400 | `REQUIRED_TERMS_NOT_AGREED` / `INVALID_INTEREST_CATEGORY_COUNT` / `VALIDATION_ERROR` | 해당 입력 수정(signupToken은 유지) |
| 404 | `REGION_NOT_FOUND` | 지역 재선택(signupToken은 유지) |
| 409 | `DUPLICATE_PHONE_NUMBER` | **이미 가입된 전화번호 = 기존 계정과 동일인**. 입력 오류가 아니라 "기존 계정(이메일 로그인)으로 로그인" 안내 화면으로 유도 |
| 409 | `DUPLICATE_NICKNAME` | 닉네임 수정(signupToken은 유지) |
| 409 | `ALREADY_REGISTERED` | 세션 발급 후 이미 가입 완료된 카카오 계정. signupToken 제거 후 로그인 다시 시도 |
| 409 | `ACCOUNT_REJOIN_BLOCKED` | 탈퇴 후 7일 재가입 제한 안내 |
| 409 | `SOCIAL_ACCOUNT_NOT_LINKED` | 연결 해제된 소셜 계정 안내 |

- signupToken 유지/제거 기준: **401·`ALREADY_REGISTERED`는 제거**하고 카카오 로그인부터, 그 외 검증 오류는 **유지**한 채 입력만 고쳐 재요청하세요. 카카오 로그인을 다시 수행하면 같은 identity에도 새 세션이 발급될 수 있지만 각 token은 일회성이며, 가입 완료 후 다른 세션으로 다시 가입할 수 없습니다.

## 4. 논의 중 / 미확정 사항

### 4-1. 활동 지역 코드 체계 — 확정 (region_group 도입, 2026-07-10)

- 활동 지역 버튼 9개: `서울 / 부산 / 인천 / 경기 / 강원 / 제주 / 경상 / 전라 / 충청`
- `GET /api/v1/regions/groups` 신설: 9버튼을 고정 노출 순서(`sort_order`)로 반환. 응답 필드: `id, code, name`.
- 경상/전라/충청처럼 여러 시도를 묶은 권역은 1365 행정구역 코드가 없어 서비스 내부 코드(`GRP_GYEONGSANG` 등)를 사용. 단일 시도 6개 버튼(서울/부산/인천/경기/강원/제주)도 동일한 내부 코드 체계(`GRP_SEOUL` 등)로 통일.
- 대구·울산·광주·대전·세종 소속 확정: **대구·울산→경상, 광주→전라, 대전·세종→충청** (구 관할 기준).
- `GET /api/v1/regions` 응답에 `regionGroupId` 필드 추가(시도 `level=1` 행에만 값 존재, 시군구 `level=2`는 항상 `null`). 버튼 클릭 시 시군구 좁히기: `regions` 목록에서 `level===1 && regionGroupId===groupId`인 시도들의 `id` 집합을 구한 뒤, 그 집합을 `parentId`로 갖는 `level===2` 행을 필터링.
- 관련 마이그레이션: `V11__create_region_group_table.sql`.
- **검색 필터 연동 완료**: 공고 목록의 지역 필터 사용법(`regionId`/`regionGroupId` 파라미터, 예시, 400 케이스)은 §3-8-2 참고.
- **필터 깊이 확장(2026-07-11)**: `regionId`/`regionGroupId`로 필터링하면 직계 자식뿐 아니라 그 아래 한 단계(예: 시도 선택 시 시군구 + 그 소속 읍/면/동까지)까지 포함하도록 서버 쿼리를 확장했다. 프론트에서 지금 당장 바꿀 건 없다 — `level=4`(읍/면/동) 데이터는 아직 이 브랜치에 없어서 `/regions` 응답엔 여전히 `level 1`(시도)·`level 2`(시군구)만 내려온다. 다만 향후 읍/면/동 데이터가 합쳐진 뒤에는:
  - 지금 쓰고 있는 `regionId`/`regionGroupId` 파라미터를 그대로 재사용하면 되고(새 파라미터 불필요), 읍/면/동 단위 공고도 자동으로 필터 결과에 포함된다.
  - 특정 읍/면/동 하나로 좁혀서 검색하고 싶으면 그 동의 `id`를 그대로 `regionId`에 넣으면 된다(시도/시군구와 동일한 파라미터).
  - `level` 값은 `1, 2, 4`만 쓰고 **3은 의도적으로 비워둔다**(1365 API가 시/군/구를 구분하지 않아 사용 안 함) — `level`이 1,2,3 연속이라고 가정하지 말고 항상 실제 값으로 분기할 것.

### 4-2. 이메일 인증 재사용 정책 (팀 결정 필요)

- 현재는 한 번 인증되면 **만료 없이** 회원가입에 사용 가능. 인증 후 유효시간 제한 / 가입 시 consume 처리 여부를 논의 중 — 정책 확정 시 프론트 타이머/재인증 UX에 영향 가능.

### 4-3. Access Token 인증 구조

- Access Token은 `TokenProvider`가 서명한 JWT이며, 보호 API에서는 `JwtAuthenticationFilter`가 토큰을 검증해 인증 정보를 구성합니다. 프론트는 보호 API의 `401` 응답 시 Refresh Token 쿠키로 `/api/v1/auth/reissue`를 호출한 뒤 새 Access Token으로 원 요청을 재시도할 수 있습니다.

### 4-4. 실메일 발송 (후속 PR)

- `LoggingEmailSender`(로그 출력) → SMTP/외부 메일 서비스로 교체 예정. 프론트 인터페이스 변화는 없음.
