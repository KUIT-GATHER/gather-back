# 계정 탈퇴 및 카카오 연결 해제 설계

- 조사 시작 기준 브랜치: `feature/auth-kakao-signup-session`
- 현재 작업 브랜치: `feature/auth-kakao-admin-unlink-client`
- 기준 HEAD: `6fb631d`
- 코드 기준: PR 3 및 현재 로컬 working tree
- 공식 문서 확인일: 2026-07-30
- 문서 상태: PR 4 구현 전 설계안

이 문서의 문장은 다음 네 가지 근거 수준으로 구분한다.

- `[공식 계약]`: 카카오 공식 문서가 직접 규정하는 외부 계약
- `[Gather 확정 정책]`: 현재 코드·적용 스키마 또는 이번 팀 결정으로 확정된 Gather의 계약
- `[권장 구현]`: 확정 정책을 안전하게 구현하기 위한 기술 권장사항
- `[운영·법적 검토]`: 기술 구현은 진행할 수 있지만 운영 공개 전 별도 검토가 필요한 사항

## 1. 문서 목적

이 문서는 Gather 회원 탈퇴와 카카오 연결 해제를 하나의 동기 HTTP 요청으로 취급하지 않고, 다음 조건을 함께 만족하는 내구성 있는 처리 흐름으로 정의한다.

1. 탈퇴 요청이 수락되는 즉시 서비스 접근을 차단한다.
2. 카카오 Admin 연결 해제를 재시도 가능한 durable task로 기록한다.
3. 외부 HTTP 호출은 데이터베이스 트랜잭션 밖에서 수행한다.
4. 재연결과 과거 task의 경쟁 상태를 `generation`으로 차단한다.
5. 카카오 연결 해제가 완료된 뒤 서비스 탈퇴를 확정하고 개인정보를 파기한다.
6. 중복 요청, worker 중단, lease 만료, webhook 도착에도 멱등성을 유지한다.

이 문서는 PR 4부터 PR 8까지의 구현 경계를 정하지만, 현재 세션에서는 구현하지 않는다.

## 2. 현재 구현 상태

### 2.1 구현 완료

| 영역 | 현재 상태 |
|---|---|
| 사용자 탈퇴 기반 | `UserStatus`는 `ACTIVE`, `SUSPENDED`, `WITHDRAWN`을 제공한다. `User.withdraw()`와 `User.anonymize()`가 존재한다. |
| 사용자 익명화 | 이름, 생년월일, 성별, 이메일, 비밀번호, 소개, 프로필 이미지 키, 활동 지역, 관심 카테고리 등을 제거하고 전화번호·닉네임을 대체값으로 변경한다. |
| 소셜 계정 식별자 보호 | `SocialAccount`는 HMAC 조회 키, AES-GCM 암호문, 키 버전, legacy 평문 식별자를 보관한다. |
| 소셜 계정 상태와 세대 | `LINKED`, `UNLINK_PENDING`, `UNLINKED`, `generation`, 낙관적 잠금 버전을 제공한다. `relink()`는 `generation`을 증가시킨다. |
| 가입 세션 | `SocialSignupSession`은 DB에 영속화되며 `PENDING`, `CONSUMED`, `CANCELLED` 상태를 가진다. 토큰은 opaque token의 SHA-256 해시로 조회한다. |
| 가입 세션 동시성 | 동일 identity의 pending 세션을 ID 순서로 비관적 잠금하고, 선택한 세션 소비와 나머지 세션 취소를 한 트랜잭션에서 처리한다. |
| 재가입 제한 스키마 | `AccountRejoinBlock` 엔티티와 저장소, `PHONE`/`KAKAO` 식별자 유형, 만료 시각 및 출처 사용자 ID가 존재한다. |
| 비동기 정리 선례 | 프로필 이미지 정리용 after-commit listener, retry row, scheduler가 존재한다. |

### 2.2 기반만 존재하고 연결되지 않은 부분

| 영역 | 현재 한계 |
|---|---|
| `User.withdraw()` | 즉시 `WITHDRAWN`으로 전환한다. 카카오 연결 해제 완료 전의 중간 상태는 표현하지 못한다. |
| `User.anonymize()` | 개별 사용자 필드는 정리하지만 관련 도메인 데이터 전체의 개인정보 파기 범위는 정의하지 않는다. |
| `SocialAccount.markUnlinkPending()` / `markUnlinked()` | 엔티티 전이만 존재하며 서비스, task, worker, API에 연결되지 않았다. 현재 카카오 인증 흐름은 `LINKED`가 아닌 기존 계정의 로그인을 거부한다. |
| `SocialAccount.relink()` | 세대 증가 기능은 있으나 실제 재연결 흐름이 아직 없다. |
| `AccountRejoinBlock` | 생성, 로그인·가입 시 조회, 만료 정책이 실제 인증 흐름에 연결되지 않았다. |
| `KakaoApiClient` | OAuth token과 사용자 정보 조회용이며 connect timeout 3초, read timeout 5초다. 429·4xx·5xx를 사용자-facing `BusinessException`으로 바꾸므로 Admin unlink worker의 typed error와 민감정보 로그 정책에 적합하지 않다. |
| 스케줄링 기반 | scheduler는 활성화되어 있지만 카카오 task claim, lease, recovery 구현은 없다. |

### 2.3 미구현

- 탈퇴 API
- `WITHDRAWAL_PENDING` 사용자 상태
- 탈퇴 접수 application service
- 카카오 Admin key 설정과 전용 unlink client
- `KakaoUnlinkTask` 엔티티·테이블·저장소
- task claim, lease, retry, stale 판정, finalizer
- 탈퇴 접수 시 refresh token 폐기
- pending 사용자의 기존 access token 차단
- 탈퇴 시 `AccountRejoinBlock` 생성과 가입·로그인 차단
- 외부 카카오 연결 해제 webhook
- 운영자 재처리 및 `DEAD` task 관측 기능

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
- `AccountTerminationService`, `UserWithdrawnEvent`, unlink worker, 탈퇴 API가 이미 구현되었다는 설명
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
2. `[Gather 확정 정책]` worker의 claim과 결과 반영은 각각 짧은 트랜잭션이며 카카오 HTTP 호출은 그 사이의 트랜잭션 밖에서 실행한다.
3. `[Gather 확정 정책]` `KakaoUnlinkTask`가 retry와 운영 이력의 유일한 source of truth다. `SocialAccount`는 계정 상태를 표현할 뿐 queue가 아니다.
4. `[Gather 확정 정책]` worker는 at-least-once 실행을 전제로 멱등하게 동작한다.
5. `[Gather 확정 정책]` 모든 task는 생성 당시 `SocialAccount.generation`을 캡처한다. generation·대상 상태 invariant가 다르면 호출하지 않고 `STALE`로 종료하며, 결과 반영 직전 잠금 아래 다시 검증한다. claim 소유권이 없으면 task를 변경하지 않고 실행을 중단한다.
6. `[Gather 확정 정책]` 미분류 4xx나 파싱 실패를 성공으로 간주하지 않는다.
7. `[Gather 확정 정책]` pending 사용자는 새 로그인뿐 아니라 이미 발급된 access token으로도 모든 보호 API를 사용할 수 없다.
8. `[권장 구현]` 로그에는 Admin key, 복호화한 카카오 회원번호, 암호문, 사용자 토큰, 응답 원문을 남기지 않는다.
9. `[권장 구현]` 법적 파기 의무와 재가입 제한은 별개의 목적으로 설계한다.
10. `[권장 구현]` 기존 legacy 호환 범위를 제외하고 raw provider user ID를 새 컬럼, task, 로그에 평문으로 추가 저장하지 않는다.

