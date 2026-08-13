# 카카오 OAuth state 검증 — 서버 검증 설계 (조건부 보류)

작성일 2026-07-26.

## ⚠️ 이 문서의 현재 지위: 착수 예정 아님

**로그인 CSRF 방어는 프론트엔드의 state 검증으로 이미 완결된다.** 인가 URL 생성과 콜백 수신을 모두 프론트가 소유하므로, 프론트가 난수를 `sessionStorage`에 보관했다가 콜백에서 대조하면 공격은 완전히 차단된다(§13-A). **서버 검증이 있어야만 막히는 잔여 공격 경로는 없다.**

따라서 이 문서에 적힌 서버 측 구현은 **지금 하지 않는다.** 요구 출처가 외부 보안 리뷰나 카카오 검수가 아닌 팀 자체 판단이었고, 비용 대비 얻는 것이 아래 하나뿐이기 때문이다.

- **얻는 것**: 프론트가 나중에 검증을 빠뜨리는 회귀를 서버가 구조적으로 차단한다.
- **잃는 것**: 쿠키는 브라우저당 1개라 **다중 탭 로그인이 깨진다**(`sessionStorage`는 탭별 격리라 이 문제가 없다). 즉 UX는 오히려 열화된다. 여기에 프론트 동시 배포와 0.5~1일이 든다.

프론트 회귀가 걱정이라면 서버를 건드리는 것보다 **프론트 레포에 "state 불일치 시 로그인 API를 호출하지 않는다" 테스트를 추가하는 것이 훨씬 싸고 정확한 대응**이다.

### 재검토 트리거

아래 중 하나가 발생하면 이 문서를 그대로 꺼내 착수한다.

1. **두 번째 클라이언트 추가**(네이티브 앱 등). 클라이언트마다 검증을 재구현해야 하므로 서버 일원화의 값어치가 생긴다.
2. **카카오 계정 연결 기능 착수**. 연결 CSRF는 공격자의 카카오 계정을 피해자 계정에 붙여 **영구 접근권**을 넘기는 것이라 로그인 CSRF보다 위험도가 한 단계 높다. 그 시점에 재평가한다.
3. 외부 보안 점검·카카오 검수에서 **서버 측 검증을 명시적으로 요구**받을 때.

아래 §1~§13은 트리거 발생 시 바로 착수할 수 있도록 완성해 둔 설계다.

---

## 1. 기능 목표

카카오 로그인에서 **로그인 CSRF**를 차단한다. 인가 요청을 시작한 브라우저와 콜백을 완료한 브라우저가 동일함을 서버가 검증한다.

막는 공격은 하나다. 공격자가 자기 계정으로 인가를 진행해 인가코드를 확보한 뒤, 피해자 브라우저를 `https://dev.gathernow.kr/login/kakao/callback?code=<공격자코드>`로 유도한다. 프론트 콜백 페이지가 쿼리의 코드를 그대로 서버에 전달하면 **피해자가 공격자 계정으로 로그인된 상태**가 되고, 이후 피해자가 남긴 활동·북마크를 공격자가 나중에 열람할 수 있다.

반대로 이 기능이 막지 **않는** 것도 명확히 해 둔다. 인가코드 탈취·재사용은 이미 백엔드가 `client_secret`으로 서버사이드 교환하는 구조라 별도로 방어된다. state가 없다고 계정이 탈취되는 구조는 아니며, **심각도는 중간 이하**로 판단했다.

---

## 2. 확정된 요구사항

| # | 내용 |
|---|---|
| R1 | 백엔드가 state를 발급하고, 콜백 시 서버에서 검증한다(프론트 자율 검증에 의존하지 않는다) |
| R2 | `POST /api/v1/auth/kakao/login`에서 state가 없거나 불일치하면 로그인을 거부한다 |
| R3 | state는 1회용이며, 검증 후 성공·실패와 무관하게 폐기한다 |
| R4 | 서버에 상태 저장소(Redis·세션·DB 테이블)를 새로 도입하지 않는다 |
| R5 | DB 스키마를 변경하지 않는다(Flyway 마이그레이션 없음) |
| R6 | ~~1차까지의 방어 공백은 프론트 자체 검증으로 임시 대응한다~~ → **프론트 검증이 임시 대응이 아니라 상시 방어책이다**(§13-A). 서버 검증은 그 위에 얹는 이중화일 뿐이다 |

