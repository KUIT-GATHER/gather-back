# Kakao unlink 단건 canary 운영 절차

이 문서는 지정한 `kakao_unlink_task.id` 한 건을 기존 unlink 처리 흐름으로 실행하는 절차다. 실제 운영 실행 전에는 대상 계정과 권한을 별도로 승인받고, 아래 사전 조건을 모두 확인한다.

## 1. 사전 조건

- 복구 가능한 전용 Kakao 테스트 계정을 사용한다.
- 배포된 애플리케이션이 실제 `prod` profile을 사용 중인지 확인한다.
- 환경에 `KAKAO_ADMIN_ENABLED=true`가 설정되어 있고 올바른 Kakao Admin Key와 unlink 권한이 준비되어 있어야 한다.
- 모든 운영 인스턴스에서 일반 unlink worker가 비활성화되어 있어야 한다. 실행 프로세스에도 `KAKAO_UNLINK_WORKER_ENABLED=false`를 적용한다.
- `kakao_unlink_worker_control.id=1`이 존재하고 `ACTIVE`여야 한다.
- 대상 task가 `PENDING`이고 `next_attempt_at <= UTC_TIMESTAMP(6)`이어야 한다.
- 대상 task의 claim 필드 네 개가 모두 `NULL`이어야 한다.
- 첫 canary에는 `attempt_count=0`, `retry_cycle=0`인 task를 권장한다. 이는 실행 조건이 아니라 운영상 권고다.

환경 파일, Admin Key, `SPRING_APPLICATION_JSON`, 전체 명령행은 로그나 작업 기록에 출력하지 않는다.

## 2. 실행 전 DB 확인

아래 쿼리의 `<TASK_ID>`만 승인된 값으로 치환한다. provider 식별자와 claim token의 실제 값은 조회하지 않는다.

```sql
SELECT
    id,
    status,
    retry_cycle,
    attempt_count,
    next_attempt_at,
    next_attempt_at <= UTC_TIMESTAMP(6) AS is_due,
    (
        claim_token IS NULL
        AND claimed_by IS NULL
        AND claimed_at IS NULL
        AND lease_expires_at IS NULL
    ) AS claim_fields_clear,
    last_error_type,
    completed_at
FROM kakao_unlink_task
WHERE id = <TASK_ID>;

SELECT
    id,
    status,
    blocked_at,
    blocked_reason,
    updated_at
FROM kakao_unlink_worker_control
WHERE id = 1;

SELECT
    task.id AS task_id,
    task.status AS task_status,
    account.link_status,
    account.generation AS account_generation,
    task.generation AS task_generation,
    account.unlinked_at,
    users.status AS user_status,
    users.withdrawn_at,
    users.anonymized_at,
    (
        SELECT COUNT(*)
        FROM refresh_token token
        WHERE token.user_id = users.id
    ) AS refresh_token_count
FROM kakao_unlink_task task
JOIN social_account account ON account.id = task.social_account_id
JOIN users ON users.id = account.user_id
WHERE task.id = <TASK_ID>;
```

결과가 하나라도 사전 조건과 다르면 실행하지 않는다. 특히 claim 필드가 남아 있으면 수동으로 정리하지 말고 원인을 조사한다.

## 3. 단건 실행

`<TASK_ID>`를 승인된 숫자 ID로 치환한다. 서비스 경로와 unit 이름은 실제 배포 표준에 맞는지 실행 전에 확인한다.

```bash
sudo systemd-run \
  --wait \
  --pipe \
  --collect \
  --unit=gather-kakao-unlink-canary-<TASK_ID> \
  --property=EnvironmentFile=/etc/gather/gather.env \
  /usr/bin/java -jar /opt/gather/gather.jar \
  --spring.profiles.active=prod,kakao-unlink-canary \
  --spring.main.web-application-type=none \
  --gather.scheduling.enabled=false \
  --kakao.admin.enabled=true \
  --kakao.admin.unlink-worker.enabled=false \
  --gather.kakao.unlink-canary.enabled=true \
  --gather.kakao.unlink-canary.task-id=<TASK_ID>

CANARY_EXIT=$?
printf 'canary exit=%s\n' "$CANARY_EXIT"
```

