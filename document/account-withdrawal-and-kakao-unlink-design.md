# 계정 탈퇴 및 카카오 연결 해제 설계

- 검증 기준일: 2026-08-02
- 기준 브랜치: `feature/auth-account-withdrawal-api`
- 구현 기준: PR 4~7 회원 탈퇴·카카오 연결 해제 흐름 구현 완료
- 현재 단계: PR 7 공개 회원 탈퇴 API 구현 완료, `develop` 병합 전 리뷰 단계
- 조사 시점 HEAD: `9643c93`
- 공식 문서 확인일: 2026-07-30

이 문서의 문장은 다음 네 가지 근거 수준으로 구분한다.

- `[공식 계약]`: 카카오 공식 문서가 직접 규정하는 외부 계약
- `[Gather 확정 정책]`: 현재 코드·적용 스키마 또는 이번 팀 결정으로 확정된 Gather의 계약
- `[권장 구현]`: 확정 정책을 안전하게 구현하기 위한 기술 권장사항
- `[운영·법적 검토]`: 기술 구현은 진행할 수 있지만 운영 공개 전 별도 검토가 필요한 사항

## PR 4~7 구현 반영 상태 (2026-08-02)

- V41 migration에 직접 provider 식별자 nullable 전환, `retry_cycle`, DB singleton worker control을 반영했다.
- application 공통 Clock은 UTC로 고정했으며 claim·reservation·result 단계가 각 operation별 now를 한 번만 사용한다.
- `claim → preflight → attempt reservation → transaction 밖 HTTP → result/finalizer` 경계와 lock order, token·lease·generation·retry cycle fencing을 구현했다.
- Retry-After 두 형식, full jitter, reservation 최대 12회, 6시간 상한을 구현했다.
- configuration 오류는 현재 task를 DEAD로 만들고 DB control을 차단하며, 명시적 내부 resume만 새 retry cycle을 시작한다.
- 동일 generation의 이미 UNLINKED인 계정은 reservation과 HTTP 없이 local finalization한다.
- 성공 finalizer는 직접 provider 식별자를 파기하고 HMAC을 유지하며 User 탈퇴·익명화·프로필 이미지 durable deletion 등록·task 성공을 하나의 transaction으로 처리한다.
- MySQL 8.4 fresh migration, `ddl-auto=validate`, 실제 DB claim 동시성 및 전체 회귀 테스트로 구현 계약을 검증했다.
- PR 7은 body 없는 `DELETE /api/v1/users/me`, `WithdrawalReason.SELF`, `COMPLETED → 200`과 `ACCEPTED → 202`, UTC `occurredAt` 응답을 구현했다.
- 모든 신규·멱등 성공은 Refresh Cookie를 만료하며, `WITHDRAWAL_PENDING`과 `WITHDRAWN` 사용자는 정확한 탈퇴 DELETE만 재호출할 수 있다.
- 공개 API의 Springdoc/Swagger, 프론트 API 가이드와 Controller·API·Security 통합 테스트를 반영했으며 신규 migration은 없다.

## 1. 문서 목적

이 문서는 Gather 회원 탈퇴와 카카오 연결 해제를 하나의 동기 HTTP 요청으로 취급하지 않고, 다음 조건을 함께 만족하는 내구성 있는 처리 흐름으로 정의한다.

1. 탈퇴 요청이 수락되는 즉시 서비스 접근을 차단한다.
2. 카카오 Admin 연결 해제를 재시도 가능한 durable task로 기록한다.
3. 외부 HTTP 호출은 데이터베이스 트랜잭션 밖에서 수행한다.
4. 재연결과 과거 task의 경쟁 상태를 `generation`으로 차단한다.
5. 카카오 연결 해제가 완료된 뒤 서비스 탈퇴를 확정하고 개인정보를 파기한다.
6. 중복 요청, worker 중단, lease 만료, webhook 도착에도 멱등성을 유지한다.

이 문서는 PR 4~7의 구현 결과와 PR 8의 별도 구현 경계를 기록하며, 이번 문서 정정은 새로운 기능 구현을 포함하지 않는다.

## 2. 현재 구현 상태

### 2.1 구현 완료

| 영역 | 현재 상태 |
|---|---|
| 사용자 탈퇴 기반 | `UserStatus`는 `ACTIVE`, `SUSPENDED`, `WITHDRAWAL_PENDING`, `WITHDRAWN`을 제공한다. `User.requestWithdrawal()`, `withdraw()`, `anonymize()`가 존재한다. |
| 사용자 익명화 | 이름, 생년월일, 성별, 이메일, 비밀번호, 소개, 프로필 이미지 키, 활동 지역, 관심 카테고리 등을 제거하고 전화번호·닉네임을 대체값으로 변경한다. |
| 소셜 계정 식별자 보호 | `SocialAccount`는 HMAC 조회 키, AES-GCM 암호문, 키 버전, legacy 평문 식별자를 보관한다. |
| 소셜 계정 상태와 세대 | `LINKED`, `UNLINK_PENDING`, `UNLINKED`, `generation`, 낙관적 잠금 버전을 제공한다. `relink()`는 `generation`을 증가시킨다. |
| 가입 세션 | `SocialSignupSession`은 DB에 영속화되며 `PENDING`, `CONSUMED`, `CANCELLED` 상태를 가진다. 토큰은 opaque token의 SHA-256 해시로 조회한다. |
| 가입 세션 동시성 | 동일 identity의 pending 세션을 ID 순서로 비관적 잠금하고, 선택한 세션 소비와 나머지 세션 취소를 한 트랜잭션에서 처리한다. |
| 탈퇴 접수 기반 | `WITHDRAWAL_PENDING`, `AccountTerminationService`, 일반 회원 동기 완료, 카카오 회원 pending 접수와 `KakaoUnlinkTask` 저장이 구현됐다. |
| 카카오 unlink worker | DB UTC claim·lease, preflight, attempt reservation, transaction 밖 Admin HTTP 호출, retry·terminal 분류와 DB 기반 configuration circuit breaker가 구현됐다. |
| worker finalizer | unlink 성공 또는 동일 generation `UNLINKED` local finalization에서 SocialAccount 직접 식별자 제거, User `WITHDRAWN`·익명화, 프로필 이미지 durable deletion 등록과 task `SUCCEEDED` 전이가 구현됐다. |
| 재가입 제한 | `AccountRejoinBlock`의 `PHONE`/`KAKAO` 7일 생성·연장과 가입 시 조회가 실제 인증 흐름에 연결됐다. |
| 재가입 제한 보관기간 | 탈퇴 완료(`users.withdrawn_at`) 후 3 calendar months가 지나고 차단도 끝난 row를 기동 직후와 1시간 주기 cleanup으로 물리 삭제한다. scheduler는 기본 비활성이며 운영 데이터 점검 후 환경변수로 활성화한다. |
| PHONE 동시성 | `account_identity_guard`와 `AccountIdentityGuardService`가 가입·탈퇴에 동일 PHONE HMAC row의 안정적인 비관적 잠금을 제공한다. |
| 인증 차단 | `JwtAuthenticationFilter`가 유효한 Access Token 요청마다 User를 PK로 조회해 최신 탈퇴 상태를 검사한다. |
| 공개 탈퇴 API | body 없는 `DELETE /api/v1/users/me`가 `WithdrawalReason.SELF`로 기존 service를 호출하고 `COMPLETED/ACCEPTED`를 `200/202`로 반환한다. `occurredAt`은 UTC `Instant`로 노출한다. |
| HTTP 멱등성과 Cookie | pending/withdrawn 사용자의 정확한 탈퇴 DELETE만 재호출할 수 있고, 모든 신규·멱등 성공 응답에서 기존 Refresh Cookie를 만료한다. |
| API 문서와 검증 | Springdoc/Swagger, 프론트 API 가이드, Controller·실제 DB API·Security 통합 테스트가 PR 7 계약을 반영한다. |
| PR 5 migration | `V39__add_withdrawal_pending_and_create_kakao_unlink_task.sql`에 사용자 상태, `account_identity_guard`, `kakao_unlink_task` DDL이 포함됐다. |
| 비동기 정리 선례 | 프로필 이미지 정리용 after-commit listener, retry row, scheduler가 존재한다. |

### 2.2 구현 완료 후 남은 운영·후속 영역

| 영역 | 현재 한계 |
|---|---|
| `User.anonymize()` | 개별 사용자 필드는 정리하지만 관련 도메인 데이터 전체의 개인정보 파기 범위는 정의하지 않는다. |
| `SocialAccount.relink()` | 세대 증가 기능은 있으나 실제 재연결 흐름이 아직 없다. |
| 재가입 제한 정리 | block 보관기간 cleanup은 구현했으나, 과거 upsert로 `sourceUserId`가 최초 탈퇴자에 머문 legacy row는 자동 보정하지 않는다. identity guard의 장기 retention·안전한 cleanup도 아직 구현하지 않았다. |
| 운영 활성화 | 운영 환경변수, 운영 DB의 V39/V41, WorkerControl singleton과 기존 backlog를 확인한 뒤 Admin client와 worker를 단계적으로 활성화해야 한다. |
| 운영 관측 | 구조화 로그는 존재하지만 backlog·`DEAD`·pending 체류시간 metric과 alert는 아직 구현하지 않았다. |
| 운영 복구 | one-shot command는 configuration 원인의 전체 `DEAD` task 복구만 지원한다. 비-configuration `DEAD`의 감사 가능한 내부 복구 수단은 별도 후속 작업이다. |

### 2.3 운영 준비·별도 후속·의도적 범위 밖

기능 구현은 사용자 요청 접수, 일반 회원 동기 탈퇴, 카카오 비동기 unlink와 finalizer, 공개 HTTP API와 멱등성까지 완료됐다. 남은 항목은 다음처럼 구분한다.

- 운영 준비: 운영 환경변수 최종 검증, 운영 DB V39/V41과 WorkerControl singleton 확인, 기존 backlog 확인, Admin client와 worker 단계 활성화
- 별도 후속 PR: backlog·`DEAD`·pending 체류시간 metric/alert, 비-configuration `DEAD` 내부 복구 수단, retention cleanup, HMAC/AES keyring·rotation, 타 도메인 개인정보 보존·익명화 정책
- 의도적 범위 밖: 탈퇴 완료 polling API, 탈퇴 취소, 공개 관리자 retry API, 외부 카카오 unlink webhook, relink, 다중 provider 확장

### 2.4 오래된 문서와의 불일치

이 문서의 이전 버전에 있던 다음 내용은 현재 코드 또는 공식 계약과 일치하지 않는다.

- `SocialAccount` 행 자체를 retry queue로 사용하는 설계
- 카카오 4xx 응답이면 원인과 관계없이 `SocialAccount`를 삭제하는 설계
- after-commit 이벤트 발행만으로 durable enqueue를 대신하는 설계
- task 없이 탈퇴 API가 카카오 unlink를 직접 호출하는 설계
- 데이터베이스 트랜잭션 안에서 외부 HTTP를 호출하는 설계
- task 완료 시 `generation`을 검증하지 않는 설계
- 가입 세션을 조건 없는 bulk update로 취소하는 설계
- 가입 토큰을 JWT로 가정한 설명
- 다음 migration을 `V29`로 가정한 설명
- PR 6 시점에 존재하지 않던 `UserWithdrawnEvent`와 공개 탈퇴 API가 이미 구현됐다고 적은 과거 설명. 공개 탈퇴 API 자체는 현재 PR 7에서 구현됐다.
- 카카오 연결 해제 전에 Gather 탈퇴와 개인정보 파기를 먼저 완료하는 설명

## 3. 카카오 공식 계약