R4는 현 서버가 `SessionCreationPolicy.STATELESS`이고 Redis 의존성이 없기 때문이다([SecurityConfig.java:65](../src/main/java/com/gather/gather/global/config/SecurityConfig.java)). 상태 저장소를 새로 붙이는 것은 이 기능 하나를 위해 지불하기에 비용이 크다.

---

## 3. 사용자 시나리오

### 정상 흐름

1. 사용자가 "카카오로 시작하기"를 누른다.
2. 프론트가 `GET /api/v1/auth/kakao/state`를 호출한다. 서버는 난수 state를 응답 본문으로 주고, **동일한 값을 HttpOnly 쿠키로도 내려준다.**
3. 프론트가 카카오 인가 URL에 `state=<받은 값>`을 붙여 리다이렉트한다.
4. 카카오가 `/login/kakao/callback?code=...&state=...`로 되돌려준다.
5. 프론트가 `POST /api/v1/auth/kakao/login`에 `{ authorizationCode, redirectUri, state }`를 보낸다(`credentials: 'include'` 필수).
6. 서버가 **본문 state와 쿠키 state를 비교**한다. 일치하면 기존 로그인 흐름(토큰 교환 → 회원 조회 → 분기)을 그대로 진행하고, 응답에 state 쿠키 삭제 헤더를 함께 내린다.

### 실패 흐름

- state 쿠키가 없다(만료·미발급·타 브라우저) → `400 INVALID_OAUTH_STATE`
- 본문 state 누락 → `400 VALIDATION_ERROR` (`@NotBlank`)
- 두 값 불일치 → `400 INVALID_OAUTH_STATE` + **WARN 로그** (공격 시도일 수 있으므로 추적 가능해야 한다)

프론트 처리는 기존 400과 동일하게 **"카카오 로그인 다시 시작"**이다. 별도 화면을 만들 필요는 없다.

---

## 4. API 설계

### 4-1. 신규 — `GET /api/v1/auth/kakao/state`

인증 불필요(`permitAll`). 요청 파라미터 없음.

```json
{ "success": true, "data": { "state": "y3Qd...4kA" }, "error": null }
```

```
Set-Cookie: gather_kakao_state=y3Qd...4kA; Path=/api/v1/auth/kakao; Max-Age=600;
            HttpOnly; Secure; SameSite=Lax
```

프론트는 이 요청과 이어지는 `/kakao/login` 요청 모두 `credentials: 'include'`로 보내야 한다. 서버 CORS는 이미 `allowCredentials(true)`다.

`GET`인 이유는 서버 상태를 바꾸지 않기 때문이다(쿠키 발급은 클라이언트 상태다). 멱등하며, 여러 번 호출하면 마지막 값이 유효하다 — 이 성질이 §12의 다중 탭 한계를 만든다.

### 4-2. 변경 — `POST /api/v1/auth/kakao/login`

요청 body에 `state` 필드를 **필수로** 추가한다.

```json
{ "authorizationCode": "R0-abc...", "redirectUri": "https://dev.gathernow.kr/login/kakao/callback", "state": "y3Qd...4kA" }
```

응답 계약(`signupStatus` 분기, 403 처리 등)은 **전혀 바꾸지 않는다.** 성공 응답에 state 쿠키 삭제 헤더만 추가된다.

### 4-3. 에러코드

| 상태 | 코드 | 신규 | 의미 |
|---|---|---|---|
| 400 | `INVALID_OAUTH_STATE` | ✅ 신설 | state 쿠키 부재 또는 본문과 불일치 |
| 400 | `VALIDATION_ERROR` | 기존 | `state` 필드 누락·공백 |

