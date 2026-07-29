# 회원 탈퇴 · 카카오 연결 해제 웹훅 설계

작성일: 2026-07-27
설계 인터뷰(`/grill-me`) 결과. 구현 착수 전 합의된 결정 사항을 기록한다.

---

## 2026-07-28 implementation amendments

- Rejoin becomes available exactly at `withdrawnAt + 7 days`; the daily cleanup job is only a privacy-cleanup safety net. Availability checks never mutate data. Signup locks the phone-number holder, anonymizes an eligible withdrawn holder, flushes it, and then creates the new account in the same transaction.
- Kakao unlink treats only a 2xx response and `HTTP 400` with response `code = -101` as resolved. Every other response, including malformed or unknown 4xx responses, retains `social_account` for retry. The seven-day forced deletion remains an observable recovery limit.
- An authenticated Kakao webhook returns 200 after an internal processing failure so Kakao does not retry indefinitely. Its transactional work rolls back before the outer HTTP layer logs `appId`, Kakao user id, referrer type, and cause. No durable retry queue is introduced in this scope; structured error logs are the manual recovery path.
- A valid unlink webhook removes the local social-account row even for an already withdrawn user. Account termination remains idempotent and does not re-publish the withdrawal event.
- Meeting-host transfer and one-member meeting disband remain an external integration contract with `origin/feature/user-withdrawal-cleanup`: choose the earliest approved member (then `meeting_member.id`), update host and roles atomically, disband when no other approved member exists, isolate AFTER_COMMIT work in a new transaction, and prevent listener failures from failing withdrawal.

## 1. 기능 목표

Gather 계정을 종료하는 두 개의 진입점을 만들고, 종료 처리 로직을 한 곳에서 공유한다.

```
탈퇴 API   : 사용자 → 우리 서버 → (카카오 unlink 호출) → 카카오
연결해제 웹훅: 카카오 → 우리 서버                        ※ 호출할 것이 없음
```

두 경로는 **계정 종료 코어**를 공유하며, 차이는 카카오 unlink API 호출 유무 하나뿐이다.
카카오는 서비스가 직접 unlink API를 호출한 경우 웹훅을 발송하지 않으므로 두 경로가 서로를 트리거하지 않는다.

---

## 2. 확정된 요구사항

| # | 결정 | 근거 |
|---|---|---|
| R1 | 이번 범위는 **(A) 계정 종료 코어 + (B) 탈퇴 API + (C) 웹훅**. (D) 도메인 데이터 정리는 이벤트만 발행하고 강현구님 담당 | (B)·(C)가 `(A) + 외부 연동 한 줄` 차이라 분리 시 인터페이스 협의 비용이 더 큼 |
| R2 | User는 **soft delete**(`status = WITHDRAWN`). hard delete 불가 | `meeting.host_id`가 `NOT NULL` FK → users |
| R3 | 재가입 허용. **탈퇴 후 7일 경과 시부터** 가능 | PM 결정 |
| R4 | 익명화는 **7일 유예 후 스케줄러가 수행**(지연 익명화) | 즉시 익명화하면 원 소유자를 식별할 수 없어 7일 제한을 강제할 수단이 사라짐 |
| R5 | 익명화 대상은 `phone_number`, `nickname`, `email` 3개. 접두사 `wd_` | 유니크 제약이 걸린 컬럼만. `name`/`birth_date`/`gender`는 보존 |
| R6 | 모임장이어도 **탈퇴를 차단하지 않는다.** 자동 승계·해체는 (D)가 처리 | 위임·삭제 API가 없어 차단하면 탈퇴 불가능한 사용자가 생김. 웹훅 경로는 애초에 차단 불가 |
| R7 | 탈퇴 시 **재인증을 요구하지 않는다** | 카카오 회원은 `password`가 null이라 적용 불가. 웹훅 경로엔 재인증 개념 자체가 없음 |
| R8 | Access Token 30분 잔존은 **수용**한다 | `SUSPENDED`도 동일한 구조. 탈퇴만 다르게 처리하면 `LoginPolicy` 일원화 설계가 깨짐 |
| R9 | unlink 실패 건은 `social_account` row 잔존 = 재처리 큐 | 별도 상태 컬럼 불필요 |
| R10 | 웹훅 URL은 **운영에만 등록** | 세 환경이 카카오 앱 하나를 공유. 웹훅 URL은 앱당 1개 |

---

## 3. 사용자 시나리오

### S1. 본인 탈퇴 (카카오 회원)

