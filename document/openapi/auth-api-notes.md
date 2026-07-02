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
이메일 인증 발송 → 인증 코드 확인 → (전화번호 중복 확인) → 회원가입(201) → 로그인 → 토큰 발급
```

- **회원가입 성공 시 토큰을 발급하지 않습니다.** 가입 완료 후 반드시 로그인 화면으로 보내주세요.
- 회원가입 성공은 `200`이 아니라 **`201 Created`** 입니다.

## 3. 엔드포인트 별 주의사항

### 3-1. 이메일 인증 발송 — `POST /api/v1/auth/email-verifications`

- 이미 가입된 이메일이면 `409 DUPLICATE_EMAIL` → 이 시점에 "이미 가입된 이메일" 안내 가능.
- 인증 코드는 **6자리 숫자, 유효시간 10분** (`expiresAt`으로 만료 시각 내려줌 → 타이머 표시에 사용).
- **재발송하면 이전 코드는 무효**가 되고 인증 상태도 초기화됩니다.
- ⚠️ 현재는 실제 메일이 발송되지 않고 **백엔드 서버 로그에 코드가 출력**됩니다(개발용). 로컬/개발 연동 시 백엔드에 코드 요청하세요. 실메일 발송은 이후 조치 예정.

### 3-2. 인증 코드 확인 — `POST /api/v1/auth/email-verifications/confirm`

- 실패 구분: 
  - `400 INVALID_VERIFICATION_CODE`(코드 불일치)
  - `400 EXPIRED_VERIFICATION_CODE`(만료)
  - `404 EMAIL_VERIFICATION_NOT_FOUND`(발송 이력 없음) — 각각 다른 안내 문구 권장.
- 이메일은 서버에서 **trim + 소문자 정규화** 후 대조하므로, 발송 때와 확인 때 대소문자가 달라도 동일 이메일로 처리됩니다.

### 3-3. 전화번호 중복 확인 — `POST /api/v1/auth/phone-numbers/availability`

- HTTP 메서드는 **POST**입니다.
- 하이픈, 공백은 서버에서 제거 후 판단합니다 (`010-1234-5678` → `01012345678`).
- 중복이어도 HTTP는 `200`이고 `data.available: false`로 구분합니다. 
  - **에러 응답이 아님에 주의.**

### 3-4. 회원가입 — `POST /api/v1/auth/signup`

**필드 규칙:**

| 필드 | 규칙 |
|---|---|
| `name` | 한글만이면 최대 7자, 영문/혼합은 최대 12자 |
| `birthDate` | `yyyy-MM-dd`, 미래 날짜 불가 |
| `gender` | `MALE` / `FEMALE` (그 외 값은 400) |
| `phoneNumber` | 숫자만 권장(하이픈은 서버가 제거) |
| `email` | **인증 완료된 이메일**이어야 함, 최대 255자 |
| `password` / `passwordConfirm` | 6~12자, 두 값 일치 필수 |
| `nickname` | 2~8자 |
| `introduction` | 최대 50자, **선택**(생략/빈문자열 가능 — 빈문자열은 null 처리됨) |
| `activityRegionIds` | **1~3개, 중복 불가, 최상위 지역(level 1)만** |
| `interestCategoryIds` | 1개 이상, 중복 불가 |
| `serviceTermsAgreed` / `privacyPolicyAgreed` | **반드시 `true`** |
| `marketingAgreed` | `true`/`false` 모두 가능(선택 동의), 필드 자체는 필수 |

**에러코드 → 화면 매핑:**

| 상태 | code | 안내 위치 |
|---|---|---|
| 400 | `PASSWORD_MISMATCH` | 비밀번호 확인 필드 |
| 400 | `EMAIL_NOT_VERIFIED` | 이메일 인증 단계로 유도 |
| 400 | `REQUIRED_TERMS_NOT_AGREED` | 약관 동의 |
| 400 | `INVALID_ACTIVITY_REGION_COUNT` / `INVALID_INTEREST_CATEGORY_COUNT` | 지역/카테고리 선택 |
| 404 | `REGION_NOT_FOUND` / `CATEGORY_NOT_FOUND` | 잘못된 id (정상 UI에선 미발생) |
| 409 | `DUPLICATE_EMAIL` / `DUPLICATE_PHONE_NUMBER` / `DUPLICATE_NICKNAME` | 각 필드 |

- 사전 중복확인을 통과했어도 가입 시점에 `409`가 다시 날 수 있습니다(그 사이 다른 가입). **409 재처리 로직 필수.**

### 3-5. 로그인 — `POST /api/v1/auth/login`

- 이메일 없음 / 비밀번호 틀림을 구분하지 않고 **동일하게 `401 INVALID_LOGIN`** (보안상 의도).
- `403 SUSPENDED_USER`(정지) / `403 WITHDRAWN_USER`(탈퇴)는 별도 안내 필요.
- 성공 시 `{ accessToken, refreshToken, tokenType: "Bearer" }`.

### 3-6. 토큰 재발급 — `POST /api/v1/auth/reissue`

- **재발급 성공 시 기존 Refresh Token은 즉시 폐기**(rotation)됩니다. 응답 받으면 **access/refresh 둘 다 교체 저장**하세요. 기존 refresh를 다시 쓰면 `401 REVOKED_TOKEN`.
- 401 세부
  - `INVALID_TOKEN`(서버에 없음)
  - `EXPIRED_TOKEN`(만료)
  - `REVOKED_TOKEN`(폐기됨) — 어떤 코드든 **재로그인 유도**가 기본 처리.
- Refresh Token 유효기간: **14일**.

### 3-7. 로그아웃 — `POST /api/v1/auth/logout`

- **Access Token 불필요**, Refresh Token만 body로 전송 (Access 만료 상태에서도 로그아웃 가능하게 하기 위함).
- 이미 만료/폐기된 토큰이어도 서버에 기록이 있으면 **200 성공(멱등)** — 여러 번 눌러도 안전.
- 서버가 모르는 토큰이면 `401 INVALID_TOKEN`. 이 경우에도 프론트는 로컬 토큰 삭제하고 로그아웃 완료 처리하면 됩니다.

### 3-8. 지역 조회 — `GET /api/v1/regions`

- 응답 필드: `id, name, level, code, parentId`.
- **회원가입 화면에서는 `id`, `name`만 사용**하면 됩니다. 활동 지역 후보는 `level === 1`인 항목만 필터링하세요.
- `code`는 지역 식별 코드입니다. 단일 시도는 1365 행정구역 코드와 매핑될 수 있고, 광역권은 서비스 내부 코드가 사용될 수 있습니다. 예: 서울=`"11"`, 경기=`"41"` — 문자열임에 주의.
- ⚠️ **응답 순서 보장 없음.** 3x3 버튼 고정 순서는 프론트에서 고정 배열로 매핑하세요.

### 3-9. 카테고리 조회 — `GET /api/v1/categories`

- 응답: `{ id, code, name }`, `id` 오름차순 정렬 보장.
- 용도 구분
  - **`id`** : 회원가입 요청에 보내는 값
  - **`code`**(`ENVIRONMENT` 등 6종 고정) : 아이콘 · 색상 · 필터 칩 매핑 키
  - **`name`** : 화면 표시용.
- `id = 1은 환경` 식의 **id 하드코딩 금지** — 반드시 조회 결과의 id 사용.

## 4. 논의 중 / 미확정 사항

### 4-1. 활동 지역 코드 체계 (주의사항)

- 활동 지역 버튼 9개: `서울 / 부산 / 인천 / 경기 / 강원 / 제주 / 경상 / 전라 / 충청`
- 단일 시도 6개(서울 : 11, 부산 : 26, 인천 : 28, 경기 : 41, 강원 : 42, 제주 : 50)는 표준 코드로 확정 가능하나, **경상/전라/충청은 여러 시도를 묶은 광역권이라 단일 행정구역 코드가 없음.**
- 현재 명세에는 서울/경기 예시만 반영했고, **9개 버튼의 실제 `id`/`code` 값은 region 초기 데이터 확정 후 공유** 예정.
- 확인 중: 대구·울산·광주·대전·세종의 소속 / 광역권 버튼의 code 규칙 / 강원 신설코드(42 vs 51).

### 4-2. 이메일 인증 재사용 정책 (팀 결정 필요)

- 현재는 한 번 인증되면 **만료 없이** 회원가입에 사용 가능. 인증 후 유효시간 제한 / 가입 시 consume 처리 여부를 논의 중 — 정책 확정 시 프론트 타이머/재인증 UX에 영향 가능.

### 4-3. Access Token은 임시 구조 (후속 PR)

- 현재 Access Token은 JWT가 아닌 **임시 랜덤 토큰**이며, 검증 필터가 없어 보호 API 인증이 아직 동작하지 않습니다. 토큰 **저장/재발급/로그아웃 흐름 연동은 지금 가능**하지만, "401 시 reissue 후 재시도" 같은 인터셉터 로직은 JWT 필터 적용 후 확정하세요.

### 4-4. 실메일 발송 (후속 PR)

- `LoggingEmailSender`(로그 출력) → SMTP/외부 메일 서비스로 교체 예정. 프론트 인터페이스 변화는 없음.
