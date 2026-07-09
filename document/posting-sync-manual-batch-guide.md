# 1365 API 키 로테이션 수동 배치 가이드 (인프라 담당자용)

시범 배포 후, 봉사공고 전체 스캔(full-scan)을 빨리 채우기 위해 여러 사람이 각자 발급받은
1365(공공데이터포털) 서비스키를 인프라 담당자에게 전달하고, 그 키들을 순서대로 하나씩 운영 서버에 넣어가며 수동으로 배치를 실행하는 절차입니다.

이 작업은 EC2 서버 접속 권한이 있는 인프라 담당자 1인이 진행합니다. 다른 팀원은 자기 계정으로
발급받은 키만 전달하면 되고, 이 문서의 나머지 절차는 몰라도 됩니다.

---

## 0. 미리 알아야 할 것

### 왜 키를 여러 개 돌리나?

- 1365 API 서비스키(계정)는 하루 호출 횟수가 제한돼 있습니다.
- 봉사공고 전체 스캔 1회 실행은 목록 조회(약 90회) + 신규 공고 상세 조회(최대 800건, 코드상
  `PostingSyncService.MAX_DETAIL_LOOKUPS_PER_RUN`)로 구성되어, **키 1개의 일일 한도를 거의
  다 소진**합니다. 따라서 키 1개 = 사실상 1회 실행이라고 보면 됩니다.
- 초기 백로그(쌓여있는 미수집 공고)가 800건보다 많으면, 키를 바꿔가며 여러 번 실행해야
  하루 안에 다 채울 수 있습니다.

### 지금 서버가 어떻게 동작하는지

- `PostingSyncScheduler`가 매일 새벽 3시(KST)에 자동으로 1회 동기화를 실행합니다
  (`src/main/java/com/gather/gather/domain/posting/scheduler/PostingSyncScheduler.java`).
  이 스케줄러는 **그 시점에 설정돼 있는 키**를 그대로 사용합니다.
- 새벽 3시를 기다리지 않고 지금 바로 실행하고 싶을 때 쓰는 수동 트리거 엔드포인트가 있습니다:
  `POST /api/v1/postings/sync` (`PostingSyncController.java`).
  단, 이 엔드포인트는 `posting.sync.manual-endpoint-enabled=true`일 때만 활성화됩니다.
  운영 서버에는 기본적으로 꺼져 있으므로, 이번 작업을 위해 **임시로 켜야 합니다.**
- 이 엔드포인트는 로그인 토큰(JWT)이 있어야 호출됩니다.
  **PR51(`feature/posting-sync-admin-only`) 머지 이후에는 ADMIN role 계정만 호출 가능**합니다
  (`SecurityConfig`에 `hasRole("ADMIN")` 매처 추가됨). 일반 회원가입 계정은 기본 `UserRole.USER`라
  403(`FORBIDDEN`)이 납니다 — 자세한 내용은 아래 "3. 로그인" 참고.
- 실패(할당량 초과 등)는 curl 응답에는 자세히 안 나오고 **서버 로그에만** 정확한 원인이
  찍힙니다. (아래 트러블슈팅 참고)

### 실패 시 주의사항

동기화가 통째로 실패하면, 오늘 마감되는 공고는 1365 API에서 다음날부터 완전히
사라져서 다시 수집할 방법이 없습니다(스케줄러 코드 주석 참고). 실행할 때마다
결과(`scanned/inserted/updated/failed/skipped`)를 확인하고 넘어가세요.

---

## 1. 준비물

- 팀원들에게 전달받은 1365 서비스키 목록 (data.go.kr에서 각자 발급)
- EC2 SSH 접속 정보
- 로그인 가능한 계정 (이메일/비밀번호) — JWT 발급용
- **(PR51 머지 이후) 그 계정이 ADMIN role이어야 함** — 아래 3번 참고

> **1365 키 취급 주의**: 팀원의 1365 서비스키도 개인 발급 API 키인 만큼 민감정보입니다.
> 팀 채널 등으로 전달받을 때 가능하면 비공개 DM으로 받고, 전달받은 메시지/메모는 작업이
> 끝나면 삭제하세요. env 파일에도 필요한 키 외에 과거 키를 남겨두지 마세요.

---

## 2. 최초 1회: 서버 접속 & 현재 설정 확인