`VALIDATION_ERROR` 재사용도 가능하지만 신설을 택했다. **프론트 처리는 동일하되, 서버 로그·모니터링에서 공격 시도와 일반 입력 오류를 구분해야 하기 때문**이다.

---

## 5. 도메인 및 DB 설계

**DB 변경 없음. 엔티티 추가 없음. Flyway 마이그레이션 없음.**

state 저장 방식으로 세 가지를 검토했다.

| 방식 | 판정 |
|---|---|
| (i) 쿠키에 원문 저장 후 본문과 단순 비교 (double-submit) | **채택** |
| (ii) 쿠키에 state 해시 저장 | 기각 — 쿠키가 HttpOnly라 JS 유출 경로가 없어 해시의 실익이 없다 |
| (iii) 서명 JWT를 state로 사용 | 기각 — 아래 |

**(iii)을 기각한 이유가 이 설계의 핵심이다.** 서명 토큰은 "서버가 발급한 값"임을 증명하지만, 그것만으로는 **로그인 CSRF를 막지 못한다.** 공격자도 정상적으로 서명된 state를 발급받을 수 있기 때문이다. 실효성은 오직 **브라우저 바인딩**(쿠키)에서 나온다. 그리고 쿠키 바인딩이 있으면 쿠키 자체가 서버 발급의 증거이므로 서명은 잉여가 된다. 별도 secret도, 키 관리도 필요 없다.

즉 채택안 (i)은 **double-submit cookie** 패턴이며, 무상태 서버에서 OAuth state를 구현하는 표준형이다.

**state 생성:** `SecureRandom` 32바이트 → Base64 URL-safe(패딩 없음). `AuthService`가 이메일 인증 코드에 이미 `SecureRandom`을 쓰고 있으므로 방식이 일관된다.

**만료 10분:** 카카오 인가코드 자체의 만료가 10분이라 그보다 길게 잡을 이유가 없다. 사용자가 카카오 로그인·동의를 마치는 시간으로도 충분하다.

---

## 6. 인증·인가 정책

- `GET /api/v1/auth/kakao/state`는 **비로그인 접근 허용**이다. `SecurityConfig`의 `PERMIT_ALL_GET_PATHS`에 추가한다.
- 이 엔드포인트는 난수만 발급하므로 **인가 대상 리소스가 없다.** 소유권·역할 검사는 해당 사항 없다.
- state 발급 자체에는 rate limit을 걸지 않는다. 난수 발급은 비용이 사실상 없고, 남용해도 공격자가 얻는 것이 없다(자기 브라우저에만 쿠키가 심긴다). 이메일 발송처럼 외부 자원을 소모하는 동작이 아니다.
- **정지·탈퇴 계정 차단(`LoginPolicy`)은 기존 위치 그대로 유지**한다. state 검증은 그 앞단의 별개 관문이다.

---

## 7. 트랜잭션 및 동시성 정책

**트랜잭션 없음.** state 검증은 DB를 건드리지 않는 순수 문자열 비교다. `KakaoAuthService.login()`이 카카오 HTTP 호출 2회 때문에 의도적으로 `@Transactional`을 열지 않는 기존 결정에도 영향을 주지 않는다.

**비교는 `MessageDigest.isEqual()` 등 상수 시간 비교를 쓴다.** 실익은 작지만(state는 1회용 난수라 타이밍 오라클로 얻을 게 거의 없다) 비용도 0이므로 채택한다.

**동시성:** 서버 측 공유 상태가 없어 경쟁 조건이 없다. 단, 브라우저 쿠키 슬롯이 1개라는 데서 오는 다중 탭 한계가 있다(§12).

---

## 8. 실패 처리와 예외 코드