이 명령은 일반 worker와 scheduler를 실행하지 않는 non-web 프로세스를 시작한다. canary는 지정한 PK만 claim하며, Kakao API 호출 중에는 DB transaction을 유지하지 않는다.

## 4. 종료 코드 판정

| 코드 | 의미 | 조치 |
| ---: | --- | --- |
| `0` | 성공 또는 Kakao `ALREADY_UNLINKED`를 성공으로 수렴 | 사후 DB 상태 확인 |
| `10` | 재시도 예약 완료 | `next_attempt_at` 이후 재실행 여부 판단 |
| `21` | task 없음 | 입력 ID와 배포 DB 확인 |
| `22` | `PENDING` 아님 또는 아직 due가 아님 | 상태와 `next_attempt_at` 확인 |
| `23` | lock 경쟁, claim 상실 또는 STALE 전이 | 현재 소유권, lease, task 상태 확인 |
| `24` | worker control 차단 | 설정 오류를 복구한 뒤 기존 resume 절차 사용 |
| `30` | control 차단 없는 실제 `DEAD` 전이 | 오류 분류와 attempt 상태를 조사 |
| `50` | 예상하지 못한 런타임·종료 실패 또는 claim invariant 오류 | 즉시 재실행 금지, DB와 로그를 함께 조사 |
| `60` | runner가 검출한 실행환경 오류 | 실행 옵션과 profile을 수정한 뒤 재검토 |
| `1` | Spring bootstrap 또는 property binding 실패 | 애플리케이션 시작 로그와 설정 확인 |

종료 코드만으로 재실행하지 않는다. 특히 `50`이어도 외부 unlink 이후 DB 결과가 반영되었을 가능성이 있으므로 반드시 사후 DB 상태를 먼저 확인한다.

## 5. 실행 후 확인

2절의 조회를 다시 실행하고 다음을 확인한다.

- task의 `status`, `retry_cycle`, `attempt_count`, `next_attempt_at`, `last_error_type`, `completed_at`
- SocialAccount의 `link_status`, `unlinked_at`
- User의 `status`, `withdrawn_at`, `anonymized_at`
- RefreshToken 잔여 개수
- 승인된 테스트 계정에서 Kakao 연결 해제 여부

성공 시 task는 `SUCCEEDED`, SocialAccount는 `UNLINKED`, User는 `WITHDRAWN`으로 수렴하고 관련 토큰과 개인정보 정리 결과가 기존 탈퇴 설계와 일치해야 한다. 재시도나 terminal 결과에서는 종료 코드와 DB 상태가 위 표의 의미와 일치하는지 확인한다.

필요한 로그만 unit 이름으로 조회한다. 전체 환경이나 전체 명령행을 출력하지 않는다.

```bash
sudo journalctl --unit=gather-kakao-unlink-canary-<TASK_ID> --no-pager
```

로그를 공유할 때 Admin Key, Kakao 회원번호, claim token, provider 식별자·암호문·HMAC, 이메일, 전화번호, access/refresh token, 요청·응답 본문을 포함하지 않는다.

## 6. 실패 대응

- 조사 중에도 모든 운영 인스턴스의 일반 worker는 비활성화 상태를 유지한다.
- exit `50`은 즉시 재실행하지 않는다. task, SocialAccount, User 상태와 canary 로그를 대조한다.
- exit `24`는 설정 오류를 복구한 후 기존 Kakao unlink resume 절차로 worker control을 복구한다. DB에서 control 상태를 임의로 바꾸지 않는다.
- exit `23`은 다른 실행의 소유권과 lease를 확인한다. claim 필드를 수동 정리하거나 만료된 `PROCESSING` task를 canary로 회수하지 않는다.
- exit `22`의 `NOT_DUE`는 `next_attempt_at` 이후에만 재실행을 검토한다.
- 외부 unlink 성공 뒤 DB 반영이 실패했더라도 다음 정상 실행에서 Kakao `ALREADY_UNLINKED` 응답으로 멱등 수렴할 수 있다. 재실행 전 DB와 현재 Kakao 연결 상태를 확인한다.

이 PR은 Discord 알림, heartbeat, 자동 DEAD 감지, CloudWatch·journald 설정 변경을 포함하지 않는다.