### 4.1 확정 정책 요약

| 정책 | 확정 내용 | 해결하는 문제 | 권장 구현 위치 | 주요 transaction·동시성 요구사항 | 필수 테스트 | 담당 PR |
|---|---|---|---|---|---|---|
| 회원 유형별 탈퇴 상태 | `[Gather 확정 정책]` 일반 회원은 `ACTIVE/SUSPENDED → WITHDRAWN`, 카카오 `LINKED` 회원만 `WITHDRAWAL_PENDING → WITHDRAWN`을 사용한다. | 외부 unlink가 없는 일반 회원을 불필요하게 pending에 두지 않는다. | `User`, `AccountTerminationService`, worker finalizer | 일반 회원은 접수 트랜잭션에서 완료, 카카오 회원은 task와 원자적으로 접수 | 일반·카카오 두 시작 상태, 중복 요청, `DEAD` 시 카카오 pending 유지 | 주 PR 5, 검증 PR 6·7 |
| 회원 유형별 API 응답 | `[Gather 확정 정책]` 일반 회원은 최종 완료 `200`, 카카오 회원은 durable 접수 `202`다. | 완료와 비동기 진행을 응답에서 구분한다. | `UserController`, service result, 응답 DTO, OpenAPI | 응답 전 각 유형의 접수/완료 트랜잭션 커밋 | 일반 `200`, 카카오 `202`, 중복 상태별 동일 응답 | PR 7 |
| pending 사용자 접근 차단 | `[Gather 확정 정책]` 로그인·가입 세션·relink·새 인증 세션을 모두 차단한다. | 탈퇴 접수 직후 서비스 재진입을 막는다. | 중앙 인증 경로, 카카오 로그인·가입 세션 경계 | 개별 Controller 검사 금지, 가입 제출 트랜잭션에서도 재검증 | 일반·카카오 로그인, 세션 발급·제출 차단 | 주 PR 5, API 검증 PR 7 |
| Refresh Token 전체 폐기 | `[Gather 확정 정책]` 접수 트랜잭션에서 사용자 전체 refresh token을 삭제한다. | 다른 기기의 로그인 세션이 남는 것을 막는다. | `RefreshTokenRepository`, `AccountTerminationService` | 사용자 상태·task enqueue와 같은 트랜잭션, 실패 시 전체 rollback | 전량 삭제, 삭제 실패 rollback, 재발급 실패 | PR 5 |
| 기존 Access Token 중앙 차단 | `[Gather 확정 정책]` JWT 자체가 유효해도 pending/withdrawn이면 인증을 거부한다. | access token 잔여 유효기간의 보호 API 접근을 막는다. | JWT 인증 필터 또는 동등한 중앙 Security 경로 | 매 요청 최신 사용자 상태 확인, 허용 상태만 `SecurityContext` 등록 | ACTIVE 성공, pending/withdrawn 실패, 접수 직전 token 차단 | 주 PR 5, 검증 PR 7 |
| relink 금지 | `[Gather 확정 정책]` PR 4~8에서는 relink API·service 흐름을 구현하지 않는다. | 과거 generation task가 새 연결까지 해제하는 race를 막는다. | 인증·소셜 계정 application service 경계 | `UNLINK_PENDING/UNLINKED` 연결 대상 제외, block 유효 중 금지 | non-LINKED 로그인·가입·relink 거부 | PR 5~8 공통 |
| generation mismatch → `STALE` | `[Gather 확정 정책]` generation·대상 상태 invariant 위반은 API 미호출 후 `STALE`로 종료한다. | 오래된 task의 외부 부작용을 방지한다. | worker preflight와 result finalizer | 호출 전·결과 저장 시 이중 검증, 자동 retry 금지, claim 미소유자는 쓰기 금지 | mismatch/status/user 오류의 API 미호출, claim 오류의 실행 중단 | PR 6 |
| 지수 backoff + full jitter | `[Gather 확정 정책]` 기본 1분, 지수 증가, 6시간 상한, 최대 외부 호출 12회다. | 일시 장애의 동시 재시도 폭주를 줄인다. | retry policy와 `nextAttemptAt` 계산기 | 실제 HTTP 호출만 `attempt_count` 증가, 신뢰 가능한 `Retry-After` 우선 | jitter 범위, 상한, 호출 횟수, 12회 소진 | PR 6 |
| DEAD 자동 재시도 금지 | `[Gather 확정 정책]` scheduler는 `DEAD`를 claim하지 않으며 사용자와 소셜 계정은 pending 상태를 유지한다. | 완료되지 않은 unlink를 성공으로 위장하지 않는다. | task claim query, result handler, alert | terminal 상태, 수동 재처리는 별도 운영 경로 | 자동 claim 금지, 상태 유지, sanitize 저장 | PR 6 |
| AccountTermination·enqueue 원자성 | `[Gather 확정 정책]` 접수 부수효과와 task insert를 하나의 트랜잭션으로 묶는다. | task 없는 pending 상태와 고아 task를 막는다. | `AccountTerminationService`, enqueue service | outer `REQUIRED`; enqueue는 동일 트랜잭션 참여, `REQUIRES_NEW` 금지 | block/session/task 실패 rollback과 task·상태 원자성 | PR 5 |
| HTTP 중 DB transaction 금지 | `[Gather 확정 정책]` claim commit 후 transaction 없이 호출하고 별도 결과 트랜잭션에서 반영한다. | 외부 timeout 중 lock·connection 점유와 장애 전파를 막는다. | worker orchestration | claim token, lease, `claimedBy`, 결과 시 소유권·generation 재검증 | 호출 중 transaction 비활성, lease 회수, 동시 claim | PR 6 |
| PR 3 가입 세션 계약 유지 | `[Gather 확정 정책]` 같은 identity의 pending 세션을 ID 오름차순으로 비관적 잠금하고 `cancel()`한다. | 가입·탈퇴 race와 persistence context 불일치를 막는다. | `SocialSignupSessionService`의 service-level 진입점 | outer 접수 트랜잭션 참여, bulk update·잠금 순서 변경 금지 | ID 순서 잠금, cancel 실패 rollback, 가입 경쟁 | PR 5 |
| 재가입 제한 | `[Gather 확정 정책]` PHONE과 KAKAO 모두 7일이며 `now < expiresAt`만 차단한다. | cleanup 지연과 무관하게 동일한 cooldown 경계를 보장한다. | `AccountRejoinBlock` 생성·조회·cleanup | 기존 block은 `max(existing.expiresAt, now+7일)`, UTC·공통 Clock | 정확히 7일, 경계 허용, 연장, cleanup 지연 | PR 5 |
| 식별자 생명주기 | `[Gather 확정 정책]` unlink 성공 시 reversible identifier를 제거하고 HMAC은 cooldown까지만 유지한다. | 재처리 가능성과 개인정보 최소화를 함께 만족한다. | worker finalizer, retention cleanup | task에는 식별자 복제 금지, 결과 트랜잭션에서 즉시 파기 | 성공·STALE·해결 DEAD 파기, 미해결 DEAD 유지 | PR 6 |
| 운영 기본값 | `[Gather 확정 정책]` connect 2초/read 5초, scheduler 30초, batch 10, concurrency 1, lease 120초다. | 단일 EC2의 낮은 처리량에서 예측 가능한 복구와 자원 사용을 제공한다. | Admin/worker configuration properties | client 내부 retry 없음, UTC, `SKIP LOCKED` | 값 바인딩, timeout, lease 회수, 중복 claim 방지 | PR 4·6 |