| 상황 | 처리 | 로그 |
|---|---|---|
| state 쿠키 없음 | `400 INVALID_OAUTH_STATE` | WARN |
| state 불일치 | `400 INVALID_OAUTH_STATE` | **WARN** (공격 시도 가능성) |
| 본문 state 누락 | `400 VALIDATION_ERROR` | 기존 핸들러 |
| state 검증 통과 후 카카오 호출 실패 | **기존 동작 그대로** (400/500/503) | 기존 |

**state 값 자체는 로그에 남기지 않는다.** 민감정보는 아니지만 만료 전 재사용이 가능하고, 남겨봐야 원인 파악에 도움이 되지 않는다. 불일치 사실과 요청 출처만 기록한다.

**보상 작업 불필요.** state 검증은 부수효과가 없으므로 롤백할 대상이 없다. 검증 실패 시 카카오 API를 호출하기 전에 즉시 종료하므로, 인가코드가 소모되지도 않는다(**검증을 카카오 호출보다 앞에 두는 것이 중요하다**).

---

## 9. 구현 대상 파일과 변경 책임

### 신규

| 파일 | 책임 |
|---|---|
| `domain/auth/kakao/service/KakaoStateCookieProvider` | state 생성, 쿠키 생성·삭제, 상수시간 비교 |
| `domain/auth/kakao/config/KakaoStateCookieProperties` | 쿠키 이름·secure·sameSite·path·만료 (`RefreshTokenCookieProperties`와 동일 패턴) |
| `domain/auth/kakao/dto/KakaoStateResponse` | `{ state }` |

### 수정

| 파일 | 변경 |
|---|---|
| `KakaoAuthController` | `GET /state` 추가, `login`에 `HttpServletRequest` 파라미터 + 검증 호출 + 쿠키 삭제 헤더 |
| `KakaoLoginRequest` | `state` 필드 추가 (`@NotBlank`) |
| `global/exception/ErrorCode` | `INVALID_OAUTH_STATE(BAD_REQUEST, ...)` 추가 |
| `global/config/SecurityConfig` | `PERMIT_ALL_GET_PATHS`에 `/api/v1/auth/kakao/state` 추가 |
| `application.yml` | `gather.auth.kakao-state-cookie.*` 블록 |
| `KakaoAuthSwaggerExamples` | `INVALID_OAUTH_STATE` 예시 상수 |
| `document/openapi/openapi-auth.yaml` | `/kakao/state` 경로, `KakaoLoginRequest.state` |
| `document/openapi/auth-api-notes.md` | §3-10 갱신, §3-9(신규) state 발급 |

**검증 위치는 컨트롤러다.** 쿠키는 HTTP 계층 관심사이고, `KakaoAuthService`는 `HttpServletRequest`를 알지 못한다. 기존 `AuthController.reissue(HttpServletRequest)`가 refresh 쿠키를 컨트롤러에서 추출하는 것과 동일한 패턴이므로 일관된다.

**기존 코드 재사용:** `RefreshTokenCookieProvider`를 **복제하되 공용화하지 않는다.** 두 쿠키는 수명(14일 vs 10분)·Path·용도가 달라 공통 추상화를 만들면 파라미터만 늘어난다. 단일 사용처를 위한 추상화는 만들지 않는다는 프로젝트 규칙에 따른다.

---

## 10. 테스트 시나리오

### 신규

**`KakaoStateCookieProviderTest`**
- 매 호출마다 다른 state가 생성된다
- 쿠키 속성: `HttpOnly`, `Path=/api/v1/auth/kakao`, `Max-Age=600`
- 삭제 쿠키는 `Max-Age=0`

**`KakaoAuthControllerTest` 추가분** — 반드시 검증할 경계값
- `GET /state`: 200이며 **응답 본문 state와 Set-Cookie 값이 같다**
- `login`: 쿠키 없음 → `400 INVALID_OAUTH_STATE`
- `login`: 본문 state 누락 → `400 VALIDATION_ERROR`
- `login`: 쿠키와 본문 불일치 → `400 INVALID_OAUTH_STATE`
- `login`: **불일치 시 `KakaoApiClient`가 호출되지 않는다** (인가코드 미소모 보장 — 이 검증이 §8의 핵심 계약이다)
- `login`: 일치 → 기존 흐름 정상 + state 쿠키 삭제 헤더 존재

