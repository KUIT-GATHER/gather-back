# 📢 JWT 인증 도입 + CI 자동 검사 적용 안내

develop 최신 기준으로 크게 두 가지가 바뀌었습니다.

1. **인증이 실제로 동작하기 시작** — 대부분의 API가 JWT 토큰 없이는 401을 반환합니다.
2. **PR마다 자동 검사(포맷 + 테스트)가 돌기 시작** — 검사를 통과해야 머지할 수 있습니다.

---

## 🔧 백엔드 팀

### 1️⃣ develop pull 받은 직후 (최초 1회 세팅)

- **`src/main/resources/application-secret.yml`에 `jwt.secret` 추가** — 없으면 서버가 부팅 자체가 안 됩니다 (의도된 안전장치)
  - `application-secret.yml.example` 파일을 참고해 작성
  - 시크릿은 각자 로컬에서 생성합니다 (EC2 접속 불필요, 팀원끼리 같을 필요 없음)
    - **Git Bash (추천)**: `openssl rand -base64 64`
    - **PowerShell**: `$b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)`
    - 나온 문자열을 `jwt.secret:`에 붙여넣으면 끝
- 테스트를 돌리려면 **로컬 MySQL(Docker `gather-mysql` 컨테이너) 실행 필수**
  - 안 켜져 있으면 테스트가 무더기로 실패합니다. 코드 문제가 아니라 DB 연결 문제입니다 (Hibernate Dialect 에러가 그 신호)

### 2️⃣ 개발할 때

- **새 API는 기본이 "인증 필요"** — 아무 설정 없이 만들면 자동으로 보호됩니다. 공개해야 하는 API만 `SecurityConfig`의 permitAll 목록에 추가하세요 (추가 전 팀 논의)
- 현재 공개 API 목록 (이것 외엔 전부 토큰 필요):
  - `/health`
  - `/api/v1/auth/**` (회원가입/로그인/재발급/로그아웃)
  - `/api/v1/regions`, `/api/v1/categories`
  - Swagger (`/swagger-ui/**`, `/v3/api-docs/**`)
- **로그인 유저 ID가 필요하면** `SecurityUtil.getCurrentUserId()` 사용 — 이제 실제 로그인 유저의 ID를 반환합니다 (예전처럼 `1L` 고정 아님)
- **Swagger에서 보호 API 테스트**: 로그인 API로 토큰 발급 → 우측 상단 **Authorize** 버튼에 입력하면 이후 요청에 자동으로 붙습니다
- 공개 API에도 Swagger에 자물쇠 아이콘이 뜨지만 **표시만 그런 것** — 실제로는 토큰 없이 호출됩니다
- posting 담당: `POST /api/v1/postings/sync`(로컬 수동 동기화)도 이제 토큰이 필요합니다

### 3️⃣ PR 올릴 때 (CI 자동 검사)

PR을 올리면 **포맷 검사(Spotless) + 전체 테스트**가 자동으로 실행되고, 실패하면 머지가 막힙니다.

- **포맷 검사에 걸리면** → `./gradlew spotlessApply` 실행 후 커밋/푸시하면 됩니다 (포맷 규칙을 외울 필요 없음)
- **푸시 전에 미리 확인하려면** → `./gradlew build` (CI와 동일한 검사, 로컬 MySQL 필요)
- ⚠️ **지금 작업 중인 브랜치가 있다면**: develop 머지받고 `./gradlew spotlessApply`를 한 번 돌린 뒤 푸시해야 CI를 통과합니다 (전체 코드 포맷 정리가 develop에 들어갔기 때문)

### 4️⃣ 이 공지 이후 첫 작업 시작 전 — 반드시 develop 먼저 받기

이번에 **전체 코드 포맷 정리(37개 파일)**가 develop에 들어갔기 때문에, 예전 develop 기준으로 작업하면 PR에서 충돌과 CI 실패가 거의 확실합니다.

**새 작업을 시작할 때:**

```bash
git checkout develop
git pull
git checkout -b feature/새작업브랜치
```

**이미 작업 중인 브랜치가 있을 때:**

```bash
git checkout 내브랜치
git fetch origin
git merge origin/develop        # 최신 develop을 내 브랜치로 가져오기
```

#### 충돌이 났을 때 (당황하지 않아도 됩니다)

이번 충돌은 대부분 "내가 고친 파일을 develop이 포맷 정리(들여쓰기/줄바꿈)함" 때문입니다. 처리 원칙:

1. **충돌 파일을 열어 `<<<<<<<` / `=======` / `>>>>>>>` 표시를 찾습니다**
   - `<<<<<<< HEAD` 쪽 = **내 브랜치의 코드**
   - `>>>>>>> origin/develop` 쪽 = develop의 코드 (대부분 포맷만 다름)