### 4.2 최종 기술·운영 정책 연결표

| 정책 | 적용 이유·상태 전이 | 권장 구현 | 트랜잭션·동시성 | 보안·개인정보 | 필수 테스트 | 담당 PR |
|---|---|---|---|---|---|---|
| 일반 회원 동기 완료 | 외부 unlink 없음; `ACTIVE/SUSPENDED → WITHDRAWN`, `200` | 유형 판별 service result, 즉시 익명화·durable 후처리 | User→PHONE block 순서의 단일 트랜잭션 | 원문 제거, User tombstone 유지 | 두 시작 상태, 중복 `200`, rollback | PR 5·7 |
| 카카오 회원 비동기 완료 | `ACTIVE/SUSPENDED → PENDING → WITHDRAWN`, 접수 `202` | task enqueue와 worker finalizer | 접수 원자성, claim/call/result 분리 | pending 중 ciphertext 최소 유지 | 두 시작 상태, 중복 `202`, finalizer | PR 5·6·7 |
| 재가입 7일 | PHONE/KAKAO 동일 cooldown, `now >= expiresAt` 허용 | HMAC-SHA256 keyring, UTC Clock, max 연장 | block 잠금 순서·멱등 upsert | 원문/HMAC 전체 로그 금지 | 경계·연장·24시간 cleanup 지연 | PR 5 |
| SocialAccount lifecycle | unlink 성공 시 reversible ID 제거, HMAC은 cooldown까지만 | nullable migration과 최소 tombstone | 같은 result transaction에서 파기 | task에 ID/ciphertext 복제 금지 | 상태별 식별자 존재·제거 | PR 6 |
| DEAD ciphertext | 미해결 동안 유지, 해결 성공 시 제거 | SLA와 application-service manual retry | `DEAD` 자동 claim 금지 | retention 만료 자동 파기 금지 | 유지·해결 후 제거·민감 로그 부재 | PR 6·후속 운영 |
| User 익명화 | 일반 즉시, 카카오 unlink 성공 시 | 필드별 null/고유 익명값과 S3 durable 삭제 | 상태 전이와 같은 트랜잭션 | 가역 접두어 익명화 금지 | nullable·unique·관계·S3 retry | PR 5·6 |
| 타 도메인 FK | 연쇄 삭제로 다른 사용자 데이터 훼손 방지 | anonymized User tombstone과 domain event/outbox | auth는 cascade delete하지 않음 | 공개 작성자 DTO 치환 | FK 유지·외부 응답 익명화 | 후속 domain PR |
| retention | task 30/90일, block 7일, tombstone 90일 | 별도 cleanup scheduler | terminal claim 정보 즉시 최소화 | 미해결 DEAD 예외 | 각 경계와 cleanup 멱등성 | 후속 retention PR |
| DEAD SLA | 즉시 경보, 24h 확인, 72h 복구, 7d 책임자 검토 | 구조화 metric·log, manual retry 최대 2회 | DB 직접 UPDATE 금지 | 허용 필드만 기록 | SLA event·권한·감사 이력 | PR 6·후속 운영 |
| Admin/worker 기본값 | 빠른 실패와 단일 EC2 처리량 기준 | properties 외부화 | batch 10, concurrency 1, lease 120초 | secret masking | timeout·내부 retry 부재·lease | PR 4·6 |
| unknown 오류 | 검증 불가 성공과 무한 retry 방지 | 4xx DEAD_UNKNOWN, 5xx retry, malformed/ID mismatch DEAD | durable 12회 정책만 retry | body 원문 저장 금지 | 네 분류와 상태 전이 | PR 4·6 |
| API DTO·중복 | 완료/진행 의미 분리, polling 불필요 | 일반 completedAt, 카카오 requestedAt | 중복은 부수효과 없이 기존 결과 | 내부 task 정보 비노출 | 200/202·중복·polling 부재 | PR 7 |
| lock·claim SQL | deadlock과 중복 claim 억제 | 고정 lock order, 두 `SKIP LOCKED` query | 짧은 claim tx, MySQL 8 검증 | 로그에 identity 비노출 | 동시 claim·EXPLAIN·lease 경쟁 | PR 5·6 |
| migration ownership | 병렬 PR 번호 충돌과 checksum 방지 | restack 직전 번호 결정 | PR 5 foundation, PR 6 cleanup schema | 기존 migration 변경 금지 | validate·실행계획·rollback | PR 5·6 |

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
| `WITHDRAWAL_PENDING` | 거부 | 모두 거부 | 거부 | 거부 | 거부 | 멱등하게 `202` | 수행하지 않음 |
| `WITHDRAWN` | 거부 | 모두 거부 | 거부 | 거부 | 거부 | 멱등하게 `200` | 이미 완료됨 |

`[Gather 확정 정책]` `WITHDRAWAL_PENDING`과 `WITHDRAWN`은 일반 로그인, 카카오 로그인, refresh 재발급, 기존 access token 보호 API, 가입 세션 발급·제출, relink와 새 인증 세션 생성을 모두 차단한다. 단, network timeout과 client 재전송을 위해 인증된 `DELETE /api/v1/users/me`만 멱등 재호출할 수 있다.

- `WITHDRAWAL_PENDING`: 기존 요청 의미와 동일한 `202`; task·block·세션 취소를 다시 만들지 않는다.
- `WITHDRAWN`: 기존 완료 의미와 동일한 `200`; 익명화·이벤트·task를 다시 만들지 않는다.
- 이 예외를 위해 새 access/refresh token을 발급하지 않는다.

`[권장 구현]` 개별 Controller나 도메인 service에 검사를 반복하지 않고 다음 중앙 인증 흐름을 사용한다.

```text
JWT 서명·만료 검증
→ 현재 User 조회
→ User 상태 확인
→ WITHDRAWAL_PENDING 또는 WITHDRAWN이면 인증 거부
→ 허용 상태만 SecurityContext 등록
```

refresh token 삭제만으로는 이미 발급된 access token을 즉시 무효화할 수 없다. 따라서 token의 남은 유효기간과 관계없이 현재 사용자 상태를 조회해야 한다. 초기 단계에는 매 요청 DB 조회 비용보다 정합성과 보안을 우선하며, cache 또는 token version 최적화는 후속 작업으로 분리한다. `SUSPENDED`의 허용 범위는 현재 `LoginPolicy`와 충돌하지 않도록 PR 5에서 별도로 확인한다.

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

`[권장 구현]` 외부 unlink 성공 뒤 finalizer 트랜잭션 전에 프로세스가 종료되면 같은 task가 다시 호출될 수 있다. 카카오가 이미 연결 해제되었다는 공식 오류가 확인되고 로컬 invariant가 유효할 때만 Gather의 멱등 정책으로 성공 동등 결과를 적용한다.

### 5.4 SocialSignupSession

`[Gather 확정 정책]` 가입 세션은 opaque token 기반의 영속 세션이며 동일 identity의 pending 세션을 ID 오름차순으로 잠글 수 있다.