```bash
ssh <EC2_USER>@<EC2_HOST>

# 현재 env 파일 백업 (실수 대비, 항상 먼저)
sudo cp /etc/gather/gather.env /etc/gather/gather.env.bak.$(date +%Y%m%d-%H%M%S)

# 현재 설정 확인 (기존에 뭐가 들어있는지 먼저 보세요)
# ⚠️ gather.env에는 JWT secret, DB 비밀번호, API 키 등 민감정보가 모두 들어있습니다.
# cat으로 전체를 열람하지 말고, 필요한 키만 grep으로 확인하세요.
sudo grep -E '^(VOLUNTEER_API_SERVICE_KEY|POSTING_SYNC_MANUAL_ENDPOINT_ENABLED)=' /etc/gather/gather.env
```

> **터미널 출력 공유 금지**: 위 명령 결과(특히 실수로 `cat` 전체를 열람한 경우)는 스크린샷,
> 채팅, 로그 등 어디에도 공유하지 마세요. JWT secret이나 DB 비밀번호가 노출되면 즉시
> 팀에 알리고 값을 교체해야 합니다.

---

## 3. 로그인해서 JWT 발급 (1회만 하면 됨, 토큰 만료 전까지 재사용)

```bash
curl -X POST http://<EC2_HOST>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"본인계정이메일","password":"본인비밀번호"}'
```

응답의 `accessToken` 값을 복사해두세요. 이후 모든 동기화 호출에 아래처럼 씁니다:

```
Authorization: Bearer <accessToken>
```

> **PR51(`feature/posting-sync-admin-only`) 머지 이후 주의사항**
>
> `/api/v1/postings/sync`가 ADMIN role 전용으로 바뀝니다. 일반 회원가입 계정은 기본
> `UserRole.USER`이고, 코드 전체에 ADMIN을 부여하는 API/시드가 없어서 **DB에서 직접
> role을 바꿔야 합니다** (관리자 지정 절차가 아직 없는 임시 방편입니다):
>
> ```sql
> UPDATE users SET role = 'ADMIN' WHERE email = '본인계정이메일';
> ```
>
> 위 작업이 끝나고 나서 로그인해야 JWT의 role 클레임에 ADMIN이 담깁니다 (이미 발급받은
> 토큰은 role 변경 전 것이므로 다시 로그인해서 새 토큰을 받으세요). 작업이 끝나면 이
> role을 계속 ADMIN으로 둘지 다시 USER로 되돌릴지 팀과 상의하세요. 되돌리기로 했다면
> 아래 원복 SQL을 실행하세요 (작업 시작 시 UPDATE한 계정과 동일한 이메일이어야 합니다):
>
> ```sql
> UPDATE users SET role = 'USER' WHERE email = '본인계정이메일';
> ```

---

## 4. 키 1개당 반복하는 루프

아래 4단계를 **전달받은 키 목록 순서대로** 반복합니다.

### 4-1. env 파일에 키 반영 (+ 최초 1회만 manual-endpoint 활성화)

```bash
sudo vi /etc/gather/gather.env
```

다음 두 줄을 확인/수정합니다 (없으면 추가):

```
VOLUNTEER_API_SERVICE_KEY=여기에_이번에_쓸_1365_서비스키
POSTING_SYNC_MANUAL_ENDPOINT_ENABLED=true
```

> `VOLUNTEER_API_SERVICE_KEY`, `POSTING_SYNC_MANUAL_ENDPOINT_ENABLED`는 각각
> `volunteer-api.service-key`, `posting.sync.manual-endpoint-enabled` 프로퍼티에
> 대응하는 Spring Boot 표준 환경변수 이름입니다 (점/하이픈 → 언더스코어, 대문자).

### 4-2. 재시작 & 헬스체크

```bash
sudo systemctl restart gather
sleep 20
curl --fail http://localhost/health
```

헬스체크가 실패하면 아래 트러블슈팅으로 이동하세요.

### 4-3. 수동 동기화 트리거

```bash
curl -X POST http://<EC2_HOST>/api/v1/postings/sync \
  -H "Authorization: Bearer <accessToken>"
```

### 4-4. 결과 확인

성공 응답 예시:

```json
{"success":true,"data":{"scanned":950,"inserted":620,"updated":330,"failed":0,"skipped":0},"error":null}
```

- `inserted`가 0이고 에러도 없다면, 이 키로 더 가져올 신규 공고가 없다는 뜻일 수 있습니다
  (전체 스캔은 이미 저장된 공고는 목록 필드만 갱신하고 상세조회는 신규 공고에만 함).
- 응답이 `success:false`거나 500이면, **반드시 로그를 확인**하세요:

```bash
sudo journalctl -u gather -n 100 --no-pager
```

`1365 API 호출 실패: resultCode=...` 로그가 보이면 그게 실제 원인(대부분 이 키의 할당량
초과)입니다. 이 경우 다음 키로 넘어갑니다 (4-1로 돌아가서 반복).

---

## 5. 작업 종료 후 반드시 정리할 것

모든 키 소진 후, 또는 오늘 목표만큼 채운 후:

1. `VOLUNTEER_API_SERVICE_KEY`를 **매일 새벽 3시 자동 스케줄러가 계속 써야 할 정식 키**로
   되돌려놓습니다. (마지막에 넣은 임시 키를 그대로 두면 안 됩니다 — 새벽 3시에 그 키로
   자동 실행되다가 할당량이 이미 소진돼 있으면 그날 배치가 실패합니다.)
2. `POSTING_SYNC_MANUAL_ENDPOINT_ENABLED`는 원래 꺼져 있던 설정이므로, 특별히 계속 열어둘
   이유가 없다면 다시 지우거나 `false`로 되돌리세요. (이 엔드포인트는 원래 "로컬 검증용
   임시 엔드포인트"로 만들어진 것이라 운영에 계속 열어두는 건 권장되지 않습니다.)
3. 다시 재시작 + 헬스체크:

```bash
sudo systemctl restart gather
sleep 20
curl --fail http://localhost/health
```

4. `/etc/gather/gather.env.bak.*` 백업 파일은 문제없으면 그대로 두거나 정리하세요.

---

## 6. 트러블슈팅

| 증상 | 원인 | 확인/해결 |
|---|---|---|
| curl 응답이 일반적인 500 (`INTERNAL_SERVER_ERROR`) | 여러 원인 가능 (키 할당량 초과, 코드 문제 등 구분 안 됨) | `sudo journalctl -u gather -n 100 --no-pager`로 실제 예외 메시지 확인 |
| 로그에 `1365 API 호출 실패: resultCode=...` | 대부분 이 키의 일일 요청 한도 초과 | 다음 키로 교체 후 재시작 |
| `POST /api/v1/postings/sync` 호출 시 404 | `POSTING_SYNC_MANUAL_ENDPOINT_ENABLED=true` 적용 안 됨 (재시작 누락 또는 오타) | env 파일 재확인 후 재시작 |
| `POST /api/v1/postings/sync` 호출 시 401 | JWT 없음/만료 | 3번 단계로 로그인 다시 |
| `POST /api/v1/postings/sync` 호출 시 403 (`FORBIDDEN`) | (PR51 이후) 로그인 계정이 ADMIN role이 아님 | DB에서 `role`을 `ADMIN`으로 바꾼 뒤 **다시 로그인**해서 새 토큰 발급 |
| health check 실패, 재시작 후에도 안 뜸 | 부팅 자체 실패 (env 값 오타 등) | `sudo journalctl -u gather -n 100 --no-pager`로 부팅 로그 확인, 필요시 `.bak` 파일로 env 롤백 |

---

## 참고 코드 위치

- 스케줄러: `src/main/java/com/gather/gather/domain/posting/scheduler/PostingSyncScheduler.java`
- 수동 트리거 컨트롤러: `src/main/java/com/gather/gather/domain/posting/controller/PostingSyncController.java`
- 동기화 로직 / 페이지 크기·상세조회 상한: `src/main/java/com/gather/gather/domain/posting/service/PostingSyncService.java`
- 1365 API 클라이언트 / 재시도 로직: `src/main/java/com/gather/gather/domain/posting/client/VolunteerApiClient.java`
- 키/베이스 URL 설정: `src/main/resources/application.yml`, `application-secret.yml.example`
- 배포 스크립트 (systemd 서비스명 `gather`, 헬스체크 경로): `scripts/deploy.sh`