1. 앱에서 탈퇴 버튼 → 프론트 확인 모달 → `DELETE /api/v1/users/me`
2. 서버: `status = WITHDRAWN`, `withdrawn_at = now`, `withdrawal_reason = SELF`, Refresh Token 전량 삭제
3. 커밋 후: 카카오 unlink 호출 → 성공 시 `social_account` 삭제
4. 응답 200 + Refresh Token 쿠키 만료. 프론트가 Access Token 폐기
5. `withdrawnAt + 7일`부터 가입 요청이 기존 계정을 잠금·익명화·flush한 뒤 같은 전화번호로 재가입 가능. 스케줄러 익명화는 개인정보 정리 안전망

### S2. 본인 탈퇴 (일반 회원)

S1과 동일하되 `social_account`가 없으므로 3단계를 건너뛴다.

### S3. 카카오에서 직접 연결 해제

1. 사용자가 카카오톡 "연결된 서비스 관리" 등에서 연결 해제
2. 카카오 → `POST /api/v1/webhooks/kakao/unlink`
3. 어드민 키 검증 → `social_account` 조회 → 계정 종료(`withdrawal_reason = KAKAO_UNLINK`) + 같은 트랜잭션에서 `social_account` 삭제
4. 3초 내 200 반환
5. 사용자는 자신이 탈퇴됐음을 모른 채 재로그인 시도 → 신규 가입 플로우 진입 → 7일 이내면 쿨다운 에러

### S4. 가입 미완료 웹훅 (`INCOMPLETE_SIGN_UP`)

카카오 로그인 후 가입토큰 단계에서 이탈한 사용자. `social_account`가 없는 것이 정상이므로 **no-op + 200**.

### S5. 중복 웹훅

이미 `WITHDRAWN`이면 **no-op + 200** (멱등).

---

## 4. API 설계

### 4-1. 탈퇴 API

```
DELETE /api/v1/users/me
Authorization: Bearer {accessToken}
요청 본문 없음
```

| 응답 | 조건 |
|---|---|
| `200 ApiResponse<Void>` | 성공. `Set-Cookie`로 Refresh Token 쿠키 만료 |
| `401 UNAUTHORIZED` | 토큰 없음/무효 |
| `404 USER_NOT_FOUND` | 토큰의 userId에 해당하는 사용자 없음 |

- 카카오 장애로 unlink에 실패해도 **200을 반환한다.**
- 이미 `WITHDRAWN`인 사용자의 재요청은 no-op 200 (Access Token이 30분 잔존하므로 실제로 발생 가능).
- ⚠️ R6이 뒤집히면(모임장 탈퇴 차단) 여기에 사전 검증 + 409를 추가한다. 그 외 구조는 바뀌지 않는다.

### 4-2. 연결 해제 웹훅

```
POST /api/v1/webhooks/kakao/unlink   (Content-Type: application/x-www-form-urlencoded)
GET  /api/v1/webhooks/kakao/unlink   (쿼리스트링)
Authorization: KakaoAK {대표 어드민 키}
app_id, user_id, referrer_type
```

| 항목 | 결정 |
|---|---|
| 메서드 | **GET·POST 둘 다 수신.** 카카오 문서에 두 예제가 모두 있고 어느 쪽을 보낼지 확정 불가 |
| 바인딩 | `@RequestParam` 3개. **JSON 아님** — `@RequestBody`는 415 |
| `referrer_type` | **String으로 받고 enum 변환하지 않는다.** 카카오가 값을 추가하면 400으로 죽음. 로그 전용 |
| 성공 응답 | `200`, **본문 없음**(`ResponseEntity<Void>`). 카카오는 본문을 보지 않으므로 `ApiResponse` 래핑 무의미 |
| `social_account` 없음 | **200 no-op** |
| 이미 WITHDRAWN | **200 no-op** |
| `app_id` 불일치 | **warn 로그 + 200.** 설정 오류지 공격이 아니며 재전송을 유발할 이유가 없음 |
| 어드민 키 불일치 | **401.** 카카오가 보낸 요청이 아니므로 200은 부적절 |

### 4-3. 전화번호 가용성 API 확장 (기존 API 변경)

```java
// 기존
record PhoneNumberAvailabilityResponse(String phoneNumber, boolean available)
// 변경 — 필드 추가(하위 호환)
record PhoneNumberAvailabilityResponse(String phoneNumber, boolean available, String reason)
```