`[Gather 확정 정책]` 탈퇴 접수 시 동일 identity의 `PENDING` 세션을 ID 오름차순으로 조회하고 `PESSIMISTIC_WRITE` 잠금을 획득한 뒤 각 `entity.cancel(now)`로 `CANCELLED` 전환한다. JPQL/native bulk update, 잠금 없는 다건 변경, `cancel()` 우회와 서로 다른 잠금 순서를 금지하며 `LockedSocialSignupSession`의 가입 성공 계약을 변경하지 않는다.

`[권장 구현]` 실제 타입과 명칭은 PR 5 코드 조사에 맞추되 다음 의미의 service-level 진입점을 두고 outer 탈퇴 접수 트랜잭션에 참여시킨다.

```java
@Transactional(propagation = Propagation.MANDATORY)
public void cancelPendingForIdentity(
        SocialProvider provider,
        RejoinBlockIdentifier identifier,
        LocalDateTime now)
```

탈퇴 대상 사용자가 이미 가입된 상태이므로 일반적으로 pending 세션이 없어야 하지만, 과거 로그인·가입 경쟁이나 장애 복구 상황을 안전하게 정리하기 위해 이 단계를 둔다.

### 5.5 AccountRejoinBlock

`[Gather 확정 정책]` PHONE/KAKAO 식별자 해시, 키 버전, 만료 시각, 출처 사용자 ID를 담는 테이블 기반이 있다.

`[Gather 확정 정책]` unlink 성공 여부와 관계없이 탈퇴 접수 트랜잭션에서 필요한 block을 즉시 생성하거나 연장한다. worker는 block을 만들거나 수정하지 않는다. 카카오 로그인에서는 가입 세션 발급 전에 확인하고, 가입 확정 트랜잭션에서는 잠금 아래 최종 확인한다.

`[Gather 확정 정책]` PHONE과 KAKAO의 재가입 제한은 모두 7일이다.

- 일반 회원: `WITHDRAWN` 완료 시각을 기준으로 PHONE block을 저장한다.
- 카카오 회원: 탈퇴 접수 트랜잭션 성공 시각을 기준으로 PHONE/KAKAO block을 저장한다.
- `now < expiresAt`이면 차단하고 `now >= expiresAt`이면 허용한다.
- 기존 block은 `max(existing.expiresAt, now + 7 days)`로 연장하며 기간을 단축하지 않는다.
- 모든 시각은 DB에 UTC로 저장하고 애플리케이션의 일관된 `Clock`을 사용한다.
- 만료 row는 정기 cleanup하며 만료 후 최대 24시간 안에 물리 삭제한다. 조회는 row 존재가 아니라 `expiresAt`을 기준으로 하므로 cleanup 지연이 차단을 연장하지 않는다.

`[권장 구현]` PHONE/KAKAO 식별자는 HMAC-SHA256과 key version으로 저장한다. block 최대 생존 기간 동안 과거 key 조회가 가능한 keyring을 유지하고 일반 SHA-256으로 대체하지 않는다. 원문과 HMAC 전체는 로그에 남기지 않는다.

`[운영·법적 검토]` 7일은 Gather 서비스 정책이며 법정 의무 기간이 아니다. 서비스 목적, 약관·개인정보처리방침 문구와 HMAC identifier의 처리 목적·보유 기간 고지를 운영 공개 전에 검토한다.

### 5.6 확정 상태 흐름

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
- 요청 본문의 탈퇴 사유 형식과 길이는 API 계층에서 검증한다.
- 사용자에게 카카오 `LINKED` 계정이 있는지 잠금 아래 다시 확인해 일반·카카오 경로를 선택한다.

### 6.2 일반 회원 완료 트랜잭션

`[Gather 확정 정책]` 카카오 `LINKED` 계정이 없는 일반 회원은 하나의 트랜잭션에서 탈퇴를 완료하고 `200` 결과를 만든다.

1. `User`를 비관적 잠금한다.
2. 이미 `WITHDRAWN`이면 추가 부수효과 없이 기존 완료 결과를 반환한다.
3. 카카오 `LINKED` 계정이 없음을 재검증한다.
4. PHONE `AccountRejoinBlock`을 `max(existing.expiresAt, now+7일)`로 생성·연장한다.
5. 사용자 refresh token을 전량 삭제하고 가입·인증 관련 데이터를 정리한다.
6. `User`를 `WITHDRAWN`으로 전환하고 같은 트랜잭션에서 auth/user 개인정보를 익명화한다. 현재 `User.anonymize()`의 상태 invariant에 맞춰 실제 호출 순서는 withdraw 후 anonymize로 둔다.
7. profile image durable deletion row와 필요한 탈퇴 후처리 event 또는 outbox를 저장한다.
8. 완료 시각을 기록한다.
9. 커밋한다.

### 6.3 카카오 회원 접수 트랜잭션

`[Gather 확정 정책]` 카카오 `LINKED` 회원은 하나의 짧은 트랜잭션에서 다음을 수행하고 `202` 결과를 만든다.

1. `User`를 비관적 잠금한다.
2. `WITHDRAWAL_PENDING`이면 추가 부수효과 없이 기존 접수 결과를, `WITHDRAWN`이면 기존 완료 결과를 반환한다.
3. 동일 identity의 pending `SocialSignupSession`을 ID 오름차순으로 잠그고 각 `cancel(now)`를 호출한다.
4. `SocialAccount`를 잠그고 provider, `LINKED` 상태와 generation을 재검증한다.
5. `User → WITHDRAWAL_PENDING`, `SocialAccount → UNLINK_PENDING`으로 전환한다.
6. PHONE, KAKAO 순서로 `AccountRejoinBlock`을 7일까지 생성·연장한다.
7. 사용자 refresh token을 전량 삭제한다.
8. `(social_account_id, generation)`이 유일한 `KakaoUnlinkTask(PENDING)`를 생성한다.
9. 필요한 domain event를 등록하고 커밋한다.

외부 카카오 HTTP 호출과 개인정보 파기는 이 트랜잭션에서 수행하지 않는다.

### 6.4 실패 원자성

- task insert가 실패하면 사용자와 소셜 계정의 pending 전이도 롤백한다.
- refresh token 폐기나 재가입 block 생성이 실패해도 전체 접수를 롤백한다.
- 중복 요청이 unique constraint와 경쟁하면 기존 task와 최신 사용자 상태를 다시 조회해 멱등 결과로 변환한다.
- 카카오 계정이 이미 `UNLINKED`라면 외부 task를 만들지 않고, 상태·세대 검증 후 로컬 finalization 후보로 처리한다.

`[Gather 확정 정책]` 위 작업은 하나의 AccountTermination 트랜잭션이다. task enqueue는 별도 `REQUIRES_NEW`를 사용하지 않고 동일 트랜잭션에 참여한다. task 저장 실패 시 전체 접수를 rollback하고, 상태 전이 실패 시 task도 저장하지 않는다. task 없는 `UNLINK_PENDING`과 탈퇴와 관계없는 고아 task를 허용하지 않는다.

`[권장 구현]` `AccountTerminationService`는 기본 `REQUIRED`를 사용하고 enqueue와 가입 세션 취소 service는 `MANDATORY` 또는 같은 `REQUIRED` 트랜잭션 참여를 보장한다. 정확한 annotation은 실제 호출 구조에 맞추되 원자성은 변경하지 않는다. after-commit 이벤트는 worker를 빠르게 깨우는 최적화일 뿐 task 생성의 유일한 경로가 아니다.