| 항목 | 공식 규격 | Gather 적용 | 공식 문서 |
|---|---|---|---|
| 연결 해제 endpoint | `POST https://kapi.kakao.com/v1/user/unlink` | Admin 전용 client가 호출한다. | [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api) |
| 인증 | `Authorization: KakaoAK ${SERVICE_APP_ADMIN_KEY}` | REST API key나 사용자 access token과 혼용하지 않는다. | [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api) |
| 요청 형식 | `application/x-www-form-urlencoded;charset=utf-8` | `target_id_type=user_id`, `target_id=<카카오 회원번호>`를 전송한다. `app_id`는 unlink 요청 파라미터가 아니다. | [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api) |
| 응답 | 성공 시 연결 해제된 회원번호 `id`를 반환한다. | 복호화한 요청 대상과 응답 `id`가 같은지 검증한다. | [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api) |
| 탈퇴 순서 | 서비스 탈퇴 과정에 연결 해제를 포함하며, 서비스가 unlink 성공 응답을 받은 뒤 회원 탈퇴를 처리하는 흐름을 제시한다. | `WITHDRAWAL_PENDING`에서 unlink를 먼저 완료하고 `WITHDRAWN`을 확정하는 모델을 권고한다. | [카카오 로그인 이해하기](https://developers.kakao.com/docs/ko/kakaologin/common) |
| unlink 효과 | 사용자 동의와 발급된 access/refresh token이 해제·만료된다. | Gather가 발급한 JWT/refresh token 폐기는 별도로 수행한다. | [카카오 로그인 이해하기](https://developers.kakao.com/docs/ko/kakaologin/common) |
| 개인정보 | 탈퇴 시 개인정보를 복구할 수 없게 파기해야 하며, 탈퇴 후 보관에는 사용자 동의 등 적법한 근거가 필요하다. 카카오 회원번호도 개인정보에 해당한다. | unlink 완료 후 provider ID 평문·암호문 등 식별정보 파기를 finalizer의 필수 단계로 둔다. 재가입 제한용 파생값 보관은 별도 법무 결정이 필요하다. | [카카오 로그인 이해하기](https://developers.kakao.com/docs/ko/kakaologin/common) |
| unlink webhook | 외부 unlink, 미완료 연결, 카카오계정 탈퇴 등의 경로를 서비스에 알린다. GET/POST를 지원하고 3초 안에 `200` 응답이 필요하다. 서비스가 unlink API를 직접 호출한 경우에는 전송되지 않는다. | PR 8에서 별도 inbound 경로로 구현하며 outbound task 성공 통지로 기대하지 않는다. | [카카오 로그인 콜백](https://developers.kakao.com/docs/ko/kakaologin/callback) |
| webhook 필드 | `app_id`, `user_id`, `referrer_type` 등이 전달되며 primary Admin key 검증 규칙을 따른다. | app 식별과 요청 검증, 멱등 처리를 구현한다. | [카카오 로그인 콜백](https://developers.kakao.com/docs/ko/kakaologin/callback) |
| 오류 형식 | REST API 오류 본문은 `code`, `msg`를 제공한다. `msg`는 변경될 수 있으므로 `code`로 분기해야 한다. | HTTP status만으로 삭제·성공을 결정하지 않고 typed result로 변환한다. | [REST API 공통 참고사항](https://developers.kakao.com/docs/ko/rest-api/reference) |
| Admin key 권한 | Admin key별 API 카테고리를 선택할 수 있으며 연결 해제 권한이 활성화되어야 한다. | 배포 전 콘솔 체크리스트에 unlink 권한 확인을 포함한다. | [Admin key API](https://developers.kakao.com/docs/ko/reference/admin-key-api) |

`[공식 계약]` 위 표의 계약은 2026-07-30에 확인했다. 구현 직전과 배포 직전에 공식 문서를 다시 확인한다.

## 4. 핵심 설계 원칙

1. `[Gather 확정 정책]` 탈퇴 접수와 durable task enqueue는 하나의 원자적 트랜잭션이다.
2. `[Gather 확정 정책]` worker의 claim, attempt reservation과 결과 반영은 각각 짧은 트랜잭션이며 카카오 HTTP 호출은 reservation commit 뒤 결과 트랜잭션 전까지 DB 트랜잭션 밖에서 실행한다.
3. `[Gather 확정 정책]` `KakaoUnlinkTask`가 retry와 운영 이력의 유일한 source of truth다. `SocialAccount`는 계정 상태를 표현할 뿐 queue가 아니다.
4. `[Gather 확정 정책]` worker는 at-least-once 실행을 전제로 멱등하게 동작한다.
5. `[Gather 확정 정책]` 모든 task는 생성 당시 `SocialAccount.generation`을 캡처한다. generation mismatch는 호출하지 않고 `STALE`로 종료한다. 같은 generation의 `UNLINK_PENDING`만 외부 호출하고, 같은 generation의 `UNLINKED`와 pending User는 HTTP 없는 local finalization으로 수렴시킨다. 결과 반영 직전 잠금 아래 다시 검증하며 claim 소유권이 없으면 task를 변경하지 않고 실행을 중단한다.
6. `[Gather 확정 정책]` 미분류 4xx나 파싱 실패를 성공으로 간주하지 않는다.
7. `[Gather 확정 정책]` pending/withdrawn 사용자는 새 로그인과 이미 발급된 access token의 보호 API 접근을 차단한다. 단, 유효한 기존 access token으로 정확히 `DELETE /api/v1/users/me`를 재호출하는 경우에만 서비스 멱등 결과에 도달하도록 상태 접근 정책에서 예외 처리한다.
8. `[권장 구현]` 로그에는 Admin key, 복호화한 카카오 회원번호, 암호문, 사용자 토큰, 응답 원문을 남기지 않는다.
9. `[권장 구현]` 법적 파기 의무와 재가입 제한은 별개의 목적으로 설계한다.
10. `[권장 구현]` 기존 legacy 호환 범위를 제외하고 raw provider user ID를 새 컬럼, task, 로그에 평문으로 추가 저장하지 않는다.

### 4.1 확정 정책 요약

| 정책 | 확정 내용 | 해결하는 문제 | 권장 구현 위치 | 주요 transaction·동시성 요구사항 | 필수 테스트 | 담당 PR |
|---|---|---|---|---|---|---|
| 회원 유형별 탈퇴 상태 | `[Gather 확정 정책]` 일반 회원은 `ACTIVE/SUSPENDED → WITHDRAWN`, 카카오 `LINKED` 회원만 `WITHDRAWAL_PENDING → WITHDRAWN`을 사용한다. | 외부 unlink가 없는 일반 회원을 불필요하게 pending에 두지 않는다. | `User`, `AccountTerminationService`, worker finalizer | 일반 회원은 접수 트랜잭션에서 완료, 카카오 회원은 task와 원자적으로 접수 | 일반·카카오 두 시작 상태, 중복 요청, `DEAD` 시 카카오 pending 유지 | 주 PR 5, 검증 PR 6·7 |
| 회원 유형별 API 응답 | `[Gather 확정 정책]` `COMPLETED`는 `200`, `ACCEPTED`는 `202`와 `ApiResponse<AccountTerminationResponse>`로 반환한다. | 완료와 비동기 진행을 응답에서 구분한다. | `UserController`, service result, 응답 DTO, OpenAPI | 응답 전 각 유형의 접수/완료 트랜잭션 커밋 | 일반 `200`, 카카오 `202`, 중복 상태별 동일 응답 | PR 7 |
| pending 사용자 접근 차단 | `[Gather 확정 정책]` 로그인·가입 세션·relink·새 인증 세션을 모두 차단한다. | 탈퇴 접수 직후 서비스 재진입을 막는다. | 중앙 인증 경로, 카카오 로그인·가입 세션 경계 | 개별 Controller 검사 금지, 가입 제출 트랜잭션에서도 재검증 | 일반·카카오 로그인, 세션 발급·제출 차단 | 주 PR 5, API 검증 PR 7 |
| Refresh Token 전체 폐기 | `[Gather 확정 정책]` 접수 트랜잭션에서 사용자 전체 refresh token을 삭제한다. | 다른 기기의 로그인 세션이 남는 것을 막는다. | `RefreshTokenRepository`, `AccountTerminationService` | 사용자 상태·task enqueue와 같은 트랜잭션, 실패 시 전체 rollback | 전량 삭제, 삭제 실패 rollback, 재발급 실패 | PR 5 |
| 기존 Access Token 중앙 차단 | `[Gather 확정 정책]` JWT 자체가 유효해도 pending/withdrawn이면 인증을 거부하되, 인증된 `DELETE /api/v1/users/me`만 멱등 재호출을 허용한다. | access token 잔여 유효기간의 일반 보호 API 접근을 막으면서 HTTP 멱등성을 제공한다. | JWT 인증 필터 또는 동등한 중앙 Security 경로 | 서명·만료 검증과 User 조회를 마친 뒤 method·path가 정확히 일치할 때만 상태 정책 예외 | ACTIVE DELETE 성공, pending/withdrawn DELETE 성공, 그 밖의 보호 API 실패 | 주 PR 5, 검증 PR 7 |
| relink 금지 | `[Gather 확정 정책]` PR 4~8에서는 relink API·service 흐름을 구현하지 않는다. | 과거 generation task가 새 연결까지 해제하는 race를 막는다. | 인증·소셜 계정 application service 경계 | `UNLINK_PENDING/UNLINKED` 연결 대상 제외, block 유효 중 금지 | non-LINKED 로그인·가입·relink 거부 | PR 5~8 공통 |
| generation mismatch → `STALE` | `[Gather 확정 정책]` generation mismatch는 API 미호출 후 `STALE`로 종료하되 같은 generation의 `UNLINKED`와 pending User는 local finalization한다. | 오래된 task의 외부 부작용을 방지하면서 이미 완료된 unlink의 로컬 후처리를 복구한다. | worker preflight와 result finalizer | 호출 전·결과 저장 시 이중 검증, 자동 retry 금지, claim 미소유자는 쓰기 금지 | mismatch API 미호출, `UNLINKED` HTTP 0회, claim 오류 실행 중단 | PR 6 |
| 지수 backoff + full jitter | `[Gather 확정 정책]` 기본 1분, 지수 증가, 6시간 상한이며 자동 retry cycle당 attempt reservation은 최대 12회다. | 일시 장애의 동시 재시도 폭주와 crash window의 호출 상한 초과를 막는다. | reservation service, retry policy와 `nextAttemptAt` 계산기 | HTTP 직전 reservation에서 `attempt_count` 증가, 조건부 `Retry-After` 반영 | jitter 범위, 상한, reservation crash, 12회 소진 | PR 6 |
| DEAD 자동 재시도 금지 | `[Gather 확정 정책]` scheduler는 `DEAD`를 claim하지 않으며 사용자와 소셜 계정은 pending 상태를 유지한다. 설정 오류는 현재 task를 `DEAD`로 만들고 DB 전역 worker control을 `CONFIGURATION_BLOCKED`로 전환한다. | 완료되지 않은 unlink를 성공으로 위장하거나 전역 설정 오류로 backlog를 연속 소진하지 않는다. | task result handler, worker control, claim query, alert | terminal task와 전역 circuit breaker 원자 전이, 운영 retry cycle만 명시적 resume | 자동 claim 금지, batch 중단, 다중 인스턴스 차단, sanitize 저장 | PR 6 |
| AccountTermination·enqueue 원자성 | `[Gather 확정 정책]` 접수 부수효과와 task insert를 하나의 트랜잭션으로 묶는다. | task 없는 pending 상태와 고아 task를 막는다. | `AccountTerminationService`, enqueue service | outer `REQUIRED`; enqueue는 동일 트랜잭션 참여, `REQUIRES_NEW` 금지 | block/session/task 실패 rollback과 task·상태 원자성 | PR 5 |
| HTTP 중 DB transaction 금지 | `[Gather 확정 정책]` claim commit 후 transaction 없이 호출하고 별도 결과 트랜잭션에서 반영한다. | 외부 timeout 중 lock·connection 점유와 장애 전파를 막는다. | worker orchestration | claim token, lease, `claimedBy`, 결과 시 소유권·generation 재검증 | 호출 중 transaction 비활성, lease 회수, 동시 claim | PR 6 |
| PR 3 가입 세션 계약 유지 | `[Gather 확정 정책]` 같은 identity의 pending 세션을 ID 오름차순으로 비관적 잠금하고 `cancel()`한다. | 가입·탈퇴 race와 persistence context 불일치를 막는다. | `SocialSignupSessionService`의 service-level 진입점 | outer 접수 트랜잭션 참여, bulk update·잠금 순서 변경 금지 | ID 순서 잠금, cancel 실패 rollback, 가입 경쟁 | PR 5 |
| PHONE identity 직렬화 | `[Gather 확정 정책]` 가입과 탈퇴는 전화번호별로 동일한 `account_identity_guard` row를 잠근다. | User 전화번호 익명화와 block 확인 사이의 재가입 우회 경쟁을 막는다. | `AccountIdentityGuardService` | active HMAC identity upsert 후 `FOR UPDATE`, 기존 application transaction 참여 | 가입 선점·탈퇴 선점 두 순서, rollback | PR 5 |
| 재가입 제한 | `[Gather 확정 정책]` PHONE과 KAKAO 모두 7일이며 `now < expiresAt`만 차단한다. 활성 PHONE block이 있는 identity에 신규 ACTIVE User를 만들지 않는다. | cleanup 지연과 무관하게 동일한 cooldown 경계를 보장한다. | `AccountRejoinBlock` 생성·locking read·cleanup | PHONE guard 안에서 최종 block 조회·중복 검사·User flush, 기존 block은 `max(existing.expiresAt, now+7일)` | 정확히 7일, 경계 허용, 연장, cleanup 지연, 가입·탈퇴 경쟁 | PR 5 |
| 식별자 생명주기 | `[Gather 확정 정책]` unlink 성공 시 복구 가능한 직접 identifier를 제거하고 HMAC과 key version은 cooldown 동안 유지한 뒤 후속 retention PR에서 제거한다. | 재처리 가능성, 재가입 제한과 개인정보 최소화를 함께 만족한다. | worker finalizer, 후속 retention cleanup | task에는 식별자 복제 금지, 결과 트랜잭션에서 직접 식별자 즉시 파기 | 성공·local finalization·STALE·미해결 DEAD·HMAC 유지 | PR 6·후속 retention |
| 운영 기본값 | `[Gather 확정 정책]` connect 2초/read 5초, scheduler 30초, batch 10, concurrency 1, lease 120초다. | 단일 EC2의 낮은 처리량에서 예측 가능한 복구와 자원 사용을 제공한다. | Admin/worker configuration properties | client 내부 retry 없음, UTC, `SKIP LOCKED` | 값 바인딩, timeout, lease 회수, 중복 claim 방지 | PR 4·6 |

### 4.2 최종 기술·운영 정책 연결표

| 정책 | 적용 이유·상태 전이 | 권장 구현 | 트랜잭션·동시성 | 보안·개인정보 | 필수 테스트 | 담당 PR |
|---|---|---|---|---|---|---|
| 일반 회원 동기 완료 | 외부 unlink 없음; `ACTIVE/SUSPENDED → WITHDRAWN`, `200` | 유형 판별 service result, 즉시 익명화·durable 후처리 | User→PHONE guard→PHONE block 순서의 단일 트랜잭션 | 원문 제거, User tombstone 유지 | 두 시작 상태, 중복 `200`, rollback | PR 5·7 |
| 카카오 회원 비동기 완료 | `ACTIVE/SUSPENDED → PENDING → WITHDRAWN`, 접수 `202` | task enqueue와 worker finalizer | 접수 원자성, claim/call/result 분리 | pending 중 ciphertext 최소 유지 | 두 시작 상태, 중복 `202`, finalizer | PR 5·6·7 |
| 재가입 7일 | PHONE/KAKAO 동일 cooldown, `now >= expiresAt` 허용 | 고정된 active HMAC, UTC Clock, max 연장 | PHONE guard·locking read·멱등 block upsert | 원문/HMAC 전체 로그 금지 | 경계·연장·cleanup 지연·가입 경쟁 | PR 5 |
| SocialAccount lifecycle | unlink 성공 또는 동일 generation의 기존 `UNLINKED` local finalization 시 직접 ID 제거, HMAC은 cooldown 동안 유지 | nullable migration, local finalizer와 최소 tombstone | 같은 result/finalizer transaction에서 직접 식별자 파기 | task에 ID/ciphertext 복제 금지 | 상태별 직접 식별자 제거와 HMAC 유지 | PR 6·후속 retention |
| DEAD ciphertext | 미해결 동안 유지, 해결 성공 시 제거 | SLA와 application-service manual retry | `DEAD` 자동 claim 금지 | retention 만료 자동 파기 금지 | 유지·해결 후 제거·민감 로그 부재 | PR 6·후속 운영 |
| User 익명화 | 일반 즉시, 카카오 unlink 성공 시 | 필드별 null/고유 익명값과 S3 durable 삭제 | 상태 전이와 같은 트랜잭션 | 가역 접두어 익명화 금지 | nullable·unique·관계·S3 retry | PR 5·6 |
| 타 도메인 FK | 연쇄 삭제로 다른 사용자 데이터 훼손 방지 | anonymized User tombstone과 domain event/outbox | auth는 cascade delete하지 않음 | 공개 작성자 DTO 치환 | FK 유지·외부 응답 익명화 | 후속 domain PR |
| retention | task 30/90일, block 7일, tombstone 90일; guard는 현재 삭제하지 않음 | 별도 cleanup scheduler와 안전한 guard cleanup 설계 | terminal claim 정보 즉시 최소화, guard를 block과 함께 삭제 금지 | 미해결 DEAD 예외, guard에 원문 비저장 | 각 경계와 cleanup 멱등성 | 후속 retention PR |
| DEAD SLA | 즉시 경보, 24h 확인, 72h 복구, 7d 책임자 검토 | 구조화 metric·log, DB worker control과 감사 가능한 retry cycle | DB 직접 UPDATE와 자동 configuration resume 금지 | 허용 필드만 기록 | SLA event·권한·감사 이력·resume 원자성 | PR 6·후속 운영 |
| Admin/worker 기본값 | 빠른 실패와 단일 EC2 처리량 기준 | properties 외부화 | batch 10, concurrency 1, lease 120초 | secret masking | timeout·내부 retry 부재·lease | PR 4·6 |
| unknown 오류 | 검증 불가 성공과 무한 retry 방지 | 4xx DEAD_UNKNOWN, 5xx retry, malformed/ID mismatch DEAD | durable 12회 정책만 retry | body 원문 저장 금지 | 네 분류와 상태 전이 | PR 4·6 |
| API DTO·중복 | 완료/진행 의미 분리, polling 불필요 | `status=COMPLETED/ACCEPTED`, UTC `occurredAt` | 중복은 부수효과 없이 최초 결과와 시각 유지 | 내부 task·worker 정보 비노출 | 200/202·중복·UTC·polling 부재 | PR 7 |
| lock·claim SQL | deadlock과 중복 claim 억제 | 고정 lock order, 두 `SKIP LOCKED` query | 짧은 claim tx, MySQL 8 검증 | 로그에 identity 비노출 | 동시 claim·EXPLAIN·lease 경쟁 | PR 5·6 |
| migration ownership | PR별 schema 책임 분리 | PR 5 `V39`, PR 6 cleanup schema | PR 5 foundation, PR 6 cleanup schema | merge된 migration 변경 금지 | validate·실행계획·rollback | PR 5·6 |

## 5. 상태 모델

### 5.1 User

`[Gather 확정 정책]` 외부 unlink 필요 여부에 따라 완료 흐름을 분리한다.

| 회원 유형 | 상태 전이 | 응답 | 응답 시 보장 |
|---|---|---|---|
| 카카오 `LINKED` 계정이 없는 일반 회원 | `ACTIVE/SUSPENDED → WITHDRAWN` | `200 OK` | 접근 차단, 익명화와 필수 durable 후처리 저장까지 최종 완료 |
| 카카오 `LINKED` 회원 | `ACTIVE/SUSPENDED → WITHDRAWAL_PENDING → WITHDRAWN` | 접수 시 `202 Accepted` | 접근 차단과 durable unlink task 저장 완료, unlink·최종 파기는 진행 중 |

일반 회원은 외부 unlink를 기다릴 이유가 없으므로 탈퇴 트랜잭션에서 즉시 익명화하고 `WITHDRAWN`으로 완료한다. 카카오 회원은 접수 시 `WITHDRAWAL_PENDING`으로 접근을 차단하고, worker 성공 결과 트랜잭션에서만 익명화와 `WITHDRAWN` 전이를 수행한다. `DEAD`는 완료 조건이 아니다.

상태별 인증·탈퇴 의미는 다음과 같다.

| 상태 | 로그인 | Access Token 보호 API | Refresh Token 재발급 | 가입 세션 발급 | 가입 세션 제출 | 중복 탈퇴 요청 | 최종 개인정보 파기 |
|---|---|---|---|---|---|---|---|
| `ACTIVE` | 허용 | 허용 | 허용 | 카카오 신규 identity 정책에 따라 허용 | 유효 세션이면 허용 | 회원 유형별 최초 접수 | 일반 회원은 접수에서, 카카오는 worker에서 수행 |
| `SUSPENDED` | 현재 `LoginPolicy` 기준 | 현재 정책 기준 | 현재 정책 기준 | 현재 정책 재확인 | 현재 정책 재확인 | 회원 유형별 최초 접수 | 일반 회원은 접수에서, 카카오는 worker에서 수행 |
| `WITHDRAWAL_PENDING` | 거부 | `DELETE /api/v1/users/me`만 허용, 그 외 거부 | 거부 | 거부 | 거부 | 멱등하게 `202` | 수행하지 않음 |
| `WITHDRAWN` | 거부 | `DELETE /api/v1/users/me`만 허용, 그 외 거부 | 거부 | 거부 | 거부 | 멱등하게 `200` | 이미 완료됨 |

`[Gather 확정 정책]` `WITHDRAWAL_PENDING`과 `WITHDRAWN`은 일반 로그인, 카카오 로그인, refresh 재발급, 기존 access token 보호 API, 가입 세션 발급·제출, relink와 새 인증 세션 생성을 모두 차단한다. 단, network timeout과 client 재전송을 위해 인증된 `DELETE /api/v1/users/me`만 멱등 재호출할 수 있다.

- `WITHDRAWAL_PENDING`: 기존 요청 의미와 동일한 `202`; task·block·세션 취소를 다시 만들지 않는다.
- `WITHDRAWN`: 기존 완료 의미와 동일한 `200`; 익명화·이벤트·task를 다시 만들지 않는다.
- 이 예외를 위해 새 access/refresh token을 발급하지 않는다.

`[권장 구현]` 개별 Controller나 도메인 service에 검사를 반복하지 않고 다음 중앙 인증 흐름을 사용한다.

```text
JWT 서명·만료 검증
→ 현재 User 조회
→ JWT subject·User 일치 검증
→ User 상태와 HTTP method·path 확인
→ WITHDRAWAL_PENDING 또는 WITHDRAWN이면 정확한 DELETE /api/v1/users/me만 상태 정책 예외
→ 허용된 요청만 SecurityContext 등록
```

Access Token은 stateless JWT이므로 발급 이후 별도의 폐기 상태를 서버에서 조회하지 않는다. refresh token 삭제만으로는 이미 발급된 access token을 즉시 무효화할 수 없다. 따라서 `JwtAuthenticationFilter`는 JWT가 유효한 인증 요청마다 User를 PK로 단건 조회하고 `WITHDRAWAL_PENDING` 또는 `WITHDRAWN`이면 정확한 탈퇴 DELETE 예외 외에는 인증을 등록하지 않는다. 현재는 매 요청 DB 조회 비용보다 즉시 접근 차단의 정확성을 우선한다. 성능은 운영 지표를 확인한 뒤 별도로 최적화하며 Redis 또는 cache 도입은 이번 설계 범위가 아니다.

`[권장 구현]` 인증 거부는 기존 인증 `ErrorCode` 체계에 맞추고, DELETE 재호출 예외의 실제 Security 경로는 PR 5·7에서 현재 필터 구조에 맞춰 구현한다.

### 5.2 SocialAccount

```text
LINKED
  |
  | 탈퇴 접수, generation 유지
  v
UNLINK_PENDING
  |
  | 같은 generation의 unlink 완료
  v
UNLINKED

UNLINKED
  |
  | 미래의 명시적 재연결
  v
LINKED, generation + 1
```

`[Gather 확정 정책]` `SocialAccount`에는 이미 상태, generation, 암호화 식별자, HMAC 조회 키와 낙관적 잠금 버전이 있다.

`[Gather 확정 정책]` `UNLINK_PENDING` 진입은 task insert와 같은 트랜잭션에서 수행한다. `UNLINKED` 전이는 worker finalizer만 수행한다. task와 현재 generation이 다르면 외부 결과로 현재 계정을 변경하지 않는다.

`[Gather 확정 정책]` PR 4~8에서는 엔티티의 `relink()`를 호출하는 service나 API를 추가하지 않는다. `UNLINK_PENDING`과 `UNLINKED` 계정은 로그인·가입 연결 대상으로 사용하지 않으며, `AccountRejoinBlock`이 유효한 동안 재연결을 허용하지 않는다. relink는 stale task, active lease와 generation fencing을 재검토하는 별도 설계·PR에서만 도입한다.

### 5.3 KakaoUnlinkTask

```text
PENDING -> PROCESSING -> SUCCEEDED
   ^           |
   |           | retryable failure / lease expiry
   +-----------+

PROCESSING -> DEAD
PROCESSING -> STALE
```

- `PENDING`: 다음 실행 시각 이후 claim 가능
- `PROCESSING`: worker가 lease와 claim token을 소유
- `SUCCEEDED`: 동일 generation의 unlink와 로컬 finalization 완료
- `DEAD`: 자동 재시도 한도를 넘겼거나 복구 불가능한 설정·계약 오류
- `STALE`: 현재 `SocialAccount.generation`과 달라 결과를 적용할 수 없는 과거 작업

`[Gather 확정 정책]` `DEAD`는 scheduler가 자동 claim하지 않는다. `DEAD`가 되어도 `SocialAccount=UNLINK_PENDING`, `User=WITHDRAWAL_PENDING`을 유지하며 자동으로 `UNLINKED` 또는 `WITHDRAWN`을 만들지 않는다.

`[Gather 확정 정책]` `STALE`은 현재 상태에서 실행하면 안 되는 task다. 외부 API를 호출하지 않고 자동 재시도하지 않는다. 반면 `DEAD`는 해야 할 작업이지만 영구 오류 또는 재시도 소진으로 완료하지 못한 상태다.

`PERMANENT_CONFIGURATION`에서는 현재 task가 `DEAD`가 되는 것과 전역 worker control이 `CONFIGURATION_BLOCKED`가 되는 것을 구분한다. task terminal 상태는 해당 실행 결과를 보존하고, 전역 control 상태는 모든 인스턴스의 이후 claim을 차단한다.

| 현재 task | 조건 | 다음 task | worker control | HTTP | User / SocialAccount |
|---|---|---|---|---|---|
| `PENDING` | control `ACTIVE`, due | `PROCESSING` | `ACTIVE` | 아직 없음 | 변경 없음 |
| `PROCESSING` | generation mismatch | `STALE` | 유지 | 없음 | 변경 없음 |
| `PROCESSING` | same generation + `UNLINK_PENDING` + reservation 가능 | `PROCESSING` | `ACTIVE` | reservation commit 뒤 1회 | 결과 전까지 유지 |
| `PROCESSING` | same generation + `UNLINKED` + User pending | `SUCCEEDED` | 유지 | 없음 | 직접 ID 제거, User `WITHDRAWN`·익명화 |
| `PROCESSING` | success / `-101` 성공 동등 | `SUCCEEDED` | 유지 | 완료됨 | `UNLINKED`, 직접 ID 제거, User `WITHDRAWN`·익명화 |
| `PROCESSING` | retryable, reservation 예산 잔여 | `PENDING` | 유지 | 완료 또는 결과 불명 | pending 유지 |
| `PROCESSING` | 12번째 reservation 소진 또는 영구 task 오류 | `DEAD` | 유지 | 추가 호출 없음 | pending 유지 |
| `PROCESSING` | `PERMANENT_CONFIGURATION` | `DEAD` | `CONFIGURATION_BLOCKED` | 추가 호출 없음 | pending 유지 |
| `DEAD` | 설정 복구와 운영자 승인 retry cycle | `PENDING` | `ACTIVE` | scheduler 재개 후 가능 | pending 유지 |

`[권장 구현]` 외부 unlink 성공 뒤 finalizer 트랜잭션 전에 프로세스가 종료되면 같은 task가 다시 호출될 수 있다. 카카오가 이미 연결 해제되었다는 공식 오류가 확인되고 로컬 invariant가 유효할 때만 Gather의 멱등 정책으로 성공 동등 결과를 적용한다.

### 5.4 SocialSignupSession

`[Gather 확정 정책]` 가입 세션은 opaque token 기반의 영속 세션이며 동일 identity의 pending 세션을 ID 오름차순으로 잠글 수 있다.

`[Gather 확정 정책]` 탈퇴 접수 시 동일 identity의 `PENDING` 세션을 ID 오름차순으로 조회하고 `PESSIMISTIC_WRITE` 잠금을 획득한 뒤 각 `entity.cancel(now)`로 `CANCELLED` 전환한다. JPQL/native bulk update, 잠금 없는 다건 변경, `cancel()` 우회와 서로 다른 잠금 순서를 금지하며 `LockedSocialSignupSession`의 가입 성공 계약을 변경하지 않는다.

`[Gather 확정 정책]` PR 5 구현은 다음 service-level 진입점으로 잠긴 세션 묶음을 반환하고 outer 탈퇴 접수 트랜잭션에 참여시킨다. 호출자는 SocialAccount snapshot을 재검증하고 PHONE guard를 획득한 뒤 `cancelAll(now)`을 호출하며, 내부에서는 각 entity의 `cancel(now)`를 사용한다.

```java
@Transactional(propagation = Propagation.MANDATORY)
public LockedPendingSocialSignupSessions lockPendingForIdentity(
        SocialProvider provider,
        RejoinBlockIdentifier identifier,
        LocalDateTime now)
```

탈퇴 대상 사용자가 이미 가입된 상태이므로 일반적으로 pending 세션이 없어야 하지만, 과거 로그인·가입 경쟁이나 장애 복구 상황을 안전하게 정리하기 위해 이 단계를 둔다.

### 5.5 AccountRejoinBlock

`[Gather 확정 정책]` PHONE/KAKAO 식별자 해시, 키 버전, 만료 시각, 출처 사용자 ID를 담는 테이블 기반이 있다.

`[Gather 확정 정책]` unlink 성공 여부와 관계없이 탈퇴 접수 트랜잭션에서 필요한 block을 즉시 생성하거나 연장한다. worker는 block을 만들거나 수정하지 않는다. 카카오 로그인에서는 가입 세션 발급 전에 확인하고, 가입 확정 트랜잭션에서는 잠금 아래 최종 확인한다.

`[Gather 확정 정책]` PHONE과 KAKAO의 재가입 제한은 모두 7일이다.

`[Gather 확정 정책]` 활성 PHONE `AccountRejoinBlock`이 존재하는 전화번호로 신규 `ACTIVE` User가 생성되어서는 안 된다. 전화번호 가용성 API나 transaction 밖의 non-locking block 선조회는 빠른 사용자 안내를 위한 사전 검사일 뿐 최종 정합성 경계가 아니다. 최종 가입은 같은 PHONE guard lock 안에서 PHONE block locking read, 전화번호 중복 최종 확인, User 저장과 flush를 순서대로 수행한다. 일반 회원 탈퇴는 같은 guard lock 안에서 PHONE block을 생성·연장한 뒤 기존 User의 전화번호를 익명화한다. 일반적인 snapshot read만으로는 이 불변식을 보장하지 못한다.

- 일반 회원: `WITHDRAWN` 완료 시각을 기준으로 PHONE block을 저장한다.
- 카카오 회원: 탈퇴 접수 트랜잭션 성공 시각을 기준으로 PHONE/KAKAO block을 저장한다.
- `now < expiresAt`이면 차단하고 `now >= expiresAt`이면 허용한다.
- 기존 block은 `max(existing.expiresAt, now + 7 days)`로 연장하며 기간을 단축하지 않는다.
- 모든 시각은 DB에 UTC로 저장하고 애플리케이션의 일관된 `Clock`을 사용한다.
- 만료 row는 개인정보 보관기간이 끝난 뒤 정기 cleanup으로 물리 삭제한다. 조회는 row 존재가 아니라 `expiresAt`을 기준으로 하므로 cleanup 지연이 차단을 연장하지 않는다.

`[Gather 확정 정책]` 재가입 제한 기간과 `AccountRejoinBlock` row의 보관기간은 별개다. 차단 기간은 7일이고, row 자체는 **실제 탈퇴가 완료된 시각(`users.withdrawn_at`)으로부터 3 calendar months** 동안 보관한 뒤 파기한다.

- 보관기간 기산점은 `AccountRejoinBlock.createdAt`이 아니라 `sourceUserId`가 가리키는 User의 `withdrawnAt`이다. 카카오 회원은 탈퇴 접수 시점에 block이 먼저 생기고 unlink 성공 후에야 탈퇴가 완료되므로 두 시각이 다를 수 있다.
- 3개월은 90일이 아니라 달력 기준이며 월말은 보정한다(예: 1월 31일 탈퇴 → 4월 30일 보관 종료).
- 파기 대상은 `expiresAt <= now`이고 `withdrawnAt + 3개월 <= now`인 row다. 두 경계 모두 inclusive이며, 차단이 아직 유효한 row는 파기하지 않는다.
- 동일 식별자로 재가입 후 다시 탈퇴하면 기존 row가 재사용되므로 upsert에서 `sourceUserId`와 `keyVersion`을 최신 탈퇴 기준으로 갱신한다. `expiresAt`은 단축하지 않고 `createdAt`은 row 최초 생성 시각으로 유지한다.
- cleanup scheduler는 기본 비활성이며 `GATHER_REJOIN_BLOCK_CLEANUP_SCHEDULER_ENABLED=true`로만 켜진다. 과거 upsert가 `sourceUserId`를 갱신하지 않아 legacy row의 기산점이 실제 탈퇴일보다 이를 수 있으므로, 운영 데이터 점검 전에는 환경변수가 누락돼도 파기가 실행되지 않아야 한다.
- 활성화된 뒤 정상 운영에서는 보관기간 종료 후 최대 약 1시간 안에 파기된다. 애플리케이션이 장기 중단되면 3개월을 넘겨 남을 수 있으나 다음 기동의 startup cleanup에서 제거한다.
- 이 파기가 보장하는 범위는 `AccountRejoinBlock`에 저장된 PHONE/KAKAO HMAC row에 한정된다. `account_identity_guard` 등 다른 내부 HMAC은 포함하지 않는다.

`[Gather 확정 정책]` `AccountRejoinBlock`과 `AccountIdentityGuard`는 동일한 active HMAC identity와 key version을 사용한다. HMAC keyring과 previous-key lookup이 구현되기 전까지 두 기능에 사용하는 HMAC secret과 key version을 기간 제한 없이 변경하지 않는다. 서비스 운영 중 block은 계속 생성될 수 있으므로 “활성 block의 7일 동안만” 회전을 금지하는 것으로는 충분하지 않다. 준비 없이 회전하면 동일 전화번호가 새 hash로 계산되어 가입과 탈퇴가 서로 다른 guard row를 잠글 수 있고, 기존 key로 만든 block 조회도 실패해 활성 재가입 제한을 우회할 수 있다.

`[권장 구현]` PHONE/KAKAO 식별자는 HMAC-SHA256과 key version으로 저장한다. 현재 HMAC keyring, previous-key lookup, 여러 key의 guard locking, rehash/backfill과 안전한 rotation 절차는 구현되지 않았다. 향후 회전은 이 기능과 migration 전략을 함께 설계한 뒤 도입하며 일반 SHA-256으로 대체하지 않는다. 원문과 HMAC 전체는 로그에 남기지 않는다.

`[운영·법적 검토]` 7일은 Gather 서비스 정책이며 법정 의무 기간이 아니다. 서비스 목적, 약관·개인정보처리방침 문구와 HMAC identifier의 처리 목적·보유 기간 고지를 운영 공개 전에 검토한다.

### 5.6 AccountIdentityGuard

`[Gather 확정 정책]` `account_identity_guard`는 재가입 제한 상태를 저장하지 않는다. 동일 전화번호를 처리하는 회원가입과 회원 탈퇴 트랜잭션을 전화번호별로 직렬화하는 안정적인 잠금 기준점이다. 현재 지원하는 identity type은 `PHONE`이며, active HMAC으로 계산한 hash와 key version을 사용한다.

row의 유일 식별자는 다음 조합이다.

```text
(identity_type, key_version, identity_hash)
```

전화번호 원문이나 복호화 가능한 전화번호, HMAC secret, 카카오 회원번호, Kakao Admin Key는 저장하지 않는다. 같은 active HMAC identity는 항상 같은 guard row를 사용한다.

잠금은 기존 application transaction 안에서 다음 순서로 획득한다.

```text
1. 전화번호를 active HMAC으로 한 번 해시
2. guard row를 upsert
3. 동일 guard row를 SELECT ... FOR UPDATE
4. transaction commit 또는 rollback까지 lock 유지
```

upsert는 row가 없으면 만들고 이미 있으면 변경 없이 기존 row를 유지한다.

```sql
INSERT INTO account_identity_guard (...)
VALUES (...)
ON DUPLICATE KEY UPDATE id = id;
```

`AccountIdentityGuardService.lockPhone()`은 `MANDATORY`로 호출자의 transaction에 참여하며 별도 `REQUIRES_NEW` transaction을 열지 않는다.

`[Gather 확정 정책]` guard row의 생명주기는 `AccountRejoinBlock`의 7일 만료와 독립적이다. 같은 전화번호가 계속 같은 잠금 기준점을 사용하도록 현재 설계에서는 자동 만료·정리하지 않고 block 만료 시 함께 삭제하지 않는다. PR 5에는 guard retention scheduler가 없다. 장기 row 증가량과 안전한 cleanup 전략은 별도 논의 대상이며, 안전한 삭제 전략이 설계되기 전에는 guard를 삭제하지 않는다. 이는 무조건 영구 보존을 확정한 것이 아니라 현재 lifecycle 계약이다.

### 5.7 확정 상태 흐름

```text
일반 회원 탈퇴 완료:
User: ACTIVE / SUSPENDED → WITHDRAWN
PHONE AccountRejoinBlock: 7일
Refresh Token: 전량 삭제
개인정보: 즉시 익명화
```

```text
카카오 회원 탈퇴 접수:
User: ACTIVE / SUSPENDED → WITHDRAWAL_PENDING
SocialAccount: LINKED → UNLINK_PENDING
PENDING SocialSignupSession → CANCELLED
KakaoUnlinkTask → PENDING
```

```text
카카오 worker 성공:
KakaoUnlinkTask: PENDING → PROCESSING → SUCCEEDED
SocialAccount: UNLINK_PENDING → UNLINKED
User: WITHDRAWAL_PENDING → WITHDRAWN
```

```text
일시적 실패:
KakaoUnlinkTask: PROCESSING → PENDING
nextAttemptAt 갱신
```

```text
영구 실패 또는 최대 12회 소진:
KakaoUnlinkTask: PROCESSING → DEAD
User = WITHDRAWAL_PENDING 유지
SocialAccount = UNLINK_PENDING 유지
```

```text
오래되거나 유효하지 않은 task:
KakaoUnlinkTask: PROCESSING → STALE
카카오 API 호출 금지
현재 User/SocialAccount 상태 변경 금지
```

## 6. 탈퇴 접수 트랜잭션

### 6.1 사전 조건

- 인증된 현재 사용자만 요청할 수 있다.
- 공개 `DELETE /api/v1/users/me`는 request body를 받지 않으며 사용자 직접 탈퇴 원천을 `WithdrawalReason.SELF`로 고정한다.
- `KAKAO_UNLINK`, `ADMIN`은 내부 처리 전용이고 공개 입력으로 노출하지 않는다. 사용자 설문형 상세 탈퇴 사유가 필요하면 별도의 `WithdrawalSurveyReason`과 후속 PR로 설계한다.
- 사용자에게 카카오 `LINKED` 계정이 있는지 잠금 아래 다시 확인해 일반·카카오 경로를 선택한다.

### 6.2 일반 회원 완료 트랜잭션

`[Gather 확정 정책]` 카카오 `LINKED` 계정이 없는 일반 회원은 하나의 트랜잭션에서 탈퇴를 완료하고 `200` 결과를 만든다.

1. `User`를 비관적 잠금한다.
2. 이미 `WITHDRAWN`이면 추가 부수효과 없이 기존 완료 결과를 반환한다.
3. 카카오 `LINKED` 계정이 없음을 재검증한다.
4. 익명화 전에 원본 전화번호와 정리에 필요한 이메일·프로필 이미지 key를 캡처한다.
5. 원본 전화번호의 PHONE identity guard를 잠근다.
6. PHONE `AccountRejoinBlock`을 `max(existing.expiresAt, now+7일)`로 생성·연장한다.
7. 사용자 refresh token을 전량 삭제하고 이메일 인증·프로필 이미지 durable 삭제 데이터를 정리한다.
8. `User`를 `WITHDRAWN`으로 전환하고 같은 guard lock 안에서 auth/user 개인정보와 기존 전화번호를 익명화한다. 현재 `User.anonymize()`의 상태 invariant에 맞춰 실제 호출 순서는 withdraw 후 anonymize로 둔다.
9. 완료 시각을 기록하고 커밋한다.

### 6.3 카카오 회원 접수 트랜잭션

`[Gather 확정 정책]` 카카오 `LINKED` 회원은 하나의 짧은 트랜잭션에서 다음을 수행하고 `202` 결과를 만든다.

1. `User`를 비관적 잠금한다.
2. `WITHDRAWAL_PENDING`이면 추가 부수효과 없이 기존 접수 결과를, `WITHDRAWN`이면 기존 완료 결과를 반환한다.
3. 동일 identity의 pending `SocialSignupSession`을 ID 오름차순으로 잠그고 각 `cancel(now)`를 호출한다.
4. `SocialAccount`를 비관적 잠금하고 provider, `LINKED` 상태, generation과 처음 읽은 identity snapshot을 재검증한다.
5. 원본 전화번호의 PHONE identity guard를 잠근다.
6. 잠근 가입 세션을 각 entity의 `cancel(now)`로 취소한다. bulk session update는 사용하지 않는다.
7. `User → WITHDRAWAL_PENDING`, `SocialAccount → UNLINK_PENDING`으로 전환한다.
8. PHONE, KAKAO 순서로 `AccountRejoinBlock`을 7일까지 생성·연장한다.
9. 사용자 refresh token을 전량 삭제한다.
10. `(social_account_id, generation)`이 유일한 `KakaoUnlinkTask(PENDING)`를 `saveAndFlush`한다.
11. 커밋한다.

외부 카카오 HTTP 호출과 개인정보 파기는 이 트랜잭션에서 수행하지 않는다.

### 6.4 실패 원자성

- task insert가 실패하면 사용자와 소셜 계정의 pending 전이도 롤백한다.
- refresh token 폐기나 재가입 block 생성이 실패해도 전체 접수를 롤백한다.
- 중복 요청이 unique constraint와 경쟁하면 기존 task와 최신 사용자 상태를 다시 조회해 멱등 결과로 변환한다.
- 탈퇴 API service가 `WITHDRAWAL_PENDING + UNLINKED` 등 허용되지 않은 조합을 발견하면 `ACCOUNT_TERMINATION_STATE_CONFLICT`로 거부한다. 동일 generation `UNLINKED`의 local finalization은 이미 claim된 task를 처리하는 PR 6 worker 책임이다.

`[Gather 확정 정책]` 위 작업은 하나의 AccountTermination 트랜잭션이다. task enqueue는 별도 `REQUIRES_NEW`를 사용하지 않고 동일 트랜잭션에 참여한다. task 저장 실패 시 전체 접수를 rollback하고, 상태 전이 실패 시 task도 저장하지 않는다. task 없는 `UNLINK_PENDING`과 탈퇴와 관계없는 고아 task를 허용하지 않는다.

`[권장 구현]` `AccountTerminationService`는 기본 `REQUIRED`를 사용하고 enqueue와 가입 세션 취소 service는 `MANDATORY` 또는 같은 `REQUIRED` 트랜잭션 참여를 보장한다. 정확한 annotation은 실제 호출 구조에 맞추되 원자성은 변경하지 않는다. after-commit 이벤트는 worker를 빠르게 깨우는 최적화일 뿐 task 생성의 유일한 경로가 아니다.

`[Gather 확정 정책]` refresh token 삭제는 단일 token이 아니라 해당 사용자의 모든 로그인 세션을 대상으로 하며 상태 전이와 같은 트랜잭션에서 수행한다. 삭제 실패 시 접수 전체를 rollback한다.

`[권장 구현]` repository 메서드는 `deleteAllByUserId(userId)`처럼 사용자 전체 삭제 의미를 드러내야 한다. 실제 엔티티 연관관계에 따라 이름은 조정할 수 있다.

### 6.5 PR 7 HTTP adapter와 transaction 경계

PR 7은 탈퇴 로직을 다시 구현하지 않고 다음 흐름으로 기존 application service를 공개 API에 연결한다.

```text
UserController
  -> SecurityUtil.getCurrentUserId()
  -> AccountTerminationService.terminate(userId, WithdrawalReason.SELF)
  -> service transaction commit
  -> COMPLETED/ACCEPTED를 200/202로 매핑
  -> RefreshTokenCookieProvider.clear()로 Set-Cookie 반환
```

Controller에는 `@Transactional`을 선언하지 않는다. DB transaction은 `AccountTerminationService`의 public transactional method가 소유하고, 서비스가 정상 반환하면 commit이 완료된 상태에서 Controller가 HTTP status, 응답 DTO와 cookie header를 조립한다. Controller는 Repository를 직접 호출하지 않고, 요청 처리 중 Kakao Admin API나 S3 API도 직접 호출하지 않는다.

## 7. Worker 흐름

`[Gather 확정 정책]` worker는 claim transaction, preflight, attempt reservation transaction, transaction 없는 외부 호출, result/finalizer transaction으로 분리한다. 카카오 HTTP 요청 중에는 DB transaction이나 row lock을 유지하지 않는다.

### 7.1 Claim 트랜잭션

1. singleton `KakaoUnlinkWorkerControl` row를 먼저 잠그고 상태가 `ACTIVE`인지 확인한다. `CONFIGURATION_BLOCKED`이면 task를 claim하지 않고 종료한다.
2. 같은 claim transaction에서 `databaseNow = SELECT UTC_TIMESTAMP(6)`을 정확히 한 번 구한다.
3. `status=PENDING AND next_attempt_at <= :databaseNow`인 행 또는 `status=PROCESSING AND lease_expires_at <= :databaseNow`인 행을 찾는다. native query 내부에서 별도의 `UTC_TIMESTAMP(6)`을 다시 평가하지 않는다.
4. MySQL의 `FOR UPDATE SKIP LOCKED`를 사용해 due task는 `(next_attempt_at, id)`, expired lease는 `(lease_expires_at, id)` 순서로 제한된 batch를 잠근다.
5. 각 행을 `PROCESSING`으로 바꾸고 새 `claim_token`, `claimed_by`, `claimed_at = databaseNow`, `lease_expires_at = databaseNow + leaseDuration`을 기록한다. entity의 claim/reclaim invariant도 같은 `databaseNow`를 사용한다.
6. 커밋한다.

lease는 HTTP connect/read timeout과 정상적인 결과 처리 시간을 합친 값보다 충분히 길어야 한다. batch 크기와 worker 동시성은 DB connection pool과 카카오 호출량 제한을 함께 고려한다.

### 7.2 Preflight와 동일 generation `UNLINKED` 분기

각 claim에 대해 다음 상태를 확인한다.

1. 공통 invariant를 확인한다.
   - `SocialAccount`가 존재한다.
   - provider가 `KAKAO`다.
   - task의 `socialAccountId`와 조회된 계정 ID가 일치한다.
   - `SocialAccount.generation == task.generation`이다.
   - 연결된 `User.status == WITHDRAWAL_PENDING`이다.
   - task가 현재 worker의 유효한 `PROCESSING` claim과 claim token을 가진다.
2. generation이 다르면 HTTP를 호출하지 않고 `STALE` 후보로 반환한다.
3. 같은 generation의 `UNLINK_PENDING`이면 attempt reservation 후보로 반환한다.
4. 같은 generation의 `UNLINKED`이고 User가 `WITHDRAWAL_PENDING`이면 attempt reservation과 HTTP 호출 없이 local finalization 후보로 반환한다.
5. 같은 generation이라도 그 밖의 상태 조합이면 `STALE` 또는 명시적인 local invariant failure로 분류한다.
6. claim 소유권이 유효하지 않으면 task 상태를 변경하지 않고 실행을 중단한다.

사전 generation 검사는 불필요한 호출을 줄이는 최적화다. 정확성을 위해 결과 트랜잭션에서 반드시 다시 검증한다.

### 7.3 Attempt reservation 트랜잭션과 외부 호출

HTTP 호출 직전의 짧은 reservation transaction에서 `attemptNow = LocalDateTime.now(Clock.systemUTC())`를 한 번 캡처하고 다음을 수행한다.

1. `KakaoUnlinkWorkerControl → SocialAccount → KakaoUnlinkTask → User` 순서로 row lock을 획득한다.
2. worker control이 여전히 `ACTIVE`인지 확인한다. 이미 `CONFIGURATION_BLOCKED`이면 reservation을 만들지 않고 현재 batch 실행을 중단한다.
3. task가 `PROCESSING`이고 claim token이 일치하며 lease가 DB UTC 시각 기준으로 유효한지 확인한다.
4. 현재 retry cycle의 `attemptCount < 12`인지 확인한다.
5. provider `KAKAO`, task와 SocialAccount generation 일치, User `WITHDRAWAL_PENDING`, SocialAccount `UNLINK_PENDING`을 다시 확인한다.
6. 호출에 필요한 직접 provider identifier가 존재하고 복호화 가능하며 복호화 결과가 양의 Kakao ID인지 확인한다.
7. `attemptCount + 1`, `lastAttemptAt = attemptNow`를 반영하고 커밋한다.

`attemptCount`는 실제 HTTP 호출 횟수가 아니라 commit된 attempt reservation 횟수다. reservation commit 직후 프로세스가 종료되어 실제 요청이 전송되지 않은 경우도 포함할 수 있으며 실제 외부 호출 횟수는 항상 `attemptCount` 이하이다. preflight에서 `STALE` 또는 local finalization으로 분류된 task에는 reservation을 만들지 않는다.

reservation commit 뒤 transaction 없이 Admin unlink client를 호출한다. client는 HTTP status, Kakao `code`, 응답 `id`, timeout·network·parse 결과와 안전하게 파싱한 `retryAfterAt`만 typed result로 반환한다. 민감한 요청·응답 원문, raw `Retry-After`, 전체 header와 파싱 오류 원문은 task나 로그에 기록하지 않는다.

12번째 reservation commit 직후 프로세스가 종료되면 실제 외부 호출이 12회보다 적더라도 lease 회수 후 추가 HTTP 호출을 하지 않는다. 자동 처리 예산이 소진된 것으로 보고 terminal 처리한다.

### 7.4 결과·finalizer 트랜잭션

result/finalizer transaction마다 `resultNow = LocalDateTime.now(Clock.systemUTC())`를 한 번 캡처한다. HTTP 호출 전의 `attemptNow`를 완료 시각이나 retry 기준 시각으로 재사용하지 않는다.

1. 필요한 경우 `KakaoUnlinkWorkerControl`을 가장 먼저 잠그고, 그 뒤 `SocialAccount → KakaoUnlinkTask → User` 순서로 잠근다.
2. task가 아직 같은 `claim_token`의 `PROCESSING`이고 lease가 유효한지 확인한다.
3. 현재 `SocialAccount.generation == task.generation`, provider `KAKAO`, 연결된 User `WITHDRAWAL_PENDING`을 다시 확인한다.
4. 세대가 다르면 task를 `STALE`로 종료하고 현재 소셜 계정과 사용자는 변경하지 않는다.
5. 성공 또는 검증된 성공 동등 결과라면:
   - task를 `SUCCEEDED`로 전환한다.
   - `UNLINK_PENDING`인 `SocialAccount`만 `UNLINKED`로 전환한다.
   - legacy provider ID, ciphertext와 encryption key version을 즉시 제거한다.
   - providerUserKey HMAC과 provider key version은 cooldown 동안 유지한다.
   - `User`가 여전히 `WITHDRAWAL_PENDING`인지 확인한다.
   - 사용자를 `WITHDRAWN`으로 전환하고 같은 트랜잭션에서 개인정보를 익명화한다.
   - 필요한 프로필 이미지 durable 삭제 처리를 등록한다.
6. 동일 generation의 `UNLINKED` local finalization이라면:
   - `markUnlinked()`를 다시 호출하거나 기존 `unlinkedAt`을 덮어쓰지 않는다.
   - 남은 legacy provider ID, ciphertext와 encryption key version을 제거한다.
   - User를 `WITHDRAWN`으로 전환하고 개인정보 익명화와 프로필 이미지 durable 삭제를 등록한다.
   - task를 `SUCCEEDED`로 전환한다.
7. retryable 결과라면 이미 증가한 reservation 수를 유지하고 backoff를 계산해 `PENDING`으로 되돌리거나, 12번째 reservation이면 `DEAD`로 종료한다.
8. `PERMANENT_CONFIGURATION`이면 현재 task를 `DEAD`, worker control을 `CONFIGURATION_BLOCKED`로 같은 transaction에서 전환하고 현재 batch를 즉시 중단한다.
9. 그 밖의 영구 실패는 현재 task를 `DEAD`로 전환한다.
10. terminal 전이에서는 claim token과 lease 정보를 정리하고 `completedAt = resultNow`를 기록한 뒤 커밋한다.

`[Gather 확정 정책]` `SUCCEEDED`는 카카오 HTTP 성공만이 아니라 로컬 상태 전이와 필수 개인정보 파기까지 같은 결과 트랜잭션에서 완료되었음을 뜻한다.

### 7.5 Lease 회수와 fencing

- lease가 만료된 `PROCESSING` task는 새 worker가 다시 claim할 수 있다.
- claim마다 예측 불가능한 `claim_token`을 새로 발급한다.
- 늦게 돌아온 이전 worker는 task ID와 claim token이 모두 일치할 때만 결과를 기록할 수 있다.
- lease 연장이 필요하다면 같은 claim token의 소유자만 갱신한다.
- due eligibility, expired lease eligibility, `claimedAt`과 `leaseExpiresAt`은 JVM 시각이 아니라 DB UTC 시각을 사용한다.

이 분리는 외부 timeout 중 DB lock 유지, connection pool 고갈, deadlock 가능성, 카카오 장애의 DB 전파를 방지하고 worker crash·재시작과 stuck task 회수를 가능하게 한다. 필수 보완 장치는 claim token, lease expiration, `claimedBy`, generation·상태 재검증, 결과 트랜잭션의 claim 소유권 검증과 멱등성이다.

### 7.6 시간 책임과 운영 계약

`[Gather 확정 정책]` 애플리케이션 공통 Clock은 `Clock.systemUTC()`를 사용한다. 현재 구현의 `KakaoTimeConfig`와 `kakaoClock`은 여러 auth service가 공통 사용하므로 PR 6 구현 단계에서 공통 시간 설정 책임이 드러나는 이름과 위치로 정리한다.

시간 책임은 다음처럼 분리한다.

| 책임 | 기준 시각 |
|---|---|
| due task eligibility, expired lease eligibility | claim transaction에서 한 번 읽은 DB UTC `databaseNow` |
| `claimedAt`, `leaseExpiresAt` | eligibility와 동일한 `databaseNow`, `databaseNow + leaseDuration` |
| attempt reservation, `lastAttemptAt` | `Clock.systemUTC()`의 `attemptNow` |
| Retry-After, backoff, `nextAttemptAt` | `Clock.systemUTC()`의 `resultNow` |
| result 처리, `completedAt`, User/SocialAccount 상태 전이 | `Clock.systemUTC()`의 `resultNow` |

`operation당 now 한 번`은 전체 task 생명주기에서 한 번이 아니라 각 transaction 단계에서 한 번이라는 뜻이다. claim transaction은 DB의 `claimNow`, reservation transaction은 application의 `attemptNow`, result/finalizer transaction은 application의 `resultNow`를 각각 한 번만 사용한다.

운영 계약은 DB session timezone UTC, JVM timezone UTC, EC2 시스템 시각의 NTP 동기화다. 애플리케이션은 `hibernate.jdbc.time_zone=UTC`도 명시해 Hibernate timestamp binding을 JVM 기본 timezone에만 맡기지 않는다. local example의 `Asia/Seoul` JDBC 설정은 PR 6 구현에서 UTC로 변경한다. 배포 전 기존 `DATETIME` 데이터가 KST wall-clock으로 저장됐는지 표본 검증하며, UTC 설정 변경이 기존 데이터의 자동 변환을 뜻하지 않는다.

### 7.7 Admin client와 worker 기본값

`[Gather 확정 정책]` 다음 값은 configuration properties로 외부화하는 기본값이다.

| 설정 | 기본값 |
|---|---|
| connect timeout | 2초 |
| read timeout | 5초 |
| HTTP client 내부 retry | 없음 |
| scheduler fixed delay | 30초 |
| claim batch size | 10 |
| worker concurrency | 1 |
| processing lease | 120초 |

retry는 HTTP client가 아니라 durable task가 담당한다. 현재 단일 EC2와 낮은 처리량에서는 worker 1개가 충분하고, batch 10개 순차 처리의 예상 최악 시간에 여유를 둔 lease를 사용한다. lease 만료 task는 scheduler가 회수한다.

local/test에도 명시적 UTC 기본값을 두고 운영에서는 Admin client enabled 여부를 분리한다. worker가 활성화되려면 Admin client도 활성화되어야 하며 `worker=true`, `admin=false` 조합은 설정 오류로 애플리케이션 기동을 실패시킨다. concurrency를 늘릴 때 batch와 lease를 함께 재검토한다. 현재 값은 확정 기본값이지만 운영 지표에 따라 configuration으로 조정할 수 있다.

| Admin | Worker | 기동 결과 |
|---|---:|---|
| false | false | 정상 기동, Admin client와 worker 비활성 |
| true | false | 정상 기동, Admin client만 활성 |
| false | true | 설정 오류로 startup 실패 |
| true | true | Admin client와 worker 활성 |

`[Gather 확정 정책]` 공개 탈퇴 API는 위 가용성을 사전 확인하거나 `KakaoUnlinkWorkerControl`을 직접 조회하지 않는다. Admin client 또는 worker가 비활성이거나 control이 `CONFIGURATION_BLOCKED`여도 카카오 탈퇴 의사를 `WITHDRAWAL_PENDING`, `UNLINK_PENDING`, `KakaoUnlinkTask.PENDING`으로 durable하게 저장하고 `202 Accepted`를 반환한다. 운영 장애를 이유로 공개 탈퇴 요청을 `503`으로 거부하지 않는다.

처리되지 않은 `PENDING` task는 backlog에 유지되고 worker와 Admin client가 활성화되며 control이 `ACTIVE`로 복구된 뒤 기존 claim query의 대상이 된다. 공개 응답에는 worker disabled·blocked 여부를 노출하지 않는다.

## 8. Generation과 stale task

### 8.1 필요한 이유

다음 순서가 가능하다.

1. generation 3 계정의 unlink task가 생성된다.
2. worker가 외부 호출 중이거나 장애로 멈춘다.
3. 운영 복구 또는 미래 기능으로 계정이 재연결되어 generation 4가 된다.
4. generation 3 worker가 늦게 결과를 기록한다.

generation 검증이 없으면 과거 결과가 새 연결을 `UNLINKED`로 덮어쓴다.

### 8.2 규칙

`[Gather 확정 정책]` 다음 invariant를 적용한다.

- task 생성 시 현재 generation을 저장한다.
- `(social_account_id, generation)`은 유일하다.
- HTTP 호출 전 `SocialAccount` 존재, provider `KAKAO`, generation 일치와 연결 사용자 `WITHDRAWAL_PENDING`을 검증한다.
- 같은 generation의 `UNLINK_PENDING`만 reservation 뒤 HTTP 호출 대상으로 삼는다.
- 같은 generation의 `UNLINKED`는 `STALE`이 아니라 reservation과 HTTP 호출 없는 local finalization 대상으로 삼는다.
- 결과 반영 직전 잠금 아래 같은 invariant를 다시 검증한다.
- generation mismatch는 현재 계정과 사용자를 변경하지 않고 task를 `STALE`로 종료한다. 그 밖의 허용되지 않은 상태 조합은 `STALE` 또는 명시적인 invariant failure로 분류한다.
- mismatch를 retryable failure로 취급하지 않으며 `STALE`은 자동 재시도하지 않는다.
- `STALE` metric에는 원인만 남기고 식별정보를 출력하지 않는다.
- future relink는 동일 계정의 unresolved task와 active lease가 없는지 확인한 뒤 generation을 증가시켜야 한다.

### 8.3 외부 부작용의 한계

generation 3 요청이 카카오에서 실제 성공한 직후 로컬 재연결이 발생하면, 카카오 연결 자체는 이미 해제되었을 수 있다. 로컬 generation 검증은 오래된 결과가 새 상태를 덮는 것을 막지만 외부 호출을 되돌리지는 못한다.

`[Gather 확정 정책]` PR 4~8에서는 재연결 기능을 구현하거나 허용하지 않는다. 향후 별도 PR에서 도입할 때 카카오 재인증 완료, unresolved task·active lease 확인과 로컬 generation 전이를 하나의 명시적 프로토콜로 설계한다.

## 9. Retry 및 오류 분류

공식 문서가 보장하는 값과 Gather의 처리 정책을 구분한다.

| HTTP / Kakao code | 공식 의미 | Gather 분류 | Retry | task 최종 상태 | SocialAccount | 로그·알림 |
|---|---|---|---|---|---|---|
| `200`, 응답 `id` 일치 | unlink 성공 | 성공 | 아니요 | `SUCCEEDED` | 같은 generation을 `UNLINKED`로 전환 | 성공 metric만 기록 |
| `200`, 응답 `id` 불일치 | 성공 형식이지만 요청 대상과 다른 응답 | security failure | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | 즉시 보안 경보, 실제 ID 기록 금지 |
| `code=-101` | 앱과 연결되지 않은 카카오계정 | 이미 unlink 상태로 간주하는 Gather 제안 | 아니요 | invariant 유효 시 `SUCCEEDED` | 같은 generation만 `UNLINKED` | 성공 동등 metric |
| `400`, `code=-10` | API 호출 허용량 초과 | retryable | 예 | 성공 전에는 비종료, 한도 초과 시 `DEAD` | `UNLINK_PENDING` 유지 | rate-limit metric·지속 시 경보 |
| `401`, `code=-401` | 유효하지 않은 인증 정보 | permanent configuration error | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | 즉시 운영·보안 경보 |
| `403`, `code=-3` | 해당 key 또는 앱에 API 사용 권한 없음 | permanent configuration error | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | 즉시 운영 경보 |
| `500`, `502`, `503` | 서버 처리 오류 | retryable | 예 | 성공 전에는 비종료, 한도 초과 시 `DEAD` | `UNLINK_PENDING` 유지 | 오류율·backlog 경보 |
| timeout, DNS, 일시적 network 오류 | 전송 결과를 확정할 수 없음 | retryable | 예 | 성공 전에는 비종료, 한도 초과 시 `DEAD` | `UNLINK_PENDING` 유지 | 정규화된 오류만 기록 |
| 호출 전 invariant 불일치 | 외부 오류가 아닌 로컬 세대·상태 불일치 | stale local task | 아니요, HTTP 미호출 | `STALE` | 변경 없음 | stale metric과 원인만 기록 |
| unknown 4xx | 공식 의미 추가 확인 필요 | `DEAD_UNKNOWN` | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | HTTP·code만 기록하고 경보 |
| unknown 5xx | 공식 의미 추가 확인 필요 | `RETRYABLE_UNKNOWN` | 예 | 12회 소진 시 `DEAD` | `UNLINK_PENDING` 유지 | 오류율·backlog 경보 |
| 2xx body 누락·파싱 실패 | 성공 검증 불가 | `DEAD_RESPONSE` | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | 계약 변경 경보 |
| 2xx 응답 ID 불일치 | 대상 검증 실패 | `DEAD_SECURITY` | 아니요 | `DEAD` | `UNLINK_PENDING` 유지 | 즉시 보안 경보 |

`[공식 계약]` Kakao 오류의 `msg`는 고정 계약이 아니므로 분기 기준으로 사용하지 않는다.

`[권장 구현]` `-101`을 성공 동등 결과로 보는 것은 Gather의 멱등 정책이지 카카오의 성공 응답 정의가 아니다. 현재 task의 대상·generation·로컬 상태가 정확할 때만 적용한다.

### 9.1 Retry 계산과 attempt 정의

`[Gather 확정 정책]` retryable 오류에는 기본 지연 1분의 지수 backoff와 full jitter를 적용한다. 계산된 지연 상한은 6시간이고 자동 worker는 한 retry cycle에서 최대 12개의 attempt reservation만 허용한다. 최초 task는 즉시 실행할 수 있다.

```text
1차 실패: 최대 1분 범위에서 무작위 지연
2차 실패: 최대 2분 범위에서 무작위 지연
3차 실패: 최대 4분 범위에서 무작위 지연
4차 실패: 최대 8분 범위에서 무작위 지연
...
상한: 최대 6시간 범위에서 무작위 지연
```

full jitter는 `0 ~ min(1분 × 2^(retryableFailureIndex-1), 6시간)` 범위에서 다음 지연을 선택하므로 정확히 표의 최대 시간 뒤에 실행된다는 뜻이 아니다.

현재 자동 retry cycle에서는 영구 결과가 즉시 terminal로 끝나므로 `retryableFailureIndex`는 retryable result를 만든 현재 reservation의 `attemptCount`와 같다. 운영자가 새 cycle을 시작하면 둘 다 1차 시도 기준으로 다시 시작한다.

`attempt_count`는 scheduler 조회나 claim 횟수, 실제 HTTP 응답 횟수가 아니라 Kakao Admin API 호출을 위해 commit된 attempt reservation 횟수다. 프로세스 장애로 실제 HTTP 요청이 전송되지 않은 reservation도 포함할 수 있으며 실제 외부 호출 횟수는 `attempt_count` 이하이다. preflight에서 `STALE` 또는 동일 generation `UNLINKED` local finalization으로 분기한 task는 증가시키지 않는다.

재시도 대상은 network·DNS·connect/read timeout, 일시적 5xx, rate limit과 공식 계약상 카카오 측 일시 오류다. Admin key 오류, 콘솔 권한 미설정, 잘못된 요청 형식, 응답 ID 불일치, provider ID 복호화 불가, generation mismatch와 local invariant 위반은 재시도하지 않는다.

### 9.2 Retry-After 계약

`[Gather 확정 정책]` `Retry-After`는 typed result의 disposition이 `RETRYABLE`이고 HTTP status가 `429` 또는 `5xx`일 때만 적용한다. Kakao code 분류가 HTTP status fallback보다 우선하므로 Kakao code가 영구 오류이면 HTTP status가 `429` 또는 `5xx`여도 무시한다. Kakao code 때문에 `RETRYABLE`이지만 HTTP status가 `400`인 경우와 network 오류처럼 HTTP 응답이 없는 경우에도 적용하지 않는다.

`KakaoAdminUnlinkResult`에는 raw header나 전체 response header가 아니라 client가 안전하게 파싱한 `Instant retryAfterAt`만 추가한다. 값이 없거나 신뢰할 수 없으면 `null`이다. client도 애플리케이션 공통 UTC Clock으로 응답 수신 시각을 한 번 캡처해 delta-seconds를 절대 시각으로 변환한다. 지원 형식은 `delta-seconds`와 RFC 1123 HTTP-date다.

- `0` delta-seconds는 유효하다.
- 음수, 부호가 붙은 숫자, 소수, 빈 값과 공백만 있는 값은 무효다.
- 과거 HTTP-date와 의미가 모호한 복수 header 값은 무효다.
- 숫자 overflow와 파싱 실패는 외부 예외로 노출하지 않는다.
- raw header, 전체 response header와 파싱 오류 원문을 task나 로그에 저장하지 않는다.
- `5xx` body read failure에서도 응답 header를 안전하게 확보했다면 적용할 수 있다.

result transaction의 `resultNow`를 기준으로 다음처럼 계산한다.

```text
fullJitterDelay =
    random(0, min(1분 × 2^(retryableFailureIndex - 1), 6시간))

retryAfterDelay =
    max(0, retryAfterAt - resultNow)

effectiveDelay =
    min(max(fullJitterDelay, retryAfterDelay), 6시간)
```

지나치게 큰 `retryAfterAt`도 최종 6시간 cap을 넘지 않는다. `nextAttemptAt = resultNow + effectiveDelay`로 계산한다.

### 9.3 DEAD와 configuration circuit breaker 정책

`[Gather 확정 정책]` 영구 설정·요청 오류, 응답 ID 불일치, provider ID 복호화 불가, `STALE`로 분류되지 않는 데이터 무결성 오류 또는 한 retry cycle의 12개 reservation 소진은 `DEAD`로 종료한다. scheduler는 `DEAD` task를 다시 claim하지 않는다.

```text
KakaoUnlinkTask = DEAD
SocialAccount = UNLINK_PENDING
User = WITHDRAWAL_PENDING
```

`DEAD`에서 `SocialAccount → UNLINKED` 또는 `User → WITHDRAWN`을 자동 수행하지 않는다. 운영 알림을 발생시키고 오류 code와 sanitize된 설명만 저장하며 원문 요청·응답은 저장하지 않는다.

`PERMANENT_CONFIGURATION`은 개별 사용자 데이터 문제가 아니라 Admin key, App ID, 권한과 같은 전역 환경 오류다. 해당 result transaction은 DB 기반 singleton worker control을 `CONFIGURATION_BLOCKED`로 바꾸고 현재 task를 `DEAD`로 종료하며 claim·lease 정보를 정리한다. task에는 HTTP status, Kakao code, normalized error type `CONFIGURATION`과 `completedAt`만 저장한다. Admin key, Authorization header, provider ID와 원문 응답은 저장하지 않는다.

현재 batch는 즉시 중단하고 이후 scheduler는 control row가 `ACTIVE`로 복구될 때까지 claim하지 않는다. 인메모리 pause는 재시작 시 해제되고 다중 인스턴스가 공유하지 못하므로 correctness 수단으로 사용하지 않는다. 자동 worker는 configuration block을 스스로 해제하지 않는다.

`CONFIGURATION_BLOCKED` 중 공개 API가 새로 저장한 task는 처음부터 `PENDING`이므로 one-shot resume command의 `DEAD + CONFIGURATION` requeue 대상이 아니다. 운영자가 configuration 원인의 모든 `DEAD` task를 requeue하고 control을 `ACTIVE`로 복구하면, 차단 중 생성된 `PENDING` backlog도 별도 상태 변경 없이 scheduler가 자연스럽게 claim한다.

### 9.4 최종 오류 분류 원칙

- `[공식 계약]` `-101`은 해당 앱과 연결되지 않은 사용자 오류다.
- `[Gather 확정 정책]` local account ID, generation, `UNLINK_PENDING`, `WITHDRAWAL_PENDING`과 복호화한 요청 대상 검증이 모두 맞을 때만 `-101`을 목표 상태가 이미 충족된 것으로 보고 멱등 성공 처리한다.
- `[Gather 확정 정책]` local `SocialAccount`가 이미 동일 generation의 `UNLINKED`이고 User가 `WITHDRAWAL_PENDING`이면 외부 성공을 추정하지 않고 HTTP 없는 local finalization 경로로 처리한다.
- network, DNS, connect/read timeout, `500/502/503`, rate limit과 공식 문서상 일시·점검 오류만 자동 retry한다.
- 잘못된 parameter, Admin key·앱 불일치, Admin API 설정 누락, 허용되지 않은 operation, 권한 부족, 종료 API, 앱·개발자 제재, 복호화 실패와 응답 ID 불일치는 즉시 `DEAD`다.
- HTTP `429`는 방어적으로 retryable로 처리하되 unlink 쿼터 분류는 body `code`를 우선한다.
- `msg` 문자열에는 의존하지 않는다.

### 9.5 DEAD 운영 SLA와 운영 retry cycle

`[Gather 확정 정책]`

- 발생 즉시 ERROR 구조화 로그와 metric
- 24시간 이내 원인 확인
- 72시간 이내 설정 수정 또는 복구 시도
- 7일 이상 미해결이면 서비스 책임자와 개인정보 담당 검토

운영자는 오류 task뿐 아니라 `PENDING` backlog 수, 가장 오래된 `PENDING` task 체류 시간, `DEAD`·`STALE` 수, `KakaoUnlinkWorkerControl` 상태와 `WITHDRAWAL_PENDING` 사용자 체류 시간을 함께 모니터링한다. worker disabled 또는 configuration block 중에도 신규 접수를 허용하므로 backlog와 체류 시간은 공개 API 가용성과 분리된 필수 운영 지표다.

로그·metric에는 task ID, socialAccount ID, generation, attempt count, normalized error code와 발생 시각만 사용한다. Admin key, provider user ID, providerUserKey 전체, ciphertext, form body, 카카오 원문, 전화번호와 이메일은 금지한다.

PR 6에는 공개 수동 retry API나 범용 관리자 UI를 넣지 않는다. DB 직접 UPDATE를 금지하고 권한 있는 운영 절차와 application service로만 worker control resume와 task requeue를 수행한다.

configuration 복구 순서는 Admin 설정 수정, 안전한 설정 검증 또는 운영자 확인, `CONFIGURATION` 원인으로 `DEAD`가 된 모든 task의 원자적 requeue, worker control의 `ACTIVE` 전환, worker 재개다. control resume와 task requeue는 자동 동작이 아니다.

PR 6의 production 복구 진입점은 공개 Controller가 아니라 `kakao-unlink-resume` profile의 one-shot command다. command bean은 해당 profile과 `gather.kakao.unlink-resume.enabled=true`가 모두 있을 때만 등록된다. 실제 non-web application context, `gather.scheduling.enabled=false`, `kakao.admin.unlink-worker.enabled=false`, task ID 목록, 허용된 actor와 normalized reason을 DB 변경 전에 검증하며 하나라도 어긋나면 service를 호출하지 않고 non-zero로 종료한다. resume profile에서는 property 값과 무관하게 scheduling infrastructure 자체를 등록하지 않는다.

Resume command는 운영자가 선택한 일부 task만 복구하는 기능이 아니다. service는 control row를 먼저 잠가 `CONFIGURATION_BLOCKED`를 확인한 뒤 DB에 존재하는 모든 `DEAD + CONFIGURATION` task를 ID 오름차순으로 잠금 조회한다. 요청 task ID를 정렬·중복 제거한 목록과 DB 전체 대상 ID 목록이 정확히 같을 때만 모든 task의 새 retry cycle을 시작하고 마지막에 control을 `ACTIVE`로 바꾼다. 누락 ID, 추가 ID, 존재하지 않는 ID, 다른 상태의 task, 빈 요청 또는 control/task 상태 불일치가 하나라도 있으면 control과 모든 task 변경을 rollback해 부분 성공을 금지한다. command 재실행은 control이 더 이상 blocked가 아니므로 명확히 거부하며 retry cycle을 다시 증가시키지 않는다.

권장 실행 예시는 다음과 같다. 실행 전에 Kakao Admin 설정을 먼저 수정하고 검증한다. 운영자는 다음 조회 결과의 전체 ID를 command에 전달해야 하며 일부만 전달하면 command가 실패한다. command는 Kakao API를 호출하지 않고 worker control과 DB 전체 configuration-dead task를 하나의 transaction으로 복구한다.

```sql
SELECT id
FROM kakao_unlink_task
WHERE status = 'DEAD'
  AND last_error_type = 'CONFIGURATION'
ORDER BY id;
```

```powershell
java -jar gather.jar `
  --spring.profiles.active=prod,kakao-unlink-resume `
  --spring.main.web-application-type=none `
  --gather.scheduling.enabled=false `
  --kakao.admin.unlink-worker.enabled=false `
  --gather.kakao.unlink-resume.enabled=true `
  --gather.kakao.unlink-resume.task-ids=123,124 `
  --gather.kakao.unlink-resume.actor=operator-name `
  --gather.kakao.unlink-resume.reason=ADMIN_KEY_CORRECTED
```

허용 reason은 `ADMIN_KEY_CORRECTED`, `APP_ID_CORRECTED`, `KAKAO_PERMISSION_CORRECTED`, `CONFIGURATION_VERIFIED`다. 종료 코드는 `0` 성공, `2` 입력·실행 환경 오류, `3` control/task invariant 불일치, `4` transaction 또는 예기치 못한 실행 실패다. 성공 로그는 transactional service가 정상 반환해 commit이 완료된 뒤 executor에서 resumed count, actor, normalized reason만 기록한다. 성공·실패 모두 Spring context를 닫은 뒤 종료하며 command-line 전체, Admin key, provider ID, 원문 예외 메시지를 로그에 기록하지 않는다.

Acceptance criteria는 A와 B가 모두 `DEAD/CONFIGURATION`일 때 A만 요청하거나 A/B 외 ID를 추가하면 control이 blocked로 유지되고 A/B의 status, retry cycle과 attempt count가 모두 불변인 것이다. A/B 전체를 순서 변경·중복 포함해 요청하면 두 task 모두 `PENDING`, retry cycle은 각각 1 증가, attempt count는 0이 되고 control은 `ACTIVE`가 된다.

자동 worker는 한 retry cycle에서 `attemptCount` 12를 초과하지 않는다. 운영자가 새 retry cycle을 승인하면 `retryCycle + 1`, `attemptCount = 0`, task `PENDING`, `nextAttemptAt = resumeNow`로 전환하고 claim 정보와 이전 terminal 시각을 새 cycle 규칙에 맞게 정리한다. 이전 실패 이력은 구조화 로그 또는 별도 감사 정보로 추적한다. 이 계약에는 `retryCycle`과 동등한 감사 필드가 필요하며 자동 처리 중에는 변경하지 않는다.

`[운영·법적 검토]` 실제 운영 알림 채널과 담당자를 운영 공개 전에 지정한다.

## 10. Task 데이터 모델

권장 테이블명은 `kakao_unlink_task`다.

| 컬럼 | 필수 | 용도 | 보안 고려 | index / constraint |
|---|---|---|---|---|
| `id` | 예 | PK | 비민감 내부 ID | PK |
| `social_account_id` | 예 | 대상 `SocialAccount` 참조 | API에 노출하지 않음 | FK `RESTRICT`, generation과 unique |
| `generation` | 예 | 생성 시점 연결 세대 snapshot | 비민감 상태 값 | unique `(social_account_id, generation)` |
| `status` | 예 | `PENDING`, `PROCESSING`, `SUCCEEDED`, `DEAD`, `STALE` | 비민감 | claim·lease 복합 index |
| `retry_cycle` | 예 | 운영 승인으로 시작한 retry cycle 번호 | 비민감 감사 값 | 기본값 `0`, 자동 worker 변경 금지 |
| `attempt_count` | 예 | 현재 cycle에서 commit된 attempt reservation 횟수 | 비민감 | 기본값 `0`, cycle별 `0..12` |
| `next_attempt_at` | 예 | 다음 claim 가능 시각 | 비민감 | `(status, next_attempt_at, id)` |
| `last_attempt_at` | 아니요 | 마지막 attempt reservation 시각 | 비민감 | 운영 조회용 선택 index |
| `claimed_at` | 아니요 | 현재 claim 획득 시각 | 비민감 | 진단용 |
| `lease_expires_at` | 아니요 | `PROCESSING` claim 만료 시각 | 비민감 | `(status, lease_expires_at, id)` |
| `claim_token` | 아니요 | 늦은 worker 결과를 막는 fencing token | 충분한 entropy, 로그·API 노출 금지 | 활성 claim 내 검증 |
| `claimed_by` | 아니요 | worker instance 식별자 | hostname 등에 비밀을 넣지 않음 | 진단용 |
| `completed_at` | 아니요 | terminal 상태 도달 시각 | 비민감 | retention 조회용 선택 index |
| `last_http_status` | 아니요 | 마지막 HTTP status | 비민감 | index 불필요 |
| `last_kakao_code` | 아니요 | 마지막 Kakao code | provider ID를 넣지 않음 | 운영 집계용 선택 index |
| `last_error_type` | 아니요 | retryable/config/security/unknown 등 분류 | 비민감 enum | 운영 집계용 선택 index |
| `created_at`, `updated_at` | 예 | 생성·갱신 감사 시각 | 비민감 | retention 필요 시 index |
| `version` | 예 | 방어적 낙관적 잠금 | 비민감 | version invariant |

필수 제약과 인덱스:

- unique `(social_account_id, generation)`
- claim index `(status, next_attempt_at, id)`
- lease recovery index `(status, lease_expires_at, id)`
- FK는 task 이력을 의도치 않게 삭제하지 않도록 `RESTRICT`
- 상태별 필수 컬럼 조합은 application invariant와 migration 가능한 범위의 check constraint로 보호

`[권장 구현]` task에는 Admin key, 카카오 회원번호의 평문, 암호문, HMAC 조회 키, 카카오 응답 원문, unlink 요청 body를 저장하지 않는다. 호출 시 동일 generation의 `SocialAccount`에서 읽는다. task가 민감정보의 두 번째 보관소가 되면 키 회전과 파기 범위가 불필요하게 커진다.

### 10.1 전역 worker control 모델

PR 6은 모든 인스턴스가 공유하는 singleton `KakaoUnlinkWorkerControl` row를 둔다.

| 컬럼 | 용도 | 보안·동시성 |
|---|---|---|
| `status` | `ACTIVE`, `CONFIGURATION_BLOCKED` | scheduler claim 전 잠금·확인 |
| `blocked_at` | 설정 오류로 전역 차단된 UTC 시각 | 비민감 진단 |
| `blocked_reason` | 정규화된 설정 오류 분류 | 원문·비밀값 금지 |
| `last_http_status` | 설정 오류의 HTTP status | nullable |
| `last_kakao_code` | 설정 오류의 Kakao code | provider ID 금지 |
| `updated_at` | 마지막 전이 시각 | application UTC |
| `version` | 동시 resume·block 방어 | 낙관적 잠금 보조 |

configuration failure transaction은 control row를 먼저 잠그고 `CONFIGURATION_BLOCKED` 전이와 현재 task `DEAD` 전이를 함께 commit한다. scheduler claim transaction도 control row를 먼저 잠근 뒤 due/expired task를 claim한다.

## 11. 잠금 순서와 동시성

`[Gather 확정 정책]` worker control이 필요한 트랜잭션은 `KakaoUnlinkWorkerControl → SocialAccount → KakaoUnlinkTask → User` 순서를, control row가 필요 없는 worker 트랜잭션은 `SocialAccount → KakaoUnlinkTask → User` 순서를 핵심 invariant로 사용한다. 해당 transaction에서 필요하지 않은 row는 잠그지 않지만 두 종류 이상의 row를 함께 잠글 때 순서를 바꾸지 않는다.

### 11.1 일반 회원가입

```text
PHONE identity guard
  -> PHONE AccountRejoinBlock locking read
  -> 이메일 인증 및 이메일·전화번호·닉네임 중복 최종 검사
  -> User 저장 및 flush
```

전화번호 가용성 API와 transaction 밖 선조회는 사용자 안내용 최적화다. 신규 User 생성의 최종 정합성은 위 전체 구간을 같은 PHONE guard lock 안에서 실행해 보장한다.

### 11.2 카카오 회원가입 완료

```text
SocialSignupSession PESSIMISTIC_WRITE
  -> PHONE identity guard
  -> PHONE AccountRejoinBlock locking read
  -> KAKAO AccountRejoinBlock 확인
  -> 전화번호·닉네임·SocialAccount 중복 최종 검사
  -> User 및 SocialAccount 저장
```

PR 3의 가입 세션 잠금과 상태 전이 계약을 유지한다. KAKAO block은 가입 세션 발급 전에도 확인하지만, 가입 완료 transaction에서 PHONE guard와 PHONE block locking read 뒤 다시 확인한다.

### 11.3 일반 회원 탈퇴

```text
User PESSIMISTIC_WRITE
  -> 원본 전화번호 캡처
  -> PHONE identity guard
  -> PHONE AccountRejoinBlock 생성 또는 연장
  -> Refresh Token·이메일 인증·프로필 이미지 정리
  -> User WITHDRAWN
  -> User 개인정보와 기존 전화번호 익명화
```

PHONE block 생성·연장부터 기존 User 전화번호 익명화까지 같은 guard lock을 유지한다.

### 11.4 카카오 회원 탈퇴 접수

```text
User PESSIMISTIC_WRITE
  -> 동일 identity PENDING SocialSignupSession ID ASC PESSIMISTIC_WRITE
  -> SocialAccount PESSIMISTIC_WRITE
  -> identity snapshot 재검증
  -> PHONE identity guard
  -> 가입 세션 cancel(now)
  -> User WITHDRAWAL_PENDING
  -> SocialAccount UNLINK_PENDING
  -> PHONE/KAKAO AccountRejoinBlock 생성 또는 연장
  -> Refresh Token 전체 삭제
  -> KakaoUnlinkTask 저장 및 flush
```

가입 세션은 ID 오름차순으로 잠그고 각 entity의 `cancel(now)`를 사용한다. bulk session update를 금지한다. 상태 전이와 task 저장은 같은 transaction이며, Kakao Admin API는 transaction 안에서 호출하지 않는다. task 저장이 실패하면 세션 취소, 상태 전이, block과 token 삭제를 모두 rollback한다. `WITHDRAWAL_PENDING`에서는 User 개인정보를 익명화하지 않으며 finalizer는 PR 6 책임이다.

### 11.5 Worker 결과 반영

```text
KakaoUnlinkWorkerControl (필요한 경우)
  -> SocialAccount
  -> KakaoUnlinkTask
  -> User
```

Scheduler claim은 control row를 먼저 확인하고 잠근다. task와 SocialAccount를 함께 잠그는 reservation·result·finalizer도 같은 순서를 사용한다. User finalization은 잠긴 SocialAccount가 가리키는 user를 검증한 후 처리하며, 반대 순서로 SocialAccount를 다시 획득하는 호출을 만들지 않는다.

### 11.6 MySQL due task claim

```sql
SELECT *
FROM kakao_unlink_task
WHERE status = 'PENDING'
  AND next_attempt_at <= :databaseNow
ORDER BY next_attempt_at, id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

필수 index:

```sql
(status, next_attempt_at, id)
```

### 11.7 만료 lease 회수

```sql
SELECT *
FROM kakao_unlink_task
WHERE status = 'PROCESSING'
  AND lease_expires_at <= :databaseNow
ORDER BY lease_expires_at, id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

필수 index:

```sql
(status, lease_expires_at, id)
```

native query 여부는 PR 6에서 정할 수 있지만 DB UTC 시각, SQL 의미와 정렬·잠금 계약은 유지한다. 실제 claim/reclaim transaction은 먼저 singleton worker control을 잠가 `ACTIVE`인지 확인한 뒤 이 query를 수행한다.

### 11.8 PHONE 가입·탈퇴 경쟁의 허용 결과

동시 실행이 항상 같은 domain error로 끝날 필요는 없지만 활성 PHONE block이 있는 identity에 신규 ACTIVE User가 생기지 않는 최종 정책은 유지되어야 한다.

- 회원가입이 PHONE guard를 먼저 획득하면 기존 User의 전화번호가 아직 익명화되지 않았으므로 가입은 전화번호 중복 오류로 실패하고, 이후 탈퇴가 정상 진행한다.
- 탈퇴가 PHONE guard를 먼저 획득하면 PHONE block 생성과 전화번호 익명화를 commit한 뒤 대기하던 가입이 최신 block을 locking read하여 재가입 제한 오류로 실패한다.

외부에는 `DUPLICATE_PHONE_NUMBER` 또는 `ACCOUNT_REJOIN_BLOCKED`와 같은 정상 domain error를 반환한다. duplicate key SQL exception, `DataIntegrityViolationException`, deadlock 원문이나 lock wait timeout 원문 같은 DB 구현 예외를 직접 노출하지 않는다.

### 11.9 동시성 검증과 deadlock

- 같은 사용자의 탈퇴 요청 두 건
- 일반 회원가입과 일반 회원 탈퇴의 PHONE guard 경쟁
- 탈퇴 접수와 카카오 가입 확정의 경쟁
- worker 두 대가 같은 task를 claim
- lease 만료 직전 이전 worker와 새 worker의 결과 경쟁
- task 결과 반영과 future relink의 경쟁
- 탈퇴 접수와 외부 unlink webhook의 경쟁
- task 성공 후 finalizer 실패와 재시도

MySQL 8에서 두 worker의 동시 claim, 중복 claim 방지, `SKIP LOCKED`, due ordering, lease 회수 경쟁, index 사용, `EXPLAIN ANALYZE`, lock wait·deadlock과 batch 크기별 실행계획을 검증한다. H2만으로 완료 판정하지 않는다.

잠금 순서를 지켜도 MySQL deadlock이나 lock wait timeout은 발생할 수 있다. 이 경우 DB 트랜잭션 전체를 rollback하고 제한된 application-level transaction retry 또는 공통 예외 변환을 검토한다. 실제 횟수는 구현·부하 검증으로 정하며, 외부 HTTP retry와 DB deadlock retry를 혼동하지 않고 DB 원문을 사용자에게 노출하지 않는다.

## 12. 개인정보 파기 정책

### 12.1 공식 요구

- `[공식 계약]` 서비스 탈퇴 시 개인정보를 복구할 수 없게 파기한다.
- `[공식 계약]` 탈퇴 뒤 보관하려면 동의 등 적법한 근거가 필요하다.
- `[공식 계약]` 카카오 앱별 회원번호도 개인정보이므로 파기 대상이다.

### 12.2 현재 코드

- `[Gather 확정 정책]` `User.anonymize()`는 사용자 엔티티의 주요 개인정보를 제거하고 전화번호·닉네임을 비식별 대체값으로 변경한다.
- `[Gather 확정 정책]` `SocialAccount`는 카카오 회원번호를 legacy 평문, AES-GCM 암호문, HMAC 조회 키 형태로 보관한다.
- `[Gather 확정 정책]` 현재 스키마의 일부 소셜 식별자 컬럼은 null 허용과 행 tombstone을 전제로 설계되지 않았다.
- `[Gather 확정 정책]` `AccountRejoinBlock` 생성·연장과 만료 판정은 인증 흐름에 연결됐다. `AccountIdentityGuard`는 원문 없이 PHONE HMAC identity의 잠금 기준점만 저장하며 block 만료와 함께 삭제하지 않는다.

### 12.3 권장 파기 시점과 범위

`[Gather 확정 정책]` 일반 회원은 탈퇴 트랜잭션에서 즉시 익명화한다. 카카오 회원은 `WITHDRAWAL_PENDING`에서 접근만 차단하고, unlink 성공 결과 또는 동일 generation `UNLINKED` local finalization 트랜잭션에서 `SocialAccount.UNLINKED`, 복구 가능한 직접 identifier 파기, User 익명화, `User.WITHDRAWN`, task `SUCCEEDED`를 함께 반영한다. legacy provider ID, ciphertext 또는 encryption key version이 남아 있으면 local finalization을 완료한 것으로 보지 않는다.

### 12.4 SocialAccount 식별자 lifecycle

| 단계 | linkStatus | legacy provider ID | provider ID ciphertext | encryption key version | providerUserKey HMAC | provider key version | generation | 삭제 가능 여부 |
|---|---|---|---|---|---|---|---|---|
| 연결 중 | `LINKED` | 현재 dual-write 호환 범위에서만 유지 | 유지 | 유지 | 로그인·identity 조회용 유지 | 유지 | 유지 | 삭제 불가 |
| unlink 대기 | `UNLINK_PENDING` | legacy 호환 범위에서만 유지 | Admin 호출용 유지 | 유지 | task 대상 검증·cooldown 종료까지 유지 | 같은 시점까지 유지 | 유지 | 삭제 불가 |
| unlink 성공~접수 기준 cooldown 종료 | `UNLINKED` | 즉시 제거 | 즉시 제거 | 즉시 제거 | `AccountRejoinBlock.expiresAt`까지 유지 | 같은 시점까지 유지 | 유지 | row 삭제 불가 |
| cooldown 종료 | `UNLINKED` | 없음 | 없음 | 없음 | 제거 | 제거 | 유지 | 조건 충족 시 tombstone cleanup 후보 |

`[Gather 확정 정책]` 신규 코드에서 raw provider ID 평문 의존을 추가하지 않으며 task row에 ciphertext나 provider ID를 복제하지 않는다.

최소 tombstone은 다음만 유지한다.

```text
id, userId, provider, linkStatus=UNLINKED, generation,
connectedAt, unlinkedAt, createdAt, updatedAt
```

unlink 성공 후 기본 90일이 지나고 non-terminal task가 없으며, block이 만료됐고, 동일 identity의 진행 중 가입 세션·운영 hold·FK 제약이 없을 때 삭제할 수 있다. FK·task history·generation 계약이 깨지면 최소 tombstone을 더 오래 유지한다. 실제 cleanup은 후속 retention PR 범위다.

`[Gather 확정 정책]` PR 6 finalizer는 cooldown이 이미 끝났더라도 providerUserKey HMAC과 provider key version을 제거하지 않는다. 복구 가능한 직접 식별자 파기와 단방향 identity retention cleanup의 책임을 분리하기 위해 HMAC 제거는 후속 retention PR만 담당한다.

후속 HMAC cleanup은 `SocialAccount=UNLINKED`, `User=WITHDRAWN`, KAKAO `AccountRejoinBlock` 만료, 진행 중 unlink task 없음, providerUserKey 존재를 모두 확인한다. 이 cleanup은 providerUserKey HMAC과 provider key version만 제거하며 `AccountIdentityGuard`, `KakaoUnlinkTask` 감사 데이터와 아직 활성인 `AccountRejoinBlock`을 삭제하지 않는다.

```text
복구 가능한 Kakao 직접 식별자는 unlink 성공 시 즉시 파기한다.
단방향 provider HMAC과 keyVersion은 재가입 제한 기간 동안 유지하며,
만료 후 제거는 별도 retention cleanup PR에서 구현한다.
```

### 12.5 DEAD와 ciphertext

`[Gather 확정 정책]` `PENDING`, `PROCESSING`, 미해결 `DEAD`에서는 실제 unlink와 운영 재처리를 위해 `SocialAccount` ciphertext와 encryption key version을 유지한다. 미해결 `DEAD`의 ciphertext를 단순 retention 만료로 자동 제거하지 않는다.

`SUCCEEDED` 또는 수동 재처리 성공 시 결과 트랜잭션에서 즉시 제거한다. 해결된 `DEAD`도 같은 규칙을 적용한다. `STALE`은 task 대상 generation의 식별자를 더 이상 사용하지 않지만, generation mismatch가 현재의 새 연결을 뜻한다면 현재 generation의 ciphertext를 삭제하지 않는다. PR 4~8에서는 relink가 금지되므로 이러한 mismatch는 운영 이상으로 경보한다.

`STALE` 전이 시 row의 ciphertext가 stale task generation에 속하고 현재 활성 연결이 아님을 잠금 아래 입증할 수 있으면 제거한다. 입증할 수 없으면 현재 식별자를 보존하고 운영 경보로 분리한다.

### 12.6 auth/user 개인정보 처리

| 필드 또는 관계 | 탈퇴 시 처리 | nullable/unique 고려 | 담당 단계 | 필수 테스트 |
|---|---|---|---|---|
| 이름 | `null` | nullable 확인 | 일반 접수 / 카카오 finalizer | 원문 제거 |
| 이메일 | `null` 우선, 불가하면 사용자별 비가역 고유값 | nullable·unique migration 확인 | 동일 | unique 충돌·원문 부재 |
| 전화번호 | 사용자별 비가역 고유값 또는 `null` | 현재 unique와 가입 조회 영향 확인 | 동일 | 재가입 block과 unique 충돌 |
| 닉네임 | DB에는 사용자별 고유 익명값 | unique 유지 | 동일 | 중복 사용자 충돌 없음 |
| 생년월일·성별·소개 | `null` | nullable 확인 | 동일 | 원문 제거 |
| 비밀번호 hash | `null` 또는 인증 불가능한 값 | nullable·인증 경로 확인 | 동일 | 비밀번호 인증 불가 |
| profile image key/URL | `null` | 응답 URL 파생 경로 확인 | 동일 | 응답 null |
| 마케팅 동의 | `false` | non-null 기본값 | 동일 | false 저장 |
| 활동 지역 join | 삭제 | FK·orphan 정책 확인 | 동일 | 관계 row 삭제 |
| 관심 카테고리 join | 삭제 | FK·orphan 정책 확인 | 동일 | 관계 row 삭제 |
| 이메일 인증 row | 삭제 | 사용자·이메일 조회 제약 확인 | 동일 | 잔존 row 없음 |
| Refresh Token | 전량 삭제 | 모든 기기 대상 | 접수 트랜잭션 | 전량 삭제·rollback |
| profile image S3 object | durable deletion row로 제거 | DB 커밋 뒤 외부 삭제 | 접수/finalizer | retry와 멱등 삭제 |

정확한 nullable·UNIQUE 변경은 PR 5·6에서 실제 schema와 FK를 확인한 새 migration으로 결정한다. `wd_원래전화번호`, `withdrawn_원래이메일`처럼 원문을 접두어 뒤에 이어 붙이는 가역적 익명화는 금지한다.

User row는 FK 정합성을 위한 tombstone으로 유지한다. `id`, `status`, `withdrawnAt`, `withdrawalReason`, `anonymizedAt`과 최소 감사 metadata만 남긴다. DB의 nickname에는 사용자별 고유 익명값을 저장하되 외부 응답은 `nickname="탈퇴한 사용자"`, `profileImageUrl=null`, `publicStatus=WITHDRAWN`으로 치환한다.

### 12.7 타 도메인과 FK

`[Gather 확정 정책]` auth finalizer는 게시글, 댓글, 활동 기록, 모임, 모임 참여, 알림과 기타 User FK를 cascade delete하지 않는다. non-null FK와 다른 사용자의 데이터를 보존하기 위해 anonymized User tombstone 참조를 유지한다.

모임장 승계, 팀원 제외, 가입 신청 취소, 북마크·관계 정리, 공개 작성자 DTO 치환, 알림 행위자 익명화와 도메인별 보존은 별도 후속 작업이다. auth/user 도메인은 공통 탈퇴 event 또는 outbox 계약까지만 제공하며 `account_event_outbox` 도입은 PR 4~8과 별도로 검토할 수 있다.

### 12.8 데이터 보존 기본값

| 데이터 | 보존 정책 |
|---|---|
| `AccountRejoinBlock` | 재가입 차단 7일, row 보관은 `users.withdrawn_at` + 3 calendar months + 최대 약 1시간 cleanup 지연 |
| `AccountIdentityGuard` | 현재 자동 만료·cleanup 없음; 안전한 삭제 전략 설계 전 유지 |
| `SUCCEEDED` task | 완료 후 30일 |
| `STALE` task | 전이 후 30일 |
| 해결된 `DEAD` 이력 | 해결 후 90일 |
| 미해결 `DEAD` | 해결될 때까지, SLA 모니터링 필수 |
| terminal task claim token | terminal 전이 시 즉시 제거 |
| terminal lease/claimedBy | terminal 전이 시 제거 또는 최소화 |
| sanitize된 오류 metadata | task row와 같은 기간 |
| SocialAccount HMAC | cooldown 종료까지 최대 7일 |
| SocialAccount tombstone | unlink 성공 후 기본 90일 |
| User anonymized tombstone | FK가 존재하는 동안 |
| 애플리케이션 운영 로그 | 기본 30일 |

보존기간 종료는 즉시 삭제 가능한 시점이며 cleanup 지연이 서비스 차단 기간을 늘리지 않는다. 실제 cleanup scheduler는 후속 retention PR에서 구현한다. 법령·분쟁 보존 근거가 생기면 일반 서비스 데이터와 분리한다.

### 12.9 운영·법적 검토

- `[운영·법적 검토]` 게시글·모임·활동 기록의 실제 보존 기간
- `[운영·법적 검토]` 법정 보존 또는 분쟁 대응 데이터의 분리 보관
- `[운영·법적 검토]` 백업·스냅샷에서 개인정보 제거 및 복원 시 재적용 절차

## 13. 탈퇴 API 계약

PR 7은 공개 회원 탈퇴 HTTP API를 기존 `AccountTerminationService` 계약에 연결하는 adapter PR이다. endpoint는 다음으로 확정한다.

```http
DELETE /api/v1/users/me
Authorization: Bearer <access-token>
```

### 13.1 요청과 호출 계약

- request body를 받지 않으며 OpenAPI에도 request body를 선언하지 않는다.
- `SecurityUtil.getCurrentUserId()`로 인증된 현재 사용자 ID를 얻는다.
- 사용자 직접 탈퇴 원천은 `WithdrawalReason.SELF`로 고정해 `AccountTerminationService.terminate(userId, SELF)`를 호출한다.
- 내부 원천인 `KAKAO_UNLINK`, `ADMIN`은 공개 입력으로 노출하지 않는다.
- 사용자 설문형 상세 탈퇴 사유는 PR 7 범위가 아니며 필요하면 별도의 `WithdrawalSurveyReason` 모델과 후속 PR로 구현한다.

### 13.2 성공 응답과 시간 계약

`AccountTerminationResult.outcome`을 다음과 같이 HTTP status와 `AccountTerminationResponse`에 매핑한다.

| service outcome | HTTP | `data.status` | `data.occurredAt` 의미 |
|---|---:|---|---|
| `COMPLETED` | `200 OK` | `COMPLETED` | 최초 탈퇴 완료 시각 |
| `ACCEPTED` | `202 Accepted` | `ACCEPTED` | 최초 durable 탈퇴 접수 시각 |

`204 No Content`는 사용하지 않는다. 일반 동기 완료와 카카오 비동기 접수를 프론트가 구분해야 하고, 프로젝트의 `ApiResponse<T>` 성공 wrapper를 유지하며, 멱등 재요청에서도 기존 결과를 일관되게 반환해야 하기 때문이다.

일반 회원 또는 이미 완료된 요청의 응답은 다음과 같다.


```json
{
  "success": true,
  "data": {
    "status": "COMPLETED",
    "occurredAt": "2026-08-01T14:00:00Z"
  },
  "error": null
}
```

카카오 회원의 신규 또는 기존 접수 응답은 다음과 같다.

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

`occurredAt`은 UTC ISO-8601 형식으로 노출한다. 응답 DTO 타입은 `Instant` 또는 `OffsetDateTime`을 사용하고, 서비스의 UTC 기준 `LocalDateTime`을 그대로 직렬화하지 않고 PR 7 adapter에서 `ZoneOffset.UTC`를 명시해 변환한다. 서버 기본 timezone에 의존하거나 timezone 없는 값을 반환하면서 문서 예시에만 `Z`를 붙이지 않는다. 두 결과 모두 필드 이름은 `occurredAt`으로 통일한다.

`ACCEPTED`는 실패나 미접수가 아니라 접근 차단과 durable task insert가 commit된 상태다. 아직 unlink, `SocialAccount.UNLINKED`, `User.WITHDRAWN`, 개인정보 최종 파기 또는 task `SUCCEEDED`를 의미하지 않는다.

### 13.3 상태별 HTTP·멱등 계약

| User/SocialAccount 상태 | 서비스 결과 | HTTP | DB 변경 | Cookie |
|---|---|---:|---|---|
| `ACTIVE/SUSPENDED`, 카카오 없음 | `COMPLETED` | `200` | 동기 탈퇴 완료 | 만료 |
| `ACTIVE/SUSPENDED`, `LINKED` 카카오 | `ACCEPTED` | `202` | pending 전환 및 task 생성 | 만료 |
| `WITHDRAWAL_PENDING` + 정상 `UNLINK_PENDING`/task | `ACCEPTED` | `202` | 없음 | 만료 |
| `WITHDRAWN` + SocialAccount 없음 또는 `UNLINKED` | `COMPLETED` | `200` | 없음 | 만료 |
| `WITHDRAWAL_PENDING` + 잘못된 SocialAccount/task 상태 | 오류 | `409` | 없음 | 성공 계약으로 보장하지 않음 |
| `WITHDRAWN` + `LINKED/UNLINK_PENDING` | 오류 | `409` | 없음 | 성공 계약으로 보장하지 않음 |
| 인증 없음 | 필터 오류 | `401` | 없음 | 없음 |
| JWT 사용자 DB 미존재 | 필터 오류 | `401` | 없음 | 없음 |

중복 DELETE는 최초 결과를 그대로 반환하며 `occurredAt`, `withdrawalReason`, `withdrawnAt`, `KakaoUnlinkTask.createdAt`, `retryCycle`, `attemptCount`와 `AccountRejoinBlock` 만료 시각을 변경하지 않는다. 새 task, block, 가입 세션 취소, 익명화, event 또는 프로필 이미지 durable deletion 요청을 만들지 않는다.

### 13.4 Security 예외 계약

현재 중앙 인증 경로는 JWT 검증 뒤 User를 조회해 `WITHDRAWAL_PENDING`과 `WITHDRAWN`의 보호 API 접근을 차단한다. PR 7은 서비스 수준 멱등성에 HTTP 요청이 도달할 수 있도록 정확히 `DELETE /api/v1/users/me`에만 상태 정책 예외를 둔다.

| User 상태 | `DELETE /api/v1/users/me` | `GET/PATCH /api/v1/users/me`와 다른 보호 API |
|---|---|---|
| `ACTIVE/SUSPENDED` | 허용 | 기존 정책 유지 |
| `WITHDRAWAL_PENDING` | 허용 | `403 WITHDRAWAL_PENDING_USER` |
| `WITHDRAWN` | 허용 | `403 WITHDRAWN_USER` |

이 예외는 인증 생략이나 `permitAll`이 아니다. Authorization header, JWT 서명·만료 검증, User DB 조회와 JWT subject·User 일치 검증을 그대로 수행한 다음 HTTP method와 정확한 path가 일치할 때만 상태 접근 정책을 예외 처리하고 인증 principal을 만든다. Access Token은 stateless JWT이므로 발급 이후 별도의 폐기 상태를 서버에서 조회하지 않는다. 로그인, refresh token 재발급, GET/PATCH와 다른 보호 API는 예외에 포함하지 않는다. 기존 access token 자체가 만료되거나 잘못됐다면 DELETE도 `401`이다.

### 13.5 Refresh Token과 Cookie 계약

DB Refresh Token 전량 삭제는 `AccountTerminationService` 책임이다. PR 7 Controller는 `RefreshTokenRepository`를 직접 호출하지 않는다. 브라우저 Refresh Cookie 만료는 HTTP adapter 책임이며 기존 `RefreshTokenCookieProvider.clear()`를 재사용한다.

| 속성 | 삭제 Cookie 값 |
|---|---|
| Name | `gather_refresh_token` |
| Value | 빈 문자열 |
| Max-Age | `0` |
| Path | `/api/v1/auth` |
| HttpOnly | `true` |
| SameSite | `Lax` |
| Secure | `GATHER_REFRESH_COOKIE_SECURE` 설정값 |
| Domain | 설정하지 않음 |

발급 Cookie와 삭제 Cookie의 Path와 Domain은 동일해야 한다. 신규·멱등 `COMPLETED`와 신규·멱등 `ACCEPTED`의 모든 성공 응답에 만료 Cookie를 반환한다. 상태 충돌이나 인증·서버 오류에서는 Cookie 만료를 성공 계약으로 보장하지 않는다.

### 13.6 공개 오류 계약

| 상황 | HTTP | Error code |
|---|---:|---|
| 인증 정보 없음 | `401` | `UNAUTHORIZED` |
| 토큰 만료 | `401` | `EXPIRED_TOKEN` |
| 잘못된 토큰 | `401` | `INVALID_TOKEN` |
| JWT 사용자가 DB에 없음 | `401` | `INVALID_TOKEN` |
| User/SocialAccount/task 상태 불일치 | `409` | `ACCOUNT_TERMINATION_STATE_CONFLICT` |
| 예상하지 못한 서버 오류 | `500` | `INTERNAL_SERVER_ERROR` |

request body를 받지 않으므로 PR 7 탈퇴 API에 사유 enum validation 오류 계약을 추가하지 않는다. API 문서에는 body가 없다고 명시하며, 잘못된 body가 전송된 경우의 Jackson 동작을 공개 핵심 계약으로 확대하지 않는다.

### 13.7 Worker 가용성과 접수 계약

공개 탈퇴 API는 worker·Admin client 가용성이나 `KakaoUnlinkWorkerControl` 상태를 사전 확인하지 않는다. worker disabled, Admin client 비활성 또는 `CONFIGURATION_BLOCKED` 상태에서도 카카오 탈퇴 요청을 `WITHDRAWAL_PENDING`, `UNLINK_PENDING`, `KakaoUnlinkTask.PENDING`으로 commit하고 `202 Accepted`를 반환한다.

`DEAD + CONFIGURATION` task는 설정 복구 뒤 one-shot resume command로 requeue한다. 차단 중 새로 생성된 `PENDING` task는 resume 대상이 아니며 control이 `ACTIVE`가 되면 별도 requeue 없이 자연스럽게 claim된다. 공개 응답에는 worker disabled·blocked 여부나 backlog 정보를 노출하지 않는다.

### 13.8 OpenAPI와 프론트 계약

Springdoc/OpenAPI에는 Bearer 인증 필수, request body 없음, `200 COMPLETED`, `202 ACCEPTED`, `401`, `409`, `500`, `Set-Cookie`, 멱등 재호출과 카카오 비동기 처리 의미를 문서화한다.

- `200 COMPLETED`: 탈퇴 완료로 처리하고 Access Token을 폐기한 뒤 즉시 로그아웃해 로그인 또는 탈퇴 완료 화면으로 이동한다.
- `202 ACCEPTED`: durable 탈퇴 접수 완료로 처리하고 Access Token을 폐기한 뒤 즉시 로그아웃한다. 카카오 연결 해제가 비동기임을 안내하되 별도 polling은 하지 않는다.

### 13.9 공개 상태 조회

`[Gather 확정 정책]` 탈퇴 상태 polling endpoint는 제공하지 않는다. 탈퇴 접수 뒤 사용자는 로그아웃·접근 차단되고, polling token을 새로 만들면 인증·내부 task 노출 위험이 생긴다. 운영 상태는 DB task, metric과 구조화 로그로 확인한다.

### 13.10 외부 응답 금지 정보

task ID, socialAccount ID, generation, attempt count, retry cycle, next attempt, worker control 상태, 내부 `DEAD/STALE`, Kakao code·회원번호, provider ID, 암호문·HMAC·claim token과 raw Kakao 응답은 성공·오류 응답 또는 예시에 포함하지 않는다.

## 14. Migration 계획

현재 PR 6 구현 브랜치의 migration 기준은 다음과 같다.

| 기준 | 최신 번호 |
|---|---|
| 현재 local branch | `V41` |
| PR 5 소유 migration | `V39__add_withdrawal_pending_and_create_kakao_unlink_task.sql` |
| PR 6 소유 migration | `V41__add_kakao_unlink_worker_control.sql` |

`[Gather 확정 정책]` PR 5의 사용자 상태, PHONE identity guard와 task foundation은 V39가 소유한다. 후속 PR은 구현 또는 restack 직전에 최신 `origin/develop` migration을 확인해 다음 사용 가능한 번호를 결정한다.

기존·merge된 migration 수정·rename을 금지하고 PR마다 자신이 소유한 schema만 새 migration으로 변경한다. checksum 문제와 신규 schema 설계를 분리한다.

### 14.1 PR별 소유권

#### PR 4

migration 없음.

#### PR 5

- `users.status`가 `WITHDRAWAL_PENDING`을 수용하도록 enum/check 정의 변경
- `account_identity_guard` 테이블과 `(identity_type, key_version, identity_hash)` unique 제약
- `kakao_unlink_task` 테이블, 제약, 인덱스, FK
- task unique/index/lease 컬럼
- AccountTermination에 필요한 최소 schema

User status가 VARCHAR이면 DB constraint 변경이 실제 필요한지 먼저 확인한다. 하나의 entity 생성 DDL은 가능한 한 하나의 migration으로 유지한다.

#### PR 6

- unlink 성공 뒤 `social_account.provider_user_id`, `provider_user_id_ciphertext`, `encryption_key_version`을 null 처리할 수 있는 schema
- singleton Kakao unlink worker control table 또는 동등한 row와 `ACTIVE`/`CONFIGURATION_BLOCKED` 상태
- `KakaoUnlinkTask.retry_cycle`과 동등한 운영 retry cycle 감사 필드
- 필요한 enum/check constraint와 finalizer·retention metadata 정렬

현재 V39에는 claim token, `claimedBy`, `claimedAt`, `leaseExpiresAt`, `attemptCount`, `lastAttemptAt`, `nextAttemptAt`, `lastHttpStatus`, `lastKakaoCode`, `lastErrorType`, `completedAt`, `version`, due/lease index가 이미 포함되어 있으므로 해당 foundation은 재생성하지 않는다.

PR 6의 위 변경은 `V41__add_kakao_unlink_worker_control.sql`에 구현됐다. 기존 migration은 수정하거나 rename하지 않는다.

#### PR 7

신규 migration 없음. User status·withdrawalReason·withdrawnAt, SocialAccount unlink 상태, `KakaoUnlinkTask`, `AccountRejoinBlock`, `AccountIdentityGuard`와 `KakaoUnlinkWorkerControl`이 이미 존재하며 PR 7은 Controller, response DTO, Security, Cookie, Springdoc/OpenAPI와 테스트만 변경한다. PR 7은 V39·V41을 포함한 기존 migration을 수정하거나 rename하지 않는다.

### 14.2 데이터 전환

- 기존 `WITHDRAWN` 사용자는 pending으로 되돌리지 않는다.
- 기존 `UNLINK_PENDING` 행이 실제 환경에 존재하는지 배포 전 조회한다.
- 존재한다면 식별자 복호화 가능 여부와 generation을 검증해 task backfill 또는 운영 수동 처리를 선택한다.
- legacy 평문 provider ID의 null 허용 변경은 lazy backfill·조회 fallback과 충돌하지 않는지 확인한다.
- enum 컬럼이 DB native enum인지 문자열인지 실제 migration과 운영 DB에서 확인한다.

### 14.3 롤백과 배포 순서

1. 기존 애플리케이션이 새 enum 값을 읽지 않는 상태에서 pending row가 생성되지 않도록 expand-first 배포를 검토한다.
2. schema와 읽기 호환 코드를 먼저 배포한다.
3. worker를 배포하되 endpoint 공개 전 관측과 수동 task로 검증한다.
4. endpoint를 공개한다.
5. 식별자 컬럼 축소·제거는 모든 fallback 코드가 제거된 뒤 별도 migration으로 수행한다.

## 15. PR 분할

### PR 4 — 카카오 Admin unlink client

| 항목 | 내용 |
|---|---|
| 목적 | 공식 계약을 캡슐화한 전용 Admin unlink client와 typed result를 만든다. |
| 포함 | `KakaoAdminProperties`, 전용 client, connect 2초/read 5초, client retry 없음, form 요청, 응답 ID 검증, 공식 code와 unknown 오류 typed 분류, secret 마스킹, client 테스트, 콘솔 체크리스트 |
| 제외 | DB task, worker, 사용자 상태 전이, 탈퇴 API, webhook |
| 선행 조건 | Admin key 보관·주입 방식과 unlink API 권한 확인 |
| 완료 조건 | 공식 요청 형식과 성공·대표 오류·timeout·malformed response가 typed result로 표현되고 기존 `KakaoApiClient`의 사용자용 예외와 분리됨 |
| 테스트 | 성공 ID 일치/불일치, `-101`, `-10`, `-401`, `-3`, unknown 4xx/5xx, 2xx malformed body, timeout 값, 내부 retry 부재, 민감정보 로그 부재 |
| migration | 없음 |

### PR 5 — 탈퇴 접수와 durable task 기반

| 항목 | 내용 |
|---|---|
| 목적 | 외부 호출 없이 탈퇴 요청을 원자적으로 접수하고 재시도 가능한 task를 남긴다. |
| 포함 | 일반 회원 동기 `WITHDRAWN`과 service `200` 결과, 카카오 회원 `WITHDRAWAL_PENDING`과 service `202` 결과, refresh token 전량 삭제, PHONE/KAKAO 7일 block, PHONE `account_identity_guard`, 가입 세션 취소, `UNLINK_PENDING`, task schema/entity/repository/enqueue, 중앙 상태 차단, 원자성·잠금 순서 |
| 제외 | 실제 Admin HTTP 호출, scheduler worker, public 탈퇴 API, webhook, future relink |
| 선행 조건 | PR 4의 typed result 계약, 개인정보 파기·재가입 제한의 미결정 항목 확인 |
| 완료 조건 | 중복·경쟁 요청에도 사용자 pending, 소셜 계정 pending, task 한 건이 함께 커밋되거나 함께 롤백됨 |
| 테스트 | 일반 ACTIVE/SUSPENDED의 `WITHDRAWN`, 카카오 ACTIVE/SUSPENDED의 pending, 유형별 service 결과, 중복, token 전량 삭제, 7일·경계·연장·cleanup 지연, access 차단, block/session/task 실패 rollback, ID ASC 잠금, PHONE 가입·탈퇴 guard 경쟁 |
| migration | `V39`: 사용자 상태, `account_identity_guard`, task foundation 소유 |

### PR 6 — worker와 finalizer

| 항목 | 내용 |
|---|---|
| 목적 | task를 lease 기반으로 실행하고 unlink 완료 후 탈퇴와 개인정보 파기를 확정한다. |
| 포함 | UTC Clock 정리, DB UTC claim/lease, scheduler 30초, batch 10, concurrency 1, lease 120초, claim token·`SKIP LOCKED`, preflight와 attempt reservation, generation `STALE`, 동일 generation `UNLINKED` local finalization, 조건부 `Retry-After`, full-jitter retry·cycle당 reservation 최대 12회, terminal disposition, DB 기반 configuration circuit breaker와 batch 중단, 운영 resume/retry cycle 기반, unlink 성공 시 task `SUCCEEDED`, SocialAccount `UNLINKED`·직접 provider identifier 제거, User `WITHDRAWN`·익명화, 필요한 프로필 이미지 durable 삭제, 구조화 로그와 필수 테스트 |
| 제외 | public 탈퇴 API와 Controller `200/202`·cookie 계약, 공개 수동 retry API, 범용 관리자 UI, webhook, future relink, HMAC keyring·rotation, cooldown 만료 HMAC cleanup, task 장기 retention, `AccountIdentityGuard` cleanup, JWT DB 조회 최적화, Redis/cache |
| 선행 조건 | PR 4, PR 5 |
| 완료 조건 | 중복 실행과 worker crash에도 같은 generation만 안전하게 완료되고 외부 HTTP 동안 DB lock을 유지하지 않으며 설정 오류가 전 인스턴스 claim을 지속적으로 차단함 |
| 테스트 | UTC 책임 분리, timeout·내부 retry 부재, Retry-After 형식·조건·cap, due·expired lease `SKIP LOCKED`, lease 회수, reservation invariant·crash·12회, configuration atomic block·batch 중단·resume, unknown 오류, ID 불일치, 동일 generation `UNLINKED` local finalization, 직접 identifier 제거·HMAC 유지, 카카오 익명화, HTTP 중 transaction 부재, MySQL `EXPLAIN ANALYZE` |
| migration | `V41`: 직접 identifier nullable, worker control, retry cycle 감사 필드와 필요한 constraint 소유 |

영구 실패에서는 User와 SocialAccount를 pending으로 유지한다. configuration 오류는 현재 task `DEAD`와 전역 worker `CONFIGURATION_BLOCKED`를 원자적으로 적용하고, 설정 수정·검증 뒤 권한 있는 운영 절차만 control resume와 새 retry cycle을 시작할 수 있다. PR 5에는 unlink worker나 finalizer가 포함되지 않는다.

### PR 7 — 탈퇴 API

| 항목 | 내용 |
|---|---|
| 목적 | 공개 회원 탈퇴 HTTP API를 기존 `AccountTerminationService` 계약에 연결하는 adapter PR |
| 포함 | `DELETE /api/v1/users/me`, `SecurityUtil` 현재 사용자 ID, `WithdrawalReason.SELF` 고정, service 호출, `COMPLETED/ACCEPTED → 200/202`, `AccountTerminationResponse`, UTC `occurredAt`, 기존 helper를 이용한 refresh cookie 만료, pending/withdrawn DELETE 전용 Security 예외, Springdoc/OpenAPI·프론트 계약과 Controller/API/Security 테스트 |
| 제외 | `AccountTerminationService` 재구현, Kakao Admin API·S3 직접 호출, task·worker control·User·SocialAccount·재가입 block·Repository 직접 조작, 프로필 이미지 deletion task 직접 생성, polling·retry·admin·탈퇴 취소 API, 상세 탈퇴 설문, worker retry/resume 변경, webhook, future relink |
| 선행 조건 | PR 5, PR 6 |
| 완료 조건 | request body 없이 기존 service transaction을 commit한 뒤 결과·최초 시각을 응답하고, pending/withdrawn의 DELETE만 인증된 상태로 재호출할 수 있으며, 모든 성공·멱등 응답이 cookie를 만료하고 다른 보호 API 차단은 유지됨 |
| 테스트 | 일반·카카오 신규 및 멱등 결과, UTC 응답, cookie, DELETE 전용 Security 예외, worker disabled/blocked 접수, 상태 충돌, 내부정보 비노출, 외부 호출 분리, polling 부재와 OpenAPI 일치 |
| migration | 없음. V39·V41을 포함한 기존 migration 수정·rename 금지 |

PR 7 Controller에는 `@Transactional`을 사용하지 않는다. `AccountTerminationService`가 DB transaction을 소유하며 Controller는 서비스 정상 반환 뒤 HTTP status, response DTO와 `Set-Cookie`만 조립한다. Controller는 `RefreshTokenRepository`, `KakaoUnlinkTask`, `KakaoUnlinkWorkerControl`, User, SocialAccount, S3 또는 Kakao Admin client를 직접 조작하지 않는다.

worker disabled, Admin client 비활성 또는 control `CONFIGURATION_BLOCKED`여도 카카오 탈퇴를 `202`로 접수한다. 신규 task는 `PENDING` backlog로 durable하게 보존하며 control 복구 후 자연스럽게 처리한다. 이 운영 상태는 공개 응답에 포함하지 않는다.

#### PR 7 acceptance criteria

- 일반 회원 신규 탈퇴: body 없는 DELETE가 `200 COMPLETED`, UTC `occurredAt`과 만료 Cookie를 반환하고 User `WITHDRAWN`이 commit된다.
- 카카오 회원 신규 탈퇴: body 없는 DELETE가 `202 ACCEPTED`, UTC `occurredAt`과 만료 Cookie를 반환하고 User `WITHDRAWAL_PENDING`, SocialAccount `UNLINK_PENDING`, task 한 건이 commit된다. Controller 경로에서 Kakao Admin API는 호출하지 않는다.
- pending 멱등 요청: 유효한 같은 access token으로 재호출할 수 있고 최초 `occurredAt`을 유지한 `202`를 반환한다. task, retry cycle·attempt count, block과 durable deletion 요청은 변경하지 않는다.
- withdrawn 멱등 요청: 유효한 같은 access token으로 재호출할 수 있고 최초 `occurredAt`을 유지한 `200`을 반환한다. 재익명화나 프로필 이미지 삭제 요청 중복이 없다.
- Security: pending/withdrawn은 탈퇴 DELETE만 허용하고 GET/PATCH 및 다른 보호 API는 `403`, 인증 없는 DELETE와 유효하지 않은 token은 `401`이다.
- 상태 충돌: 허용되지 않은 User/SocialAccount/task 조합은 `409 ACCOUNT_TERMINATION_STATE_CONFLICT`이며 DB 변경이 없다.
- worker 운영 상태: worker disabled 또는 control `CONFIGURATION_BLOCKED`에서도 카카오 탈퇴를 `202`로 접수하고 task를 `PENDING` backlog에 보존한다.
- 외부 호출 분리: Controller 요청 처리 중 Kakao Admin API와 S3 API를 직접 호출하지 않는다.

### PR 8 — 외부 카카오 unlink webhook

| 항목 | 내용 |
|---|---|
| 목적 | 사용자가 카카오 측에서 연결을 끊는 외부 경로를 Gather 상태에 반영한다. |
| 포함 | GET/POST callback, primary Admin key 검증, 빠른 `200`, durable inbox, `app_id`·현재 연결 상태 확인, `user_id` HMAC 조회, 중복·stale webhook 방어, 개인정보 파기 정책 재사용 |
| 제외 | 카카오 직접 unlink 성공 통지로 webhook을 기대하는 설계, future relink, 운영 UI |
| 선행 조건 | PR 5의 상태·generation 모델, 카카오 콘솔 webhook 설정 |
| 완료 조건 | 같은 callback의 중복, 알 수 없는 사용자, pending outbound task와의 경쟁에서도 빠르고 멱등하게 응답함 |
| 테스트 | GET/POST, 검증 실패, 중복, unknown identity, task 경쟁, 3초 응답 경로 |
| migration | inbound event dedup 테이블을 채택할 경우에만 추가 |

### 확정 정책 테스트 전략

#### PR 5

- 일반 ACTIVE/SUSPENDED → `WITHDRAWN`, service 결과의 `200` 의미
- 카카오 ACTIVE/SUSPENDED → `WITHDRAWAL_PENDING`, service 결과의 `202` 의미
- 중복 일반 탈퇴와 중복 pending 탈퇴의 부수효과 없음
- 모든 refresh token 삭제, pending access token 차단, DELETE 외 보호 API 차단
- PHONE/KAKAO block 정확히 7일, `now == expiresAt` 허용, 기존 block 연장
- cleanup 지연이 차단을 연장하지 않음
- PHONE guard upsert·`FOR UPDATE`와 일반·카카오 가입/탈퇴의 동일 identity 직렬화
- 일반 회원가입과 탈퇴의 두 선점 순서에서 중복 또는 재가입 제한 domain error와 최종 불변식 유지
- 가입 세션 ID ASC 잠금과 `cancel()`
- block/session/task 실패 시 전체 rollback과 task·`UNLINK_PENDING` 원자성

#### PR 6

시간:

- application 공통 `Clock.systemUTC()`와 단계별 `claimNow`·`attemptNow`·`resultNow` 분리
- DB UTC due/expired lease 판정과 JVM/DB clock 차이 경계
- 기존 `DATETIME(6)` UTC round-trip과 과거 KST wall-clock 표본 검증

Retry-After와 retry:

- delta-seconds, RFC 1123 HTTP-date, zero, 과거 시각, malformed, overflow와 다중 값
- `429/5xx` 조건, permanent Kakao code 우선, network exception 미적용과 6시간 cap
- unknown 4xx `DEAD_UNKNOWN`, unknown 5xx retry, malformed 2xx `DEAD_RESPONSE`, ID 불일치 `DEAD_SECURITY`
- full jitter 범위와 6시간 상한

Reservation과 fencing:

- attempt reservation commit 뒤 HTTP 호출, stale claim token과 reservation 중 invariant 변경 거부
- reservation commit 뒤 crash, 12번째 reservation 뒤 lease 만료와 추가 HTTP 금지
- 실제 HTTP 호출 횟수 `<= attemptCount`, retry cycle당 reservation 12회
- due·expired lease DB UTC `SKIP LOCKED`, 두 worker 중복 claim 방지와 HTTP 중 transaction 비활성

Configuration failure:

- 현재 task `DEAD`와 worker control `CONFIGURATION_BLOCKED`의 원자 전이
- 같은 batch 즉시 중단과 다음 scheduler claim 0건
- 애플리케이션 재시작 뒤 차단 유지와 다중 인스턴스 공유
- 설정 복구 뒤 `ACTIVE`, configuration 원인 `DEAD` task만 명시적 requeue, 새 retry cycle 감사

동일 generation `UNLINKED` local finalization:

- HTTP 0회, `markUnlinked()` 재호출 없음과 기존 `unlinkedAt` 유지
- 직접 identifier 제거, provider HMAC 유지, User `WITHDRAWN`, task `SUCCEEDED`
- finalizer 중간 실패 시 전체 rollback

Cleanup 제외:

- PR 6에서 provider HMAC·provider key version, `AccountIdentityGuard`와 활성 `AccountRejoinBlock` 유지
- HMAC 제거는 후속 retention cleanup 계약으로만 검증

MySQL 8에서 `EXPLAIN ANALYZE`, lock wait와 deadlock도 검증한다.

#### PR 7

- Controller/API:
  - body 없는 일반 회원 신규 DELETE는 `200 COMPLETED`와 UTC `occurredAt`
  - body 없는 카카오 회원 신규 DELETE는 `202 ACCEPTED`와 UTC `occurredAt`
  - pending 멱등 DELETE는 최초 시각을 유지한 `202`, withdrawn 멱등 DELETE는 최초 시각을 유지한 `200`
  - 인증 없음은 `401`, 허용되지 않은 User/SocialAccount/task 조합은 `409`
  - 모든 신규·멱등 성공 결과에서 기존 속성과 같은 refresh cookie 만료
  - 응답에 task·worker·provider 내부정보가 없고 공개 status polling endpoint가 없음
- Security:
  - `ACTIVE/SUSPENDED/WITHDRAWAL_PENDING/WITHDRAWN`의 인증된 DELETE 허용
  - pending/withdrawn의 GET/PATCH와 다른 보호 API는 기존 `403` 유지
  - 만료되었거나 서명이 유효하지 않은 JWT는 DELETE 예외 없이 `401`
- Integration:
  - DB Refresh Token 전량 삭제, 카카오 task 정확히 한 건, 반복 DELETE에서 task·durable deletion 중복과 block 연장 없음
  - worker disabled 또는 `CONFIGURATION_BLOCKED` 중에도 `202`와 `PENDING` backlog 보존
  - Controller 요청 중 Kakao Admin API와 S3 직접 호출 없음
  - Springdoc/OpenAPI와 프론트 계약 일치

일반 회원 신규 탈퇴는 응답 전에 User `WITHDRAWN`, 카카오 신규 탈퇴는 User `WITHDRAWAL_PENDING`, SocialAccount `UNLINK_PENDING`과 task 한 건이 commit돼야 한다. pending/withdrawn 재요청은 기존 access token이 유효한 동안 Controller까지 도달하고 각각 `202/200`을 반환하되 `occurredAt`, withdrawal reason, task 시각·retry 상태, block 만료와 프로필 이미지 삭제 요청을 변경하지 않는다.

기존 `AccountTerminationService`와 worker 테스트에서 이미 검증한 내부 상태 전이·retry·finalizer 행렬을 Controller 테스트에 불필요하게 복제하지 않는다. PR 7 테스트는 HTTP mapping, 인증 경계, cookie, adapter 위임과 실제 application service 연결에 집중한다.

PR 4를 client 단독으로 먼저 분리하면 HTTP 계약과 오류 분류를 worker 코드와 독립적으로 검증할 수 있다. PR 7은 worker 가용성과 공개 접수 가용성을 분리해 사용자 탈퇴 의사를 먼저 durable하게 보존하고, backlog와 pending 체류 시간은 운영 지표로 관리한다.

## 16. 후속 작업

PR 4~7의 사용자 요청 접수, 일반 회원 동기 탈퇴, 카카오 비동기 unlink와 finalizer, 공개 HTTP API와 멱등성 구현은 완료됐다. 남은 작업은 기능 미완성이 아니라 다음 운영 준비와 별도 후속 범위다.

### 16.1 운영 전 준비

- 운영 환경변수와 Admin key 보관·최소 권한·비상 폐기 절차 최종 확인
- 운영 DB의 V39/V41 적용 상태와 `KakaoUnlinkWorkerControl` singleton 확인
- 기존 `PENDING`·`PROCESSING`·`DEAD` task와 `WITHDRAWAL_PENDING` 사용자 backlog 확인
- Admin client를 먼저 활성화해 설정을 검증한 뒤 worker를 단계적으로 활성화

### 16.2 운영 안정성 후속

#### Kakao unlink 운영 모니터링 PR B-1 persistence foundation

PR B-1은 운영 모니터링의 저장·동시성 기반만 제공한다. 애플리케이션 기동 시 scheduler, heartbeat write, detector, Discord·Email network I/O를 실행하지 않는다. 실제 heartbeat 기록과 detector·suppression reconciliation은 B-2, delivery claim·retry와 Discord·Email 발송은 B-3에서 활성화한다.

- monitor lease acquire·complete·fail은 각각 독립 `REQUIRES_NEW` transaction으로 commit한다. 상위 transaction에서 호출되더라도 control row lock이 이후 incident별 `REQUIRES_NEW` transaction으로 넘어가지 않게 한다.
- 한 scan의 lock order는 `MonitorControl → Incident → Delivery`로 고정한다. incident 관측·해소·reminder·suppression 변경은 lease의 owner, token, scan sequence를 DB UTC 기준으로 다시 검증한다.
- singleton `MonitorControl` lock은 B-1에서 incident mutation을 의도적으로 직렬화한다. B-2 활성화 후 scan 처리량을 측정하고, 병목이 확인되기 전에는 fencing 정확성을 위해 이 경계를 완화하지 않는다.
- fingerprint 확보는 observation의 전체 초기값을 가진 atomic upsert 뒤 `FOR UPDATE`로 최신 row를 읽어 lifecycle을 수렴시킨다. fingerprint별 transaction을 분리해 한 incident 실패가 scan 전체 rollback으로 전파되지 않게 한다.
- 이미 OPEN인 incident의 reminder 시각은 기존 값이 `NULL`일 때만 보충한다. suppression 원인은 알림 가능한 OPEN incident만 허용해 chain·cycle을 차단하고, 원인 occurrence가 끝난 stale suppression은 별도 조회해 B-2에서 해제한다. 해제 grace인 `notificationEligibleAt` 이전에는 reminder 후보로 선택하지 않는다.
- SUPPRESSED incident에는 INITIAL·ESCALATED delivery를 생성하지 않는다. RECOVERED는 같은 occurrence와 channel에서 문제 알림이 한 번 이상 성공한 경우에만 만들며, 재발·중복·성공 이력 부재는 예외가 아닌 명시적 결과로 반환한다.
- incident의 safe details와 delivery payload는 같은 Spring `ObjectMapper`를 사용하는 typed JSON으로 직렬화한다. fingerprint는 alert type별 factory만 공개하고 provider ID, email, token, 원문 응답을 허용하지 않는다.
- Hibernate JSON format mapper 설정은 애플리케이션 전역에 적용되지만 현재 JSON entity는 monitoring incident와 delivery뿐이다. 향후 다른 JSON entity를 추가할 때 동일 mapper 호환성 테스트를 선행한다.
- MySQL 8.4가 `AUTO_INCREMENT` 컬럼을 참조하는 CHECK를 허용하지 않아 self-suppression은 DB CHECK가 아니라 domain validation과 row-lock reconciliation에서 차단한다.
- synthetic singleton은 일반 운영 incident count, reminder, resolve, suppression, detector reconciliation에서 제외하며 TEST delivery만 허용한다.

- task `DEAD`, 처리 지연, lease 회수 횟수, 오류 code, backlog와 pending 체류시간 metric·alert
- configuration 외 원인의 `DEAD` task를 위한 감사 가능한 내부 조회·재시도·보류 절차
- 만료된 `SocialSignupSession`과 완료된 unlink task retention cleanup
- 로컬 상태와 카카오 연결 상태의 reconciliation 절차
- 대량 backlog에 대한 rate limit, backpressure, graceful shutdown
- task와 개인정보 파기 이력의 보존 기간 및 파티셔닝

### 16.3 개인정보·보안 후속

- cooldown 만료 후 providerUserKey HMAC·provider key version retention cleanup
- HMAC/AES key rotation과 과거 keyring 지원
- legacy 평문 provider ID 제거 migration
- 프로필 이미지 외의 외부 저장소 개인정보 정리
- 관련 도메인 데이터의 보존·익명화·삭제 정책
- `account_identity_guard` 증가량과 안전한 cleanup 전략(재가입 제한 block 보관기간 cleanup은 구현 완료)

### 16.4 선택 기능과 의도적 범위 밖

- 사용자 설문형 상세 탈퇴 사유가 필요할 때 내부 `WithdrawalReason`과 분리된 `WithdrawalSurveyReason` 모델·저장·공개 DTO 설계
- 외부 카카오 unlink webhook과 장애·재전송·서명 또는 신뢰 경계 운영 검증
- future social account relink 프로토콜
- 다중 provider 및 마지막 인증수단 제거 정책
- 탈퇴 완료 polling API, 탈퇴 취소와 공개 관리자 retry API는 현재 확정 범위에 포함하지 않는다.

## 17. 운영·법적 검토

기술 정책과 구현 기본값은 확정됐으며 다음 항목만 운영 공개 전 별도로 검토한다.

1. `[운영·법적 검토]` 7일 재가입 제한의 서비스 목적과 약관·개인정보처리방침 공개 문구
2. `[운영·법적 검토]` 게시글·모임·활동 기록 등 타 도메인별 실제 보존 기간
3. `[운영·법적 검토]` 법정 보존 또는 분쟁 대응 데이터의 일반 서비스 데이터와 분리 보관
4. `[운영·법적 검토]` 백업·스냅샷 개인정보 파기와 복원 시 파기 상태 재적용 절차
5. `[운영·법적 검토]` DEAD 운영 알림 채널과 실제 서비스·개인정보 담당자

timeout, lease, scheduler cadence, batch size, worker concurrency와 retention period는 현재 값을 확정 기본값으로 사용하되 운영 지표에 따라 configuration으로 조정할 수 있다. 값 변경은 정책 폐기가 아니라 운영 tuning이며 관련 timeout·lease·처리량을 함께 검토한다.

## 18. 폐기된 설계

다음 설계는 구현 기준으로 사용하지 않는다.

1. `SocialAccount`의 `UNLINK_PENDING` 행을 scheduler가 직접 훑어 queue로 사용하는 방식
2. 재시도 attempts, next attempt, lease, claim owner 없이 상태 하나로 내구성을 표현하는 방식
3. 탈퇴 트랜잭션의 after-commit listener만 믿고 task를 남기지 않는 방식
4. 카카오 HTTP 호출을 탈퇴 접수 또는 결과 반영 DB 트랜잭션 안에서 수행하는 방식
5. 모든 4xx를 “이미 해제됨”으로 간주하고 `SocialAccount`를 삭제하는 방식
6. `-101` 외의 미분류 오류도 성공으로 간주하는 방식
7. generation과 claim token을 확인하지 않고 늦은 worker 결과를 적용하는 방식
8. 카카오 unlink가 완료되기 전에 `WITHDRAWN` 확정과 개인정보 파기를 끝내는 방식
9. 카카오 unlink 재시도를 위해 탈퇴 후 provider ID를 무기한 보관하는 방식
10. task에 provider ID 평문·암호문을 복제하는 방식
11. 가입 세션을 잠금·상태 검증 없는 bulk update로 취소하는 방식
12. 서비스가 직접 unlink API를 호출한 뒤 같은 건의 webhook이 올 것이라고 기대하는 방식
13. 기존 OAuth용 `KakaoApiClient`의 사용자-facing `BusinessException`을 worker retry 분류에 그대로 사용하는 방식
14. 카카오 `LINKED` 계정이 없는 일반 회원까지 `WITHDRAWAL_PENDING`과 `202`로 처리하는 방식
15. 만료된 `AccountRejoinBlock` row가 cleanup될 때까지 재가입을 계속 차단하는 방식
16. PHONE 가입과 탈퇴가 공유 guard 없이 User unique 제약과 block snapshot read만으로 경쟁을 해결하는 방식
17. 활성 PHONE block의 non-locking 선조회만으로 신규 User 생성을 최종 허용하는 방식
18. `AccountRejoinBlock` 만료 또는 cleanup과 함께 `account_identity_guard`를 삭제하는 방식
19. HMAC keyring과 previous-key lookup 없이 7일 경과만을 근거로 HMAC secret 또는 key version을 회전하는 방식
20. claim과 lease eligibility를 JVM clock만으로 판단하는 방식
21. `attemptCount`를 결과 transaction에서 증가시키거나 실제 HTTP 응답 횟수로 정의하는 방식
22. `PERMANENT_CONFIGURATION` 뒤 다음 task를 계속 처리하거나 인메모리 pause만으로 전역 차단을 표현하는 방식
23. 설정 오류로 막힌 worker를 자동으로 resume하거나 감사 가능한 새 retry cycle 없이 `attemptCount`를 초기화하는 방식
24. 동일 generation의 이미 `UNLINKED`인 계정을 무조건 `STALE`로 종료하거나 Admin API를 다시 호출하는 방식
25. PR 6 finalizer가 cooldown 종료를 판단해 providerUserKey HMAC과 key version까지 제거하는 방식

이 목록과 충돌하는 과거 문서, 이슈 설명 또는 코드 주석은 본 문서와 카카오 공식 계약을 기준으로 재검토한다.