`reason`은 `available == true`일 때 `null`, 아니면 `IN_USE` 또는 `WITHDRAWN_COOLDOWN`.
필드 추가이므로 프론트가 무시하면 현행 동작이 유지된다.

---

## 5. 도메인 및 DB 설계

### 5-1. 마이그레이션

`V31__add_withdrawal_columns_to_users.sql` (통합 순서: V28 알림 → V29/V30 배지 → V31 회원 탈퇴)

```sql
ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME(6) NULL,
    ADD COLUMN withdrawal_reason VARCHAR(20) NULL COMMENT 'SELF | KAKAO_UNLINK';
```

- 둘 다 **nullable**. 기존 사용자와 ACTIVE 사용자는 null이다.
- 컬럼 추가뿐이라 **롤백 시 DDL을 되돌릴 필요가 없다.**
- `meeting.status`는 `VARCHAR(30)`이므로 (D)가 `DISBANDED`를 추가할 때 **마이그레이션이 필요 없다.**

### 5-2. 상태 전이

```
ACTIVE ──탈퇴 API / 웹훅──▶ WITHDRAWN (원본 값 유지, withdrawn_at 기록)
                              │
                              └──7일 경과 + 스케줄러──▶ WITHDRAWN (익명화 완료)
```

`WITHDRAWAL_PENDING` 같은 중간 상태는 두지 않는다. `LoginPolicy`가 이미 login/reissue/카카오login 3경로에서 `WITHDRAWN`을 차단하므로 즉시 차단된다.

### 5-3. 익명화 규칙

| 컬럼 | 값 |
|---|---|
| `phone_number` | `wd_{id}` |
| `nickname` | `wd_{id}` |
| `email` | `NULL` (MySQL은 NULL 중복을 허용 — V18 주석) |

- 닉네임 검증 정규식이 `^(?:[가-힣]{2,10}|[A-Za-z]{2,20})$`, 전화번호는 숫자만 허용하므로 **`wd_` 값은 사용자가 만들 수 없다. 충돌 위험 0.**
- 익명화 완료 판정은 **`phone_number NOT LIKE 'wd\_%'`**로 한다. 단일 UPDATE라 부분 실패 상태가 생기지 않으므로 별도 `anonymized_at` 컬럼을 두지 않는다.
- `phone_number`가 `VARCHAR(20)`이므로 접두사는 짧게 유지한다(`wd_` = 3자).

### 5-4. Refresh Token

`RefreshTokenRepository.deleteByUser(User)` 를 추가해 **전량 삭제**한다.
로그아웃은 `revoke()`를 쓰지만 탈퇴는 계정 자체가 끝나므로 row를 남길 이유가 없다.
부수효과: 삭제된 토큰으로 재발급을 시도하면 `REVOKED_TOKEN`이 아니라 `INVALID_TOKEN`이 나온다(둘 다 401).

### 5-5. social_account 삭제 시점

| 경로 | 시점 |
|---|---|
| 탈퇴 API | **커밋 후 unlink 성공 시** 별도 트랜잭션에서 삭제 |
| 웹훅 | **계정 종료와 같은 트랜잭션**에서 삭제 (외부 호출이 없어 실패 여지가 없음) |

먼저 지우면 재처리에 필요한 `provider_user_id`가 사라지므로, 탈퇴 API 경로에서는 반드시 unlink 성공 뒤에 삭제한다.

---

## 6. 인증·인가 정책

### 6-1. 탈퇴 API

- 기존 JWT 인증. 별도 재인증 없음(R7).
- 본인 계정만 대상(`/users/me`)이므로 소유권 검증이 불필요하다.

### 6-2. 웹훅

- `SecurityConfig.PERMIT_ALL_PATHS`에 `/api/v1/webhooks/kakao/unlink` 추가.
  현재 `anyRequest().authenticated()`라 등록하지 않으면 차단된다. CSRF는 이미 disabled.
- `JwtAuthenticationFilter`는 non-Bearer 헤더를 그대로 통과시키므로 `KakaoAK` 헤더가 401을 유발하지 않는다(수정 불필요).
- 어드민 키 비교는 **`MessageDigest.isEqual`**로 타이밍 공격을 차단하고, **헤더 값을 로그에 남기지 않는다.**

### 6-3. 설정 주입

`KakaoProperties`에 `adminKey`, `appId` 2개를 추가한다.