`[Gather 확정 정책]` refresh token 삭제는 단일 token이 아니라 해당 사용자의 모든 로그인 세션을 대상으로 하며 상태 전이와 같은 트랜잭션에서 수행한다. 삭제 실패 시 접수 전체를 rollback한다.

`[권장 구현]` repository 메서드는 `deleteAllByUserId(userId)`처럼 사용자 전체 삭제 의미를 드러내야 한다. 실제 엔티티 연관관계에 따라 이름은 조정할 수 있다.

## 7. Worker 흐름

`[Gather 확정 정책]` worker는 claim transaction, transaction 없는 외부 호출, result transaction의 세 단계로 분리한다. 카카오 HTTP 요청 중에는 DB transaction이나 row lock을 유지하지 않는다.

### 7.1 Claim 트랜잭션

1. `status=PENDING`이고 `next_attempt_at <= now`인 행, 또는 lease가 만료된 `PROCESSING` 행을 찾는다.
2. MySQL의 `FOR UPDATE SKIP LOCKED`를 사용해 `(next_attempt_at, id)` 순서로 제한된 batch를 잠근다.
3. 각 행을 `PROCESSING`으로 바꾸고 `claim_token`, `claimed_by`, `claimed_at`, `lease_expires_at`을 기록한다.
4. 커밋한다.

lease는 HTTP connect/read timeout과 정상적인 결과 처리 시간을 합친 값보다 충분히 길어야 한다. batch 크기와 worker 동시성은 DB connection pool과 카카오 호출량 제한을 함께 고려한다.

### 7.2 외부 호출

트랜잭션 밖에서 각 claim에 대해 다음을 수행한다.

1. 다음 호출 전 invariant를 전부 확인한다.
   - `SocialAccount`가 존재한다.
   - provider가 `KAKAO`다.
   - task의 `socialAccountId`와 조회된 계정 ID가 일치한다.
   - `SocialAccount.generation == task.generation`이다.
   - `SocialAccount.status == UNLINK_PENDING`이다.
   - 연결된 `User.status == WITHDRAWAL_PENDING`이다.
   - task가 현재 worker의 유효한 `PROCESSING` claim과 claim token을 가진다.
2. 대상·generation·상태 invariant가 불일치하면 HTTP를 호출하지 않고 `STALE` 후보로 반환한다. claim 소유권이 유효하지 않으면 task 상태를 변경하지 않고 실행을 중단한다.
3. `SocialAccount`의 암호문을 읽고 카카오 회원번호를 메모리에서만 복호화한다.
4. invariant가 모두 맞을 때만 Admin unlink client를 호출한다.
5. HTTP status, Kakao `code`, 응답 `id`, timeout·network·parse 결과를 typed result로 변환한다.
6. 민감한 요청·응답 원문은 로그에 기록하지 않는다.

사전 generation 검사는 불필요한 호출을 줄이는 최적화다. 정확성을 위해 결과 트랜잭션에서 반드시 다시 검증한다.

### 7.3 결과 트랜잭션

1. `SocialAccount`를 잠근다.
2. `KakaoUnlinkTask`를 잠근다.
3. 연결된 `User`와 pending 상태를 재검증한다.
4. task가 아직 같은 `claim_token`의 `PROCESSING`인지 확인한다.
5. 현재 `SocialAccount.generation == task.generation`인지 확인한다.
6. 세대가 다르면 task를 `STALE`로 종료하고 현재 소셜 계정은 변경하지 않는다.
7. 성공 또는 검증된 성공 동등 결과라면:
   - `SocialAccount`를 `UNLINKED`로 전환한다.
   - legacy provider ID, ciphertext와 encryption key version을 제거하고 cooldown이 끝났으면 HMAC·provider key version도 제거한다.
   - `User`가 여전히 `WITHDRAWAL_PENDING`인지 확인한다.
   - 사용자를 `WITHDRAWN`으로 전환하고 같은 트랜잭션에서 개인정보를 익명화한다.
   - task를 `SUCCEEDED`로 전환한다.
8. retryable 결과라면 attempts를 증가시키고 backoff를 계산해 `PENDING`으로 되돌린다.
9. 영구 실패 또는 실제 외부 호출 12회 소진이면 `DEAD`로 전환한다.
10. 커밋한다.

`[Gather 확정 정책]` `SUCCEEDED`는 카카오 HTTP 성공만이 아니라 로컬 상태 전이와 필수 개인정보 파기까지 같은 결과 트랜잭션에서 완료되었음을 뜻한다.

### 7.4 Lease 회수와 fencing

- lease가 만료된 `PROCESSING` task는 새 worker가 다시 claim할 수 있다.
- claim마다 예측 불가능한 `claim_token`을 새로 발급한다.
- 늦게 돌아온 이전 worker는 task ID와 claim token이 모두 일치할 때만 결과를 기록할 수 있다.
- lease 연장이 필요하다면 같은 claim token의 소유자만 갱신한다.
- worker clock 차이를 줄이기 위해 claim과 만료 판정은 가능한 한 DB 시각을 사용한다.

이 분리는 외부 timeout 중 DB lock 유지, connection pool 고갈, deadlock 가능성, 카카오 장애의 DB 전파를 방지하고 worker crash·재시작과 stuck task 회수를 가능하게 한다. 필수 보완 장치는 claim token, lease expiration, `claimedBy`, generation·상태 재검증, 결과 트랜잭션의 claim 소유권 검증과 멱등성이다.

### 7.5 Admin client와 worker 기본값

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

모든 시간 계산은 UTC를 사용한다. local/test에도 명시적 기본값을 두고 운영에서는 Admin client enabled 여부를 분리한다. concurrency를 늘릴 때 batch와 lease를 함께 재검토한다. 현재 값은 확정 기본값이지만 운영 지표에 따라 configuration으로 조정할 수 있다.

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
- HTTP 호출 전 `SocialAccount` 존재, provider `KAKAO`, generation 일치, `UNLINK_PENDING`, 연결 사용자 `WITHDRAWAL_PENDING`을 모두 검증한다.
- 결과 반영 직전 잠금 아래 같은 invariant를 다시 검증한다.
- 불일치하면 현재 계정과 사용자를 변경하지 않고 task를 `STALE`로 종료한다.
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

`[Gather 확정 정책]` retryable 오류에는 기본 지연 1분의 지수 backoff와 full jitter를 적용한다. 계산된 지연 상한은 6시간이고 실제 카카오 외부 API 호출은 최대 12회다. 최초 task는 즉시 실행할 수 있으며, 신뢰 가능한 `Retry-After`가 있으면 우선 적용한다.

```text
1차 실패: 최대 1분 범위에서 무작위 지연
2차 실패: 최대 2분 범위에서 무작위 지연
3차 실패: 최대 4분 범위에서 무작위 지연
4차 실패: 최대 8분 범위에서 무작위 지연
...
상한: 최대 6시간 범위에서 무작위 지연
```

full jitter는 `0 ~ min(1분 × 2^(실패 횟수-1), 6시간)` 범위에서 다음 지연을 선택하므로 정확히 표의 최대 시간 뒤에 실행된다는 뜻이 아니다.