**`KakaoSecurityIntegrationTest` 추가분**
- `GET /state`가 인증 없이 200

### 기존 테스트 영향

`KakaoAuthControllerTest`의 login 케이스 전부에 state 쿠키+본문 세팅이 필요하다. `KakaoAuthServiceTest`는 **영향 없다**(서비스는 state를 모른다) — 이 사실 자체가 계층 분리가 옳게 됐다는 신호다.

**검출력 확인:** 검증 호출을 제거했을 때 위 신규 케이스만 정확히 실패해야 한다.

---

## 11. 배포 및 마이그레이션 고려사항

### 쿠키 전송 조건 — 충족됨 (2026-07-26 확인)

운영 API는 **`https://gathernow.kr`**, 운영 프론트는 **`https://dev.gathernow.kr`**다. 이 구성에서 쿠키 바인딩에 필요한 두 조건이 모두 충족된다.

1. **same-site 성립**: 두 호스트의 등록가능도메인이 모두 `gathernow.kr`이다. 하위 도메인이 달라 cross-**origin**이지만 same-**site**이므로, `SameSite=Lax` 쿠키가 XHR에 정상적으로 실린다. `SameSite=None`은 **필요 없다.**
2. **양쪽 HTTPS**: mixed content 차단이 발생하지 않고 `Secure` 속성을 켤 수 있다.

로컬(`localhost:5173` → `localhost:8080`)도 호스트가 같아 same-site이므로 동일하게 동작한다. 포트는 same-site 판정에 영향을 주지 않는다.

**따라서 권장 설정은 기존 refresh 쿠키와 동일하다.**

| 환경 | secure | same-site |
|---|---|---|
| 운영·dev | `true` | `Lax` |
| 로컬 | `false` | `Lax` |

환경변수로 뺀다: `GATHER_KAKAO_STATE_COOKIE_SECURE`, `GATHER_KAKAO_STATE_COOKIE_SAME_SITE`. 기존 `GATHER_REFRESH_COOKIE_*`와 같은 방식이다.

> **운영 배포 참고:** 현재 HTTPS 도메인과 Nginx·Spring Boot 연결 구조는 [deployment-runbook.md](deployment-runbook.md)를 기준으로 한다.

### 하위호환 — 프론트 동시 배포 필요

`state`를 필수로 만드는 순간 **기존 프론트는 즉시 400으로 깨진다.** 두 가지 경로가 있다.

- **(1) 동시 배포** — 2차에 프론트도 카카오 플로우를 재작업하므로 현실적이다. **권장.**
- **(2) 3단계 전환** — optional 배포 → 프론트 적용 → 필수 전환. 안전하지만 배포가 3회고, 중간 단계에서는 방어가 되지 않는다.

### 롤백

DB 변경이 없으므로 **애플리케이션 롤백만으로 완전히 되돌아간다.** 스키마 롤백 절차가 필요 없다. 이것이 이 설계의 가장 큰 운영상 장점이다.

---

## 12. 보류된 사항과 잔여 위험