- 둘 다 **필수**(`requireConfigured`) — 기존 필드와 동일한 관례.
- `adminKey`는 **길이 검증**(32자 이상 기준, 실제 카카오 키 길이 확인 후 확정)과 **`toString()` 마스킹** 대상.
- **로컬은 더미 값**을 쓴다. 검증이 "설정값과 헤더가 같은가"일 뿐이라 더미로도 웹훅 전체 흐름을 테스트할 수 있고, 실키는 운영 EC2의 `/etc/gather/gather.env`에만 존재한다.
  ⚠️ 길이 검증을 넣으므로 **더미도 32자 이상**이어야 한다.
- `appId`는 비밀값이 아니므로 마스킹 대상이 아니다.
- "없으면 검증을 건너뛴다"는 선택지는 **채택하지 않는다.** 운영에 빈 값이 나가는 순간 웹훅 인증이 조용히 무력화된다.

---

## 7. 트랜잭션 및 동시성 정책

### 7-1. 트랜잭션 경계

```
[탈퇴 API]
  @Transactional  ── status/withdrawn_at/withdrawal_reason 갱신
                  ── RefreshToken 전량 삭제
                  ── UserWithdrawnEvent 발행
  ─── 커밋 ───
  AFTER_COMMIT    ── (D) 도메인 정리 리스너 (현구님)
  커밋 후 호출     ── 카카오 unlink → 성공 시 social_account 삭제 (별도 트랜잭션)

[웹훅]
  @Transactional  ── 위와 동일 + social_account 삭제
  ─── 커밋 ───
  AFTER_COMMIT    ── (D) 도메인 정리 리스너 (현구님)
```

외부 API 호출은 트랜잭션 밖에서 수행하고, **카카오 장애가 나도 탈퇴는 성공 처리**한다.

### 7-2. 이벤트 계약 — 강현구님과의 접점

```java
public record UserWithdrawnEvent(Long userId) {}
```

`@TransactionalEventListener(phase = AFTER_COMMIT)`, **동기 실행**(프로젝트에 `@EnableAsync`가 없음).
리스너 실행 시간이 웹훅 응답 시간에 그대로 포함되므로 두 가지를 계약으로 못박는다.

> **① 리스너 안에서 외부 API를 호출하지 않는다.**
> **② 리스너는 예외를 밖으로 던지지 않는다.** try/catch로 감싸고 로그만 남긴다.
> `afterCommit`에서 던진 예외는 호출자에게 전파되므로, 이미 커밋된 탈퇴가 성공했는데 응답만 500이 나가고 웹훅 경로에서는 카카오가 실패로 인식한다.
> 선례: `ProfileImageDeletionListener`

승계·해체는 유예와 무관하게 **탈퇴 즉시** 실행한다(복구 기능이 없어 미룰 이유가 없다).

⚠️ 현구님께 전달할 함정: 모임장이 **`Meeting.host`(FK)와 `MeetingMember.role = HOST` 두 군데**에 표현돼 있다. 위임 시 둘 다 갱신하지 않으면 `MEETING_HOST_ONLY` 권한 체크와 화면 표시가 어긋난다. 승계 후보는 `meeting_member`에서 `status = APPROVED` + `joined_at` 오름차순 첫 번째.

### 7-3. 동시성

- 탈퇴 API 중복 호출 / 중복 웹훅 / 두 경로 동시 발생: 모두 **이미 WITHDRAWN이면 no-op**이므로 멱등하다.
- ~~별도 락을 두지 않는다.~~ → **구현에서 뒤집힘(2026-07-27).** no-op 판정은 순차 실행에서만 멱등하고, 동시 요청은 둘 다 `ACTIVE`를 읽어 `UserWithdrawnEvent`가 두 번 발행된다. 구독자의 모임장 승계 같은 처리가 중복 실행되므로 `AccountTerminationService`는 `UserRepository.findByIdForUpdate`로 상태 검사와 갱신 사이를 잠근다(`ProfileImageService` 선례).
- 스케줄러는 ShedLock이 없으므로 **단일 인스턴스 전제**다(프로젝트 전체 관례와 동일).

---

## 8. 실패 처리와 예외 코드

### 8-1. 신규 ErrorCode

```java
WITHDRAWN_PHONE_NUMBER_COOLDOWN(HttpStatus.CONFLICT, "탈퇴 후 7일간 재가입할 수 없습니다.")
```

`SignupValidator.validatePhoneNumberNotDuplicated()`에서 중복 대상이 `WITHDRAWN`이고 유예 기간 내이면 이 코드를 던진다. 이 메서드는 **일반 가입과 소셜 가입이 공유**하므로 한 곳만 고치면 양쪽에 적용된다. `UserRepository.findByPhoneNumber()` 추가가 필요하다.