`attempt_count`는 scheduler 조회나 claim 횟수가 아니라 실제 카카오 외부 API 호출 횟수다. claim 뒤 invariant 위반으로 HTTP를 호출하지 않고 `STALE`이 된 task는 증가시키지 않는다.

재시도 대상은 network·DNS·connect/read timeout, 일시적 5xx, rate limit과 공식 계약상 카카오 측 일시 오류다. Admin key 오류, 콘솔 권한 미설정, 잘못된 요청 형식, 응답 ID 불일치, provider ID 복호화 불가, generation mismatch와 local invariant 위반은 재시도하지 않는다.

### 9.2 DEAD 정책

`[Gather 확정 정책]` 영구 설정·요청 오류, 응답 ID 불일치, provider ID 복호화 불가, `STALE`로 분류되지 않는 데이터 무결성 오류 또는 최대 12회 외부 호출 소진은 `DEAD`로 종료한다. scheduler는 `DEAD` task를 다시 claim하지 않는다.

```text
KakaoUnlinkTask = DEAD
SocialAccount = UNLINK_PENDING
User = WITHDRAWAL_PENDING
```

`DEAD`에서 `SocialAccount → UNLINKED` 또는 `User → WITHDRAWN`을 자동 수행하지 않는다. 운영 알림을 발생시키고 오류 code와 sanitize된 설명만 저장하며 원문 요청·응답은 저장하지 않는다.

### 9.3 최종 오류 분류 원칙

- `[공식 계약]` `-101`은 해당 앱과 연결되지 않은 사용자 오류다.
- `[Gather 확정 정책]` local account ID, generation, `UNLINK_PENDING`, `WITHDRAWAL_PENDING`과 복호화한 요청 대상 검증이 모두 맞을 때만 목표 상태가 이미 충족된 것으로 보고 멱등 성공 처리한다.
- network, DNS, connect/read timeout, `500/502/503`, rate limit과 공식 문서상 일시·점검 오류만 자동 retry한다.
- 잘못된 parameter, Admin key·앱 불일치, Admin API 설정 누락, 허용되지 않은 operation, 권한 부족, 종료 API, 앱·개발자 제재, 복호화 실패와 응답 ID 불일치는 즉시 `DEAD`다.
- HTTP `429`는 방어적으로 retryable로 처리하되 unlink 쿼터 분류는 body `code`를 우선한다.
- `msg` 문자열에는 의존하지 않는다.

### 9.4 DEAD 운영 SLA와 수동 재처리

`[Gather 확정 정책]`

- 발생 즉시 ERROR 구조화 로그와 metric
- 24시간 이내 원인 확인
- 72시간 이내 설정 수정 또는 복구 시도
- 7일 이상 미해결이면 서비스 책임자와 개인정보 담당 검토

로그·metric에는 task ID, socialAccount ID, generation, attempt count, normalized error code와 발생 시각만 사용한다. Admin key, provider user ID, providerUserKey 전체, ciphertext, form body, 카카오 원문, 전화번호와 이메일은 금지한다.

PR 6에는 수동 retry API를 넣지 않는다. 후속 운영 기능은 DB 직접 UPDATE를 금지하고 권한 있는 운영자가 application service로만 실행한다. 자동 12회 소진 원인이 해결된 경우 새 retry cycle을 최대 2회 시작할 수 있으며 재처리 이력과 이전 오류 metadata를 보존한다.

`[운영·법적 검토]` 실제 운영 알림 채널과 담당자를 운영 공개 전에 지정한다.

## 10. Task 데이터 모델

권장 테이블명은 `kakao_unlink_task`다.

| 컬럼 | 필수 | 용도 | 보안 고려 | index / constraint |
|---|---|---|---|---|
| `id` | 예 | PK | 비민감 내부 ID | PK |
| `social_account_id` | 예 | 대상 `SocialAccount` 참조 | API에 노출하지 않음 | FK `RESTRICT`, generation과 unique |
| `generation` | 예 | 생성 시점 연결 세대 snapshot | 비민감 상태 값 | unique `(social_account_id, generation)` |
| `status` | 예 | `PENDING`, `PROCESSING`, `SUCCEEDED`, `DEAD`, `STALE` | 비민감 | claim·lease 복합 index |
| `attempt_count` | 예 | 실제 카카오 외부 API 호출 횟수 | 비민감 | 기본값 `0`, `0..12` |
| `next_attempt_at` | 예 | 다음 claim 가능 시각 | 비민감 | `(status, next_attempt_at, id)` |
| `last_attempt_at` | 아니요 | 마지막 외부 호출 시각 | 비민감 | 운영 조회용 선택 index |
| `claimed_at` | 아니요 | 현재 claim 획득 시각 | 비민감 | 진단용 |
| `lease_expires_at` | 아니요 | `PROCESSING` claim 만료 시각 | 비민감 | `(status, lease_expires_at, id)` |
| `claim_token` | 아니요 | 늦은 worker 결과를 막는 fencing token | 충분한 entropy, 로그·API 노출 금지 | 활성 claim 내 검증 |
| `claimed_by` | 아니요 | worker instance 식별자 | hostname 등에 비밀을 넣지 않음 | 진단용 |
| `completed_at` | 아니요 | terminal 상태 도달 시각 | 비민감 | retention 조회용 선택 index |
| `last_http_status` | 아니요 | 마지막 HTTP status | 비민감 | index 불필요 |
| `last_error_code` | 아니요 | Kakao code 또는 정규화된 내부 code | provider ID를 넣지 않음 | 운영 집계용 선택 index |
| `last_error_type` | 아니요 | retryable/config/security/unknown 등 분류 | 비민감 enum | 운영 집계용 선택 index |
| `last_error_message` | 아니요 | 길이 제한된 비민감 진단 | 원문 body·회원번호·key 금지 | index 불필요 |
| `created_at`, `updated_at` | 예 | 생성·갱신 감사 시각 | 비민감 | retention 필요 시 index |
| `version` | 예 | 방어적 낙관적 잠금 | 비민감 | version invariant |

필수 제약과 인덱스:

- unique `(social_account_id, generation)`
- claim index `(status, next_attempt_at, id)`
- lease recovery index `(status, lease_expires_at, id)`
- FK는 task 이력을 의도치 않게 삭제하지 않도록 `RESTRICT`
- 상태별 필수 컬럼 조합은 application invariant와 migration 가능한 범위의 check constraint로 보호

`[권장 구현]` task에는 Admin key, 카카오 회원번호의 평문, 암호문, HMAC 조회 키, 카카오 응답 원문, unlink 요청 body를 저장하지 않는다. 호출 시 동일 generation의 `SocialAccount`에서 읽는다. task가 민감정보의 두 번째 보관소가 되면 키 회전과 파기 범위가 불필요하게 커진다.

## 11. 잠금 순서와 동시성

`[Gather 확정 정책]` 다음 잠금 순서를 핵심 invariant로 사용한다.

### 11.1 카카오 회원 탈퇴 접수

```text
User
  -> 동일 identity PENDING SocialSignupSession (ID ASC)
  -> SocialAccount
  -> AccountRejoinBlock PHONE
  -> AccountRejoinBlock KAKAO
  -> KakaoUnlinkTask insert
```

### 11.2 일반 회원 탈퇴

```text
User
  -> AccountRejoinBlock PHONE
  -> 인증·사용자 관계 정리
```

### 11.3 카카오 가입