2. **로직 기준으로만 판단하세요** — 내가 추가/수정한 로직은 살리고, develop 쪽에 새 로직(예: 404 핸들러 등 다른 사람 작업)이 있으면 그것도 살립니다. **들여쓰기나 줄바꿈이 어느 쪽이 맞는지는 고민하지 마세요** — 어차피 다음 단계에서 자동 정리됩니다.
3. 충돌 표시(`<<<<<<<` 등)를 모두 지우고 저장한 뒤:

```bash
./gradlew spotlessApply    # 포맷은 이 명령이 알아서 정답으로 맞춰줌
./gradlew build            # 컴파일/테스트 확인 (로컬 MySQL 필요)
git add -A
git commit                 # 머지 커밋 완성
git push
```

> 💡 핵심: **포맷 때문에 고민하는 시간은 0이어야 합니다.** 로직만 맞게 합치고 `spotlessApply`를 돌리면 포맷은 항상 자동으로 해결됩니다.

머지/충돌 해결이 꼬여서 되돌리고 싶으면 `git merge --abort`로 머지 전 상태로 복구할 수 있습니다. 해결이 어려우면 혼자 씨름하지 말고 팀 채널에 공유해주세요.

### 5️⃣ 배포

main 배포는 배포 담당자가 진행합니다. 팀원은 develop 머지까지만 신경 쓰면 됩니다.

---

## 🎨 프론트엔드 팀

### 1️⃣ 모든 보호 API에 헤더 필수

공개 API(회원가입/로그인/재발급/로그아웃, 지역/카테고리 조회) 외 **모든 API 요청에 아래 헤더가 필요합니다.**

```
Authorization: Bearer <accessToken>
```

헤더가 없으면 401이 반환됩니다.

### 2️⃣ 401 응답 처리 — `error.code`로 분기

401 응답은 공통 에러 포맷 그대로입니다.

```json
{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }
```

| code | 의미 | 프론트 대응 |
|---|---|---|
| `UNAUTHORIZED` | 토큰 없음 / 헤더 형식 오류 | 로그인 화면으로 이동 |
| `EXPIRED_TOKEN` | Access Token 만료 | **재발급 후 원 요청 재시도** |
| `INVALID_TOKEN` | 무효 토큰 (서명 오류 등) | 토큰 폐기 후 재로그인 유도 |

### 3️⃣ 토큰 수명과 재발급 플로우

- Access Token: **30분** / Refresh Token: **14일**
- `EXPIRED_TOKEN` 401 수신 → `POST /api/v1/auth/reissue`(Refresh Token 사용) → 새 토큰 쌍 발급 → 원 요청 재시도, 하는 인터셉터 구현을 권장합니다
- ⚠️ **재발급하면 Refresh Token도 새것으로 교체됩니다 (rotation).** 응답으로 받은 새 Refresh Token을 반드시 저장하고 이전 것은 버리세요 — 이전 Refresh Token은 즉시 무효화되어 재사용하면 재발급이 실패합니다
- 재발급도 실패하면 (Refresh 만료 등) 재로그인을 유도하세요

### 4️⃣ 기타

- API 명세는 서버의 `/swagger-ui.html`에서 확인할 수 있고, 테스트 계정은 회원가입 API로 직접 생성하면 됩니다
- Access Token 형식이 랜덤 문자열에서 JWT(`eyJ...`)로 바뀌어 길이가 길어졌습니다 — 토큰을 문자열 그대로 저장/전달한다면 코드 수정은 불필요합니다
- 개발 중 30분 만료가 불편하면 백엔드에 요청하세요 — 서버 설정으로 늘릴 수 있습니다
- JWT payload(`sub`=userId, `role`)는 디코딩해 볼 수 있지만 **표시 용도로만** 사용하고, 권한 판단의 근거로 신뢰하지 마세요

---

## 🔍 문제 발생 시 빠른 진단표

| 증상 | 원인 | 해결 |
|---|---|---|
| 서버 부팅 실패 (JwtProperties 에러) | `jwt.secret` 없음 | `application-secret.yml`에 추가 |
| 테스트 무더기 실패 (Hibernate Dialect 에러) | 로컬 MySQL 안 떠 있음 | Docker에서 `gather-mysql` 시작 |
| CI에서 spotlessCheck 실패 | 포맷 위반 | `./gradlew spotlessApply` 후 커밋 |
| API가 전부 401 | 토큰 없음/만료/헤더 오타 | 헤더 형식과 `error.code` 확인 |
| reissue 실패 | 이전 Refresh Token 재사용 (rotation) | 마지막으로 받은 Refresh인지 확인 |