닉네임은 전용 코드를 두지 않는다(바꾸면 그만이므로 `DUPLICATE_NICKNAME`으로 충분).

**정보 노출 트레이드오프(수용함)**: 타인의 번호로 "최근 7일 내 탈퇴했는지"를 조회할 수 있게 된다. 이미 가용성 API가 "사용 중인지"를 알려주고 있어 증분이 작고, 웹훅 강제 탈퇴자는 자신이 탈퇴된 사실조차 모르므로 명확한 안내가 CS 관점에서 훨씬 중요하다.

### 8-2. unlink 실패 처리

| 상황 | 처리 |
|---|---|
| 5xx · 타임아웃 · 네트워크 오류 | **일시 실패.** `social_account` row 유지 → 스케줄러가 재시도 |
| HTTP 400 + `code = -101` | 이미 연결 해제됨으로 간주하고 row 삭제 |
| 그 외 모든 오류 | 재시도 대상으로 row 유지 |
| 7일 경과해도 남은 row | **강제 삭제 + error 로그** (익명화 스케줄러가 함께 처리) |

⚠️ **구현 시점에 반드시 해결할 것 2건**

1. `HTTP 400`의 `code = -101`만 이미 연결 해제된 사용자로 확정한다. 401/403, 429, 5xx, `code = -1`, 본문 파싱 실패와 미분류 오류는 모두 재시도한다. 이 방식은 잘못된 관리 키나 카카오 내부 오류에서 복구용 `provider_user_id`를 보존한다.
2. **7일 강제 삭제 건은 카카오 쪽 연결이 남은 채 우리 기록만 사라질 수 있다.** 사용자 재가입 허용 시점은 배치와 무관하게 `withdrawnAt + 7일`이며, 배치는 개인정보 정리와 unlink 재시도의 안전망이다. 사용자가 카카오 설정에서 직접 끊어 보낸 웹훅은 WITHDRAWN 상태여도 local `social_account`를 삭제한다.

### 8-3. 로깅

- 웹훅 수신 시 `app_id`, `user_id`, `referrer_type`을 구조화 로그로 남긴다. **어드민 키 헤더는 절대 남기지 않는다.**
- 웹훅 처리 시간을 측정해 **임계치(1초) 초과 시 warn**. 3초를 넘기기 전에 조짐을 본다.
- 익명화·재처리 스케줄러는 `ProfileImageCleanupScheduler`와 동일하게 작업별 try/catch + 건수 로깅.

---

## 9. 구현 대상 파일과 변경 책임

### 신규

| 파일 | 내용 |
|---|---|
| `db/migration/V31__add_withdrawal_columns_to_users.sql` | 컬럼 2개 |
| `domain/auth/entity/WithdrawalReason.java` | `SELF`, `KAKAO_UNLINK` |
| `domain/auth/service/AccountTerminationService.java` | **계정 종료 코어.** 상태 전이 + 토큰 폐기 + 이벤트 발행 |
| `domain/auth/service/UserWithdrawnEvent.java` | `record UserWithdrawnEvent(Long userId)` |
| `domain/auth/service/AccountAnonymizer.java` | `wd_{id}` 생성·적용 규칙 |
| `domain/auth/scheduler/WithdrawnAccountScheduler.java` | 익명화 + unlink 재처리 |
| `domain/auth/service/KakaoUnlinkService.java` | 커밋 후 unlink 호출 + `social_account` 삭제 |
| `domain/auth/kakao/controller/KakaoUnlinkWebhookController.java` | 웹훅 엔드포인트 |
| `domain/auth/kakao/service/KakaoUnlinkWebhookService.java` | 어드민 키·app_id 검증 + 종료 위임 |

### 수정