```text
PENDING SocialSignupSession (ID ASC)
  -> SocialAccount
  -> AccountRejoinBlock
```

PR 3의 잠금과 상태 전이 계약을 변경하지 않고 signup 최종 트랜잭션에서 block을 재검증한다.

### 11.4 Worker 결과 반영

```text
SocialAccount
  -> KakaoUnlinkTask
```

task와 SocialAccount를 함께 잠그는 향후 흐름도 같은 순서를 사용한다. User finalization은 잠긴 SocialAccount가 가리키는 user를 검증한 후 처리하며, 반대 순서로 SocialAccount를 다시 획득하는 호출을 만들지 않는다.

### 11.5 MySQL due task claim

```sql
SELECT *
FROM kakao_unlink_task
WHERE status = 'PENDING'
  AND next_attempt_at <= :now
ORDER BY next_attempt_at, id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

필수 index:

```sql
(status, next_attempt_at, id)
```

### 11.6 만료 lease 회수

```sql
SELECT *
FROM kakao_unlink_task
WHERE status = 'PROCESSING'
  AND lease_expires_at <= :now
ORDER BY lease_expires_at, id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```

필수 index:

```sql
(status, lease_expires_at, id)
```

native query 여부는 PR 6에서 정할 수 있지만 SQL 의미와 정렬·잠금 계약은 유지한다.

### 11.7 동시성 검증과 deadlock

- 같은 사용자의 탈퇴 요청 두 건
- 탈퇴 접수와 카카오 가입 확정의 경쟁
- worker 두 대가 같은 task를 claim
- lease 만료 직전 이전 worker와 새 worker의 결과 경쟁
- task 결과 반영과 future relink의 경쟁
- 탈퇴 접수와 외부 unlink webhook의 경쟁
- task 성공 후 finalizer 실패와 재시도

MySQL 8에서 두 worker의 동시 claim, 중복 claim 방지, `SKIP LOCKED`, due ordering, lease 회수 경쟁, index 사용, `EXPLAIN ANALYZE`, lock wait·deadlock과 batch 크기별 실행계획을 검증한다. H2만으로 완료 판정하지 않는다.

잠금 순서를 지켜도 MySQL deadlock은 발생할 수 있다. 이 경우 DB 트랜잭션 전체를 rollback하고 제한된 application-level transaction retry를 검토한다. 실제 횟수는 구현·부하 검증으로 정하며, 외부 HTTP retry와 DB deadlock retry를 혼동하지 않는다.

## 12. 개인정보 파기 정책

### 12.1 공식 요구

- `[공식 계약]` 서비스 탈퇴 시 개인정보를 복구할 수 없게 파기한다.
- `[공식 계약]` 탈퇴 뒤 보관하려면 동의 등 적법한 근거가 필요하다.
- `[공식 계약]` 카카오 앱별 회원번호도 개인정보이므로 파기 대상이다.

### 12.2 현재 코드

- `[Gather 확정 정책]` `User.anonymize()`는 사용자 엔티티의 주요 개인정보를 제거하고 전화번호·닉네임을 비식별 대체값으로 변경한다.
- `[Gather 확정 정책]` `SocialAccount`는 카카오 회원번호를 legacy 평문, AES-GCM 암호문, HMAC 조회 키 형태로 보관한다.
- `[Gather 확정 정책]` 현재 스키마의 일부 소셜 식별자 컬럼은 null 허용과 행 tombstone을 전제로 설계되지 않았다.
- `[Gather 확정 정책]` `AccountRejoinBlock`은 파생 식별자를 만료 시각까지 보관할 수 있지만, 실제 생성·삭제 정책은 연결되지 않았다.

### 12.3 권장 파기 시점과 범위

`[Gather 확정 정책]` 일반 회원은 탈퇴 트랜잭션에서 즉시 익명화한다. 카카오 회원은 `WITHDRAWAL_PENDING`에서 접근만 차단하고, unlink 성공 결과 트랜잭션에서 `SocialAccount.UNLINKED`, reversible identifier 파기, User 익명화, `User.WITHDRAWN`, task `SUCCEEDED`를 함께 반영한다.

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

unlink 성공 시점에 7일 cooldown이 이미 끝났다면 HMAC과 provider key version도 즉시 제거한다.

`UNLINK_PENDING`이 7일을 넘는 예외 상황에서도 worker는 task의 `social_account_id`와 ciphertext로 호출하므로 cooldown 종료 뒤 HMAC을 task 재처리 목적으로 연장 보관하지 않는다. 미해결 task에는 ciphertext와 encryption key version만 유지한다.

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
| `AccountRejoinBlock` | 7일 + 최대 24시간 cleanup 지연 |
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

후보 endpoint:

```http
DELETE /api/v1/users/me
Content-Type: application/json
Authorization: Bearer <access-token>