| # | 내용 |
|---|---|
| **W1** | **다중 탭 한계.** 쿠키 슬롯이 1개라 사용자가 탭 2개로 동시에 카카오 로그인을 시작하면 나중 state가 앞 것을 덮어써서, 먼저 시작한 탭이 `INVALID_OAUTH_STATE`로 실패한다. 프론트의 sessionStorage 방식(탭별 격리)에는 없는 단점이다. **완화하지 않고 감수한다** — 발생 빈도가 낮고, 처리는 기존 "다시 시작"과 같으며, 해결하려면 서버에 다중 슬롯 상태를 둬야 해서 R4와 충돌한다. |
| **W2** | **PKCE 미적용.** state와 별개 방어이며 이번 범위 밖이다. 백엔드가 `client_secret`으로 서버사이드 교환을 하고 있어 우선순위가 낮다. |
| ~~W3~~ | ~~HTTPS/도메인 전제 미해소~~ → **2026-07-26 해소.** 운영 API·프론트 모두 `gathernow.kr` 하위 HTTPS라 same-site + Secure 조건 충족(§11). **blocking 이슈 없음.** |
| **W4** | **1차~2차 사이 방어 공백.** §13-A의 프론트 임시 대응이 실제로 적용됐는지 백엔드가 검증할 수단이 없다. |
| **W5** | 카카오 **계정 연결·unlink**(2차 예정)에도 인가 플로우가 생기면 state 검증을 동일하게 적용해야 한다. 설계 시 함께 고려할 것. |

---

## 13. 단계별 작업 계획

### A. 실제 채택안 — 프론트 검증 (백엔드 코드 0줄)

프론트에 아래 내용을 공유하고 `auth-api-notes.md` §3-10에 명시한다. **이것으로 방어는 끝난다.**

> **카카오 인가 요청 시 `state` 파라미터를 반드시 포함하세요.**
> 난수를 만들어 인가 URL의 `state`에 붙이고 **`sessionStorage`에 보관**한 뒤, 콜백에서 쿼리의 `state`와 **문자열 일치를 확인한 후에만** `/api/v1/auth/kakao/login`을 호출하세요. 불일치하면 요청하지 말고 로그인을 처음부터 다시 시작시키세요. 확인 후 `sessionStorage` 값은 삭제합니다.
> 이 검증이 없으면 공격자가 자신의 인가코드로 사용자를 **공격자 계정에 로그인시킬 수 있습니다**(로그인 CSRF).
> `state`는 서버로 보내지 않습니다. 요청 body는 `{ authorizationCode, redirectUri }` 그대로입니다.

`localStorage`가 아니라 `sessionStorage`여야 한다. 탭 간 공유되면 1회용 성질이 깨진다.

**회귀 방지책:** 프론트 레포에 "state 불일치 시 로그인 API를 호출하지 않는다" 테스트를 둔다. 서버 검증을 도입하는 것보다 싸고, 막으려는 실패 모드를 정확히 겨냥한다.

### B. 트리거 발생 시 착수 — 서버 검증 (백엔드, 순서대로)

**현재 착수 예정 없음.** 문서 상단의 재검토 트리거가 발생했을 때만 진행한다.

| 단계 | 작업 | 검증 |
|---|---|---|
| 1 | `ErrorCode` + `KakaoStateCookieProperties` + `KakaoStateCookieProvider` | `KakaoStateCookieProviderTest` 통과 |
| 2 | `GET /state` 엔드포인트 + `SecurityConfig` permitAll | 응답 state == 쿠키 값, 비인증 200 |
| 3 | `KakaoLoginRequest.state` + 컨트롤러 검증(**카카오 호출보다 앞**) + 쿠키 삭제 | 신규 컨트롤러 테스트 6개 통과 |
| 4 | 기존 login 테스트 state 세팅 보정 | `./gradlew build` 전체 통과 |
| 5 | Swagger + `openapi-auth.yaml` + `auth-api-notes.md` 갱신 | 계약 3곳 일치 |
| 6 | 프론트 동시 배포 조율 | state 없는 요청이 400인지 스테이징 확인 |

**예상 규모: 0.5~1일.** DB 변경도 인프라 선행 작업도 없어, 프론트 조율이 되면 바로 착수 가능하다.

---

## 관련 문서

- [auth-api-notes.md](openapi/auth-api-notes.md) §3-10, §3-11 — 카카오 로그인·가입 프론트 계약
- [openapi-auth.yaml](openapi/openapi-auth.yaml) — API 스펙 정본
- [deployment-runbook.md](deployment-runbook.md) — 운영 배포 구성과 HTTPS 계약