| 파일 | 변경 |
|---|---|
| `domain/user/controller/UserController.java` | `DELETE` 매핑 추가 (`/api/v1/users/me`에 이미 매핑됨) |
| `domain/auth/entity/User.java` | `withdraw(reason, now)`, `anonymize(now)` 추가 |
| `domain/auth/repository/RefreshTokenRepository.java` | `deleteByUser` |
| `domain/auth/repository/UserRepository.java` | `findByPhoneNumber`, 익명화 대상 조회 |
| `domain/auth/repository/SocialAccountRepository.java` | userId 기반 조회 / 탈퇴자 잔존 row 조회 |
| `domain/auth/service/SignupValidator.java` | 쿨다운 분기 |
| `domain/auth/service/AuthService.java` | `checkPhoneNumberAvailability`에 `reason` 반영 |
| `domain/auth/dto/PhoneNumberAvailabilityResponse.java` | `reason` 필드 추가 |
| `domain/auth/kakao/config/KakaoProperties.java` | `adminKey`, `appId` + 길이 검증 + 마스킹 |
| `domain/auth/kakao/client/KakaoApiClient.java` | `unlink(providerUserId)` |
| `global/config/SecurityConfig.java` | 웹훅 경로 `permitAll` |
| `global/exception/ErrorCode.java` | `WITHDRAWN_PHONE_NUMBER_COOLDOWN` |
| `src/test/resources/application.yml` | **`kakao.admin-key`·`kakao.app-id` 더미 추가 (필수)** |

> ⚠️ **`KakaoProperties`가 기동 시점에 검증하므로, 테스트 설정에 더미를 추가하지 않으면 기존 `@SpringBootTest` 15개가 전부 컨텍스트 로딩에 실패한다.**

> 참고: `User` 엔티티가 아직 `auth` 패키지에 있어 탈퇴 API는 `user` 도메인 컨트롤러, 서비스는 `auth` 도메인으로 갈린다. 이번엔 현행 구조를 따르고, `user` 도메인 이관 리팩터링 때 함께 옮긴다.

### 담당 외 (강현구님)

`UserWithdrawnEvent` 구독자 — 모임장 자동 승계(최고참 `APPROVED` 멤버), 1인 모임 자동 해체(`MeetingStatus.DISBANDED` 추가), `meeting_member`/`posting_participation`/북마크 정리.

---

## 10. 테스트 시나리오

### 통합 테스트 (`@SpringBootTest`)

1. **탈퇴 전체 플로우** — 탈퇴 호출 → `status`/`withdrawn_at`/`withdrawal_reason` 확인, **여러 기기 로그인을 재현해 Refresh Token row가 전부 삭제**되는지, 쿠키 만료 헤더, 이벤트 발행
2. **웹훅 시큐리티** — 인증 없이 접근 가능한지(`permitAll`), 어드민 키 불일치 시 401, 정상 키로 200
   → 시큐리티 설정 실수 하나로 인증 없이 계정을 삭제할 수 있는 엔드포인트이므로 필수

⚠️ **`AFTER_COMMIT` 이벤트는 롤백되는 테스트에서 발화하지 않는다.** 이벤트 검증은 `@RecordApplicationEvents` 또는 커밋 허용 후 정리 방식이 필요하다(구현 시 선택).
⚠️ 통합 테스트에서도 카카오 unlink 실호출을 막아야 한다 — `MockRestServiceServer`(기존 `KakaoApiClientTest` 방식).

### 단위 테스트

- 익명화 값 생성 규칙, 이미 익명화된 계정 재처리 제외
- 7일 경계 판정 (경계값: 6일 23시간 / 정확히 7일 / 7일 1분)
- 웹훅 no-op 2종(`social_account` 없음, 이미 WITHDRAWN)
- `app_id` 불일치 → 200, 어드민 키 불일치 → 401
- `referrer_type`이 미지의 값이어도 200
- unlink 실패 분류(4xx/5xx)와 7일 강제 삭제
- 쿨다운 ErrorCode 분기 (일반·소셜 가입 양쪽)
- `KakaoProperties` 어드민 키 누락·길이 미달 시 기동 실패

로컬 DB는 더미 데이터 전제이므로 통합 테스트가 데이터를 건드려도 무방하다.

---

## 11. 배포 및 마이그레이션 고려사항

```
1. V28 알림 → V29/V30 배지 → V31 회원 탈퇴 순서로 통합한다.
2. V31 + 코드 배포 (엔드포인트가 살아있는 상태로)
3. 운영 EC2 /etc/gather/gather.env 에 KAKAO_ADMIN_KEY / KAKAO_APP_ID 주입 후 재기동
4. /health 및 curl 로 웹훅 엔드포인트 200 확인
5. ★ 마지막에 ★ 카카오 콘솔에 웹훅 URL 등록
   https://api.gathernow.kr/api/v1/webhooks/kakao/unlink
6. 테스트 카카오 계정으로 실제 연결 해제 1회 수행 → 운영 로그에서 수신 파라미터 육안 확인
```