{
  "reason": "..."
}
```

### 13.1 일반 회원

`[Gather 확정 정책]` 일반 회원은 탈퇴와 개인정보 익명화가 최종 완료된 뒤 `200 OK`를 반환한다.

```json
{
  "success": true,
  "data": {
    "status": "WITHDRAWN",
    "completedAt": "2026-07-30T08:00:00Z"
  }
}
```

### 13.2 카카오 회원

`[Gather 확정 정책]` 카카오 회원은 접근 차단과 durable task 저장까지 완료한 뒤 `202 Accepted`를 반환한다. 이 응답은 unlink, `SocialAccount.UNLINKED`, `User.WITHDRAWN`, 개인정보 최종 파기 또는 task `SUCCEEDED`를 의미하지 않는다.

```json
{
  "success": true,
  "data": {
    "status": "WITHDRAWAL_PENDING",
    "requestedAt": "2026-07-30T08:00:00Z"
  }
}
```

프론트는 `200`과 `202`를 모두 성공으로 처리하되 완료 상태를 구분한다. 실제 DTO 이름과 공통 `ApiResponse` wrapping은 PR 7 convention에 맞춘다.

### 13.3 중복 DELETE

- `WITHDRAWAL_PENDING`: 추가 부수효과 없이 동일 의미의 `202`
- `WITHDRAWN`: 추가 익명화·event·task 없이 동일 의미의 `200`

### 13.4 공개 상태 조회

`[Gather 확정 정책]` 탈퇴 상태 polling endpoint는 제공하지 않는다. 탈퇴 접수 뒤 사용자는 로그아웃·접근 차단되고, polling token을 새로 만들면 인증·내부 task 노출 위험이 생긴다. 운영 상태는 DB task, metric과 구조화 로그로 확인한다.

### 13.5 외부 응답 금지 정보

task ID, socialAccount ID, generation, attempt count, next attempt, 내부 `DEAD/STALE`, 카카오 code·회원번호는 응답하지 않는다.

## 14. Migration 계획

조사 시점의 migration 기준은 다음과 같다.

| 기준 | 최신 번호 |
|---|---|
| `origin/develop` | `V33` |
| 현재 PR 3 포함 local branch | `V34` |
| 다음 번호 | 예약하지 않음 |

`[Gather 확정 정책]` 구현 또는 restack 직전에 최신 `origin/develop` migration을 확인해 다음 사용 가능한 번호를 결정한다. 병렬 PR 번호를 미리 선점하지 않고 merge 직전에 다시 확인한다.

기존·merge된 migration 수정·rename을 금지하고 PR마다 자신이 소유한 schema만 새 migration으로 변경한다. checksum 문제와 신규 schema 설계를 분리한다.

### 14.1 PR별 소유권

#### PR 4

migration 없음.

#### PR 5

- `users.status`가 `WITHDRAWAL_PENDING`을 수용하도록 enum/check 정의 변경
- `kakao_unlink_task` 테이블, 제약, 인덱스, FK
- task unique/index/lease 컬럼
- AccountTermination에 필요한 최소 schema

User status가 VARCHAR이면 DB constraint 변경이 실제 필요한지 먼저 확인한다. 하나의 entity 생성 DDL은 가능한 한 하나의 migration으로 유지한다.

#### PR 6

- unlink 성공 뒤 `social_accounts` identifier를 null 처리할 수 있는 schema
- finalizer에 실제 필요한 완료 시각 컬럼
- retention metadata

예상 파일명은 설명용이며 번호를 확정하지 않는다.

```text
V{N}__create_kakao_unlink_task.sql
V{N+1}__allow_social_account_identifier_cleanup.sql
```

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
| 포함 | 일반 회원 동기 `WITHDRAWN`과 service `200` 결과, 카카오 회원 `WITHDRAWAL_PENDING`과 service `202` 결과, refresh token 전량 삭제, PHONE/KAKAO 7일 block, 가입 세션 취소, `UNLINK_PENDING`, task schema/entity/repository/enqueue, 중앙 상태 차단, 원자성·잠금 순서 |
| 제외 | 실제 Admin HTTP 호출, scheduler worker, public 탈퇴 API, webhook, future relink |
| 선행 조건 | PR 4의 typed result 계약, 개인정보 파기·재가입 제한의 미결정 항목 확인 |
| 완료 조건 | 중복·경쟁 요청에도 사용자 pending, 소셜 계정 pending, task 한 건이 함께 커밋되거나 함께 롤백됨 |
| 테스트 | 일반 ACTIVE/SUSPENDED의 `WITHDRAWN`, 카카오 ACTIVE/SUSPENDED의 pending, 유형별 service 결과, 중복, token 전량 삭제, 7일·경계·연장·cleanup 지연, access 차단, block/session/task 실패 rollback, ID ASC 잠금 |
| migration | 실제 번호는 restack 직전 결정: 사용자 상태와 task foundation 소유 |

### PR 6 — worker와 finalizer

| 항목 | 내용 |
|---|---|
| 목적 | task를 lease 기반으로 실행하고 unlink 완료 후 탈퇴와 개인정보 파기를 확정한다. |
| 포함 | scheduler 30초, batch 10, concurrency 1, lease 120초, claim token·`SKIP LOCKED`, generation `STALE`, full-jitter retry·최대 12회, `DEAD`, identifier 파기, 카카오 User finalization·익명화, retention metadata |
| 제외 | public 탈퇴 API, 공개 수동 retry API, retention cleanup scheduler, webhook, future relink |
| 선행 조건 | PR 4, PR 5 |
| 완료 조건 | 중복 실행과 worker crash에도 같은 generation만 안전하게 완료되고 외부 HTTP 동안 DB lock을 유지하지 않음 |
| 테스트 | timeout·retry 부재, due `SKIP LOCKED`, lease 회수, unknown 오류, ID 불일치, jitter·6시간·12회, identifier 제거, tombstone, 카카오 익명화, HTTP 중 transaction 부재, MySQL `EXPLAIN ANALYZE` |
| migration | 실제 번호는 restack 직전 결정: identifier nullable·finalizer/retention schema 소유 |

### PR 7 — 탈퇴 API

| 항목 | 내용 |
|---|---|
| 목적 | 검증된 durable 처리 흐름을 사용자 API로 공개한다. |
| 포함 | `DELETE /api/v1/users/me`, 일반 `200`, 카카오 `202`, refresh cookie 만료, 중복 상태별 멱등성, DELETE 재호출 예외, 내부정보 비노출 DTO, OpenAPI·프론트 계약, 보호 API 차단 통합 검증 |
| 제외 | webhook, 상태 조회 UI, 운영자 재처리 API, future relink |
| 선행 조건 | PR 5, PR 6 |
| 완료 조건 | 응답 전에 접수 트랜잭션이 커밋되고, pending 사용자의 기존·신규 인증 접근이 차단되며, 중복 요청이 멱등함 |
| 테스트 | 일반 `200`, 카카오 `202`, pending 중복 `202`, withdrawn 중복 `200`, cookie 만료, 내부 task 정보 부재, polling endpoint 부재, OpenAPI 일치 |
| migration | 없음 예상 |

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
- 가입 세션 ID ASC 잠금과 `cancel()`
- block/session/task 실패 시 전체 rollback과 task·`UNLINK_PENDING` 원자성

#### PR 6

- connect/read timeout 적용과 client 내부 retry 없음
- due·expired lease `SKIP LOCKED`, 두 worker 중복 claim 방지
- generation mismatch `STALE`·HTTP 미호출, `STALE` 자동 retry 금지
- unknown 4xx `DEAD_UNKNOWN`, unknown 5xx retry, malformed 2xx `DEAD_RESPONSE`, ID 불일치 `DEAD_SECURITY`
- full jitter 범위, 6시간 상한과 실제 API 호출 12회
- `DEAD` 자동 claim 금지와 pending 상태 유지
- `SUCCEEDED` identifier 제거, 카카오 User 익명화, 최소 tombstone
- HTTP 호출 중 transaction 비활성
- MySQL `EXPLAIN ANALYZE`, lock wait·deadlock 검증

#### PR 7

- 일반 회원 `200`, 카카오 회원 `202`
- pending 중복 `202`, withdrawn 중복 `200`
- refresh cookie 만료
- 응답에 내부 task 정보 미포함
- 공개 status polling endpoint 없음
- 프론트 계약과 OpenAPI 일치

PR 4를 client 단독으로 먼저 분리하면 HTTP 계약과 오류 분류를 worker 코드와 독립적으로 검증할 수 있다. PR 7은 worker가 준비된 뒤 공개해 사용자가 장시간 처리되지 않는 pending 상태에 빠지는 것을 막는다.

## 16. 후속 작업

- task `DEAD`, 처리 지연, lease 회수 횟수, 오류 code별 metric과 alert
- 만료된 `SocialSignupSession` retention cleanup
- 완료된 unlink task retention cleanup
- 운영자용 task 조회·재시도·보류 절차와 감사 로그
- 로컬 상태와 카카오 연결 상태의 reconciliation 절차
- Admin key 회전, 최소 권한, 비밀 저장소, 비상 폐기 절차
- HMAC/AES key rotation과 과거 keyring 지원
- legacy 평문 provider ID 제거 migration
- 프로필 이미지 외의 외부 저장소 개인정보 정리
- 관련 도메인 데이터의 보존·익명화·삭제 정책
- 재가입 제한 만료 cleanup과 가입·로그인 적용
- future social account relink 프로토콜
- 다중 provider 및 마지막 인증수단 제거 정책
- webhook 장애·재전송·서명 또는 신뢰 경계 운영 검증
- 대량 backlog에 대한 rate limit, backpressure, graceful shutdown
- task와 개인정보 파기 이력의 보존 기간 및 파티셔닝

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

이 목록과 충돌하는 과거 문서, 이슈 설명 또는 코드 주석은 본 문서와 카카오 공식 계약을 기준으로 재검토한다.