- **콘솔 등록을 먼저 하면 안 된다.** 엔드포인트가 없는 상태의 웹훅은 404가 되고, **카카오는 재전송을 보장하지 않으므로 영구 유실**된다.
- **롤백 시 콘솔 등록도 함께 해제**해야 한다. 코드를 되돌려도 콘솔 설정은 남는다.
- V31은 nullable 컬럼 추가뿐이라 DDL 롤백이 불필요하다.
- 6번이 중요한 이유: dev에서 카카오가 실제로 보내는 요청을 받아볼 수 없어(R10) **파라미터 형식 오해가 있다면 운영에서 처음 드러난다.**

---

## 12. 보류된 사항과 잔여 위험

### 보류

| 항목 | 사유 |
|---|---|
| 모임장 탈퇴 차단(R6 반대안) | PM/기획 논의 대상. 뒤집혀도 탈퇴 API 진입부 검증 추가로 끝나며 코어·웹훅·DB는 재설계 불필요 |
| 계정 복구 기능 | 유예기간의 목적이 "충동 탈퇴 방지"라면 유예보다 복구가 정확한 답. 이번 범위 밖 |
| 카카오 앱 환경 분리 | 테스트 앱은 **비즈 앱만 생성 가능**(현재 Gather가 비즈 앱인지 확인 필요), 원본 앱당 5개. 일반 앱 추가 생성도 가능(계정당 10개 한도). **백엔드 코드 변경은 0줄** — 전부 환경변수 주입이라 값만 바꾸면 됨. 병목은 프론트 `client_id` 분리 + 콘솔 작업. 완료되면 dev 앱 웹훅 URL만 추가 등록하면 됨 |
| `JwtAuthenticationFilter`의 DB 조회 도입 | `SUSPENDED`까지 포함한 별도 논의 필요 |

### 잔여 위험

1. **Access Token 30분 잔존** — 탈퇴자가 최대 30분간 모임 가입·북마크·게시글 작성·프로필 수정이 가능해 데이터가 오염될 수 있다. 정지·탈퇴 공통의 알려진 제약으로 수용한다. 모임장 권한은 승계로 `host_id`가 바뀌면 자연히 막히지만 경합 구간은 남는다.
2. **웹훅 응답 시간의 제어권이 (D) 리스너에 있다** — 느려지면 3초를 넘기고 웹훅이 유실된다. 7-2의 계약 2건 + 1초 warn 로깅으로 완화한다.
3. **웹훅 유실 시 복구 수단이 없다** — 카카오가 재전송을 보장하지 않는다. 대사(reconciliation) 수단으로 카카오 "사용자 목록 가져오기" 어드민 API 활용 가능성이 있으나 **미확인**이다.
4. **실수로 연결 해제한 사용자가 7일간 락아웃된다** — 본인 의사와 무관한 탈퇴에 유예가 페널티로 작동한다. `withdrawal_reason` 컬럼이 있으므로 나중에 경로별로 유예를 다르게 가져갈 수 있다.
5. **7일간 개인정보가 평문으로 보관된다** — 지연 익명화의 대가. 보관 기간 관점에서 PM/법무 확인이 필요할 수 있다.
6. **탈퇴자가 (D) 이전까지 화면에 노출된다** — 지연 익명화라 7일간은 실제 닉네임이 그대로 남는다. "탈퇴한 사용자" 표시는 `wd_` 접두사가 아니라 **`status` 기반**이어야 한다.
7. **카카오 unlink 오류 코드 미확인** — 8-2 참조.

---

## 13. 단계별 작업 계획

| 단계 | 내용 | 검증 |
|---|---|---|
| 0 | develop 최신화, V31 확보 | V28 알림 → V29/V30 배지 → V31 순서 확인 |
| 1 | V31 + `User.withdraw()`/`anonymize()` + `WithdrawalReason` | 엔티티 단위 테스트, `ddl-auto=validate` 부팅 성공 |
| 2 | `AccountTerminationService` + `UserWithdrawnEvent` + `RefreshTokenRepository.deleteByUser` | 단위 테스트 — 상태 전이·토큰 전량 삭제·이벤트 발행 |
| 3 | `DELETE /api/v1/users/me` + 쿠키 만료 | **통합 테스트 1** 통과 |
| 4 | `KakaoProperties` 확장 + 테스트 yml 더미 + `KakaoApiClient.unlink` | 기존 `@SpringBootTest` 15개 정상 기동, `MockRestServiceServer` 테스트 |
| 5 | `KakaoUnlinkService` — 커밋 후 호출 + `social_account` 삭제 | 4xx/5xx 분류 단위 테스트 |
| 6 | 웹훅 컨트롤러 + 서비스 + `SecurityConfig` | **통합 테스트 2** 통과, no-op 2종 단위 테스트 |
| 7 | `WithdrawnAccountScheduler` — 익명화 + 재처리 + 7일 강제 정리 | 경계값 단위 테스트 |
| 8 | 쿨다운 ErrorCode + `SignupValidator` + availability `reason` | 일반·소셜 양쪽 단위 테스트 |
| 9 | 전체 테스트 + 문서(OpenAPI) 갱신 | `./gradlew test` 전체 통과 |

1~3단계까지가 **계정 종료 코어**이므로, 여기까지 먼저 머지하면 (D) 작업이 이벤트를 구독해 병렬로 진행할 수 있다.

---

## 프론트 전달 사항

1. `DELETE /api/v1/users/me` — **요청 본문 없음**, `Authorization: Bearer` 헤더만
2. 재인증이 없으므로 **확인 모달이 유일한 오조작 방어선**. 문구에 "되돌릴 수 없음"과 "탈퇴 후 7일간 재가입 불가"를 포함할 것
3. 탈퇴 성공 시 **로컬 Access Token을 즉시 폐기하고 로그아웃 상태로 전환**할 것. 서버가 무효화할 수 없어 안 지우면 최대 30분간 앱이 계속 동작함
4. Refresh Token 쿠키는 **서버가 `Set-Cookie`로 만료**시킴. 프론트 조치 불필요
5. 가입 화면에 **`WITHDRAWN_PHONE_NUMBER_COOLDOWN` 분기** 필요. **일반·카카오 가입 양쪽**
6. `/phone-numbers/availability` 응답에 **`reason` 필드 추가**(`null` / `IN_USE` / `WITHDRAWN_COOLDOWN`)
7. 카카오에서 연결을 해제한 사용자는 다음 로그인 시 **신규 가입 플로우**로 진입. 7일 이내면 5번 에러를 만남
8. 탈퇴자 표기 문자열 협의 필요 — **`status` 기반**으로 판별해야 함(7일간은 원래 닉네임이 남음)

---

## 확인 필요 항목

- [ ] 현재 Gather 카카오 앱이 **비즈 앱인지 일반 앱인지** (테스트 앱 사용 가능 여부가 갈림)
- [ ] 카카오 앱 생성일이 **2018-09-19 이전인지** — 이전이면 [카카오 로그인] > [고급] > [사용자 아이디 고정] 활성화 필요(연결 해제 후 회원번호 변경 방지)
- [ ] 카카오 unlink API의 "이미 연결 해제됨" 오류 코드 — 미확인. 8-2대로 보수적으로 구현했다(5xx와 429만 재시도, 나머지 4xx는 영구 실패).
- [x] 대표 어드민 키의 실제 길이 — **32자**(2026-07-27 콘솔 확인). `KakaoProperties`는 "32자 이상"이 아니라 **정확히 32자**를 요구한다. 하한만 두면 긴 자리표시자가 그대로 통과해 검증 의도가 무력화된다.
- [x] 통합 순서 확정 — V28 알림, V29/V30 배지, V31 회원 탈퇴.
- [x] 일반 가입은 만료되지 않은 이메일 인증만 사용하고, 가입 성공 시 인증 row를 소비한다.

## 후속 보완 사항

- 카카오 로그인에서 탈퇴 계정의 stale `social_account`를 발견하면 해당 row와 User를 잠근 뒤 `withdrawnAt + 7일`을 판정한다. 7일 미만은 기존 탈퇴 계정 오류를 반환하고, 7일 이상이면 stale row를 삭제한 뒤 신규 가입 토큰 흐름으로 전환한다. 일일 unlink 재시도 배치는 재가입 허용 시점을 결정하지 않는 안전망이다.
- 일반 회원가입은 `verified == true`이고 아직 만료되지 않은 이메일 인증만 허용한다. 가입에 성공하면 해당 인증 row를 같은 트랜잭션에서 삭제하며, 일반 계정 탈퇴 시에도 기존 이메일의 인증 row를 정리한다.
- unlink 재시도는 `social_account.id` 오름차순 keyset 순회로 실행한다. 한 실행의 대상이 100건을 초과해도 다음 페이지를 처리하고, 재시도 대기 row는 같은 실행에서 반복 호출하지 않는다.
