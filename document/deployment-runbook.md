# Gather Backend 운영 배포 Runbook

이 문서는 Gather Backend의 현재 운영 구조, 자동 배포 흐름, 환경변수 계약과 기본 장애 대응 절차를 정리한다.

- 기준 코드: 2026-08-10 `develop`
- 운영 서버 정보: EC2에서 읽기 전용 명령으로 확인한 값
- 실제 secret 값, EC2 Public IP, PEM 경로와 개인 PC 경로는 기록하지 않는다.
- 코드와 문서가 충돌하면 현재 `develop`의 배포 workflow와 script를 우선 확인한다.

## 1. 시스템 구조

```text
Client
  → HTTP :80 (HTTPS로 301 redirect)
  → HTTPS :443
  → Nginx (TLS termination)
  → http://127.0.0.1:8080
  → Spring Boot
  → Docker MySQL :3306
```

운영 API 주소는 다음과 같다.

```text
https://api.gathernow.kr
```

외부 health check:

```text
https://api.gathernow.kr/health
```

Spring Boot에 직접 수행하는 내부 health check:

```text
http://localhost:8080/health
```

## 2. 주요 운영 위치

| 항목 | 위치 |
| --- | --- |
| 현재 실행 JAR | `/opt/gather/gather.jar` |
| 새 배포 JAR | `/opt/gather/gather.jar.new` |
| 배포 script | `/opt/gather/deploy.sh` |
| 배포 환경 검증 script | `/opt/gather/validate-deploy-env.sh` |
| 운영 환경변수 | `/etc/gather/gather.env` |
| systemd unit | `/etc/systemd/system/gather.service` |

## 3. systemd 계약

| 항목 | 현재 운영 값 |
| --- | --- |
| Service | `gather` |
| Unit | `/etc/systemd/system/gather.service` |
| User | `ubuntu` |
| Restart | `always` |
| EnvironmentFile | `/etc/gather/gather.env` |
| ExecStart | `/usr/bin/java -Xmx384m -jar /opt/gather/gather.jar` |

환경변수 전달 경로는 다음과 같다.

```text
/etc/gather/gather.env
  → systemd EnvironmentFile
  → java process
  → Spring Boot relaxed binding
```

`deploy.sh`는 환경파일 일부를 배포 전에 검사하지만 애플리케이션 환경변수를 직접 `source`하지 않는다. 실제 환경변수 주입은 systemd의 `EnvironmentFile`이 담당한다.

JVM 기본 timezone은 UTC가 운영 계약이다. `/etc/gather/gather.env`의 `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`가 `java` 프로세스에 적용되며, 애플리케이션은 Spring 기동 전에 JVM timezone을 검증한다. UTC와 동등하지 않은 timezone이면 기동을 중단한다.

- 토큰 만료와 생성·수정 시각 같은 절대 시각은 UTC를 사용한다.
- 마감일과 한국 기준 알림일 같은 지역 달력 날짜는 `Asia/Seoul`을 명시한다.

## 4. Nginx와 TLS

현재 Nginx 계약은 다음과 같다.

```text
server_name api.gathernow.kr
listen 443 ssl
proxy_pass http://127.0.0.1:8080
```

- `http://api.gathernow.kr/*` 요청은 HTTPS로 301 redirect한다.
- TLS는 Nginx에서 종료한다.
- 인증서는 Let's Encrypt/Certbot으로 관리한다.
- Certbot renewal timer가 활성화되어 있다.
- Spring Boot의 `server.forward-headers-strategy=framework` 설정이 proxy forward header 처리를 지원한다.

## 5. Security Group과 SSH

현재 EC2 listen 및 Security Group 계약은 다음과 같다.

| Port | 역할 | 외부 inbound |
| ---: | --- | --- |
| 22 | SSH 및 GitHub Actions 배포 | 허용 |
| 80 | Nginx HTTP redirect | 허용 |
| 443 | Nginx HTTPS | 허용 |
| 8080 | Spring Boot | 없음 |
| 3306 | Docker MySQL | 없음 |

Spring Boot는 현재 `*:8080`으로 listen하지만 Security Group에 8080 inbound가 없어 인터넷에서 직접 접근할 수 없다. Nginx만 `127.0.0.1:8080`으로 proxy한다. MySQL 3306도 외부 inbound가 없다.

SSH 22번 포트는 현재 GitHub Actions가 EC2에 JAR와 배포 script를 전송하고 원격 배포를 실행하는 경로에서도 사용하므로 외부 inbound가 필요하다. SSH는 public-key authentication만 허용하며 password authentication은 비활성화되어 있다.

Public SSH 제거 및 AWS SSM/OIDC 기반 배포 전환은 후속 운영 보안 개선 대상이다. 이번 문서 작업에서는 Security Group이나 SSH 설정을 변경하지 않는다.

## 6. Docker MySQL과 schema 관리

운영 DB는 EC2 내부 Docker MySQL이다. 데이터는 Docker named volume으로 영속화한다.

```text
gather-db_gather_mysql_data
  → /var/lib/mysql
```

다음을 혼동하지 않는다.

```text
Docker volume persistence: 존재
DB backup/restore 체계: 별도 Release Blocker 또는 후속 작업
```

애플리케이션 schema 정책은 다음과 같다.

- Flyway가 애플리케이션 시작 시 아직 적용되지 않은 migration을 순서대로 실행한다.
- Hibernate는 `spring.jpa.hibernate.ddl-auto=validate`로 mapping과 실제 schema를 검증한다.
- Hibernate가 운영 table을 자동 생성하거나 변경하지 않는다.
- JAR rollback은 이미 적용된 Flyway migration을 되돌리지 않는다.

## 7. 자동 배포 흐름

배포는 `.github/workflows/deploy.yml`에 따라 `main` push 또는 `workflow_dispatch`에서 실행된다.

```text
main push / workflow_dispatch
  → GitHub Actions
  → MySQL 8.4 service container 준비
  → ./gradlew clean test
  → ./gradlew bootJar
  → deploy/gather.jar.new 준비
  → SSH/SCP로 EC2 /opt/gather에 업로드
  → /opt/gather/deploy.sh 실행
  → /etc/gather/gather.env 사전 검증
  → EC2 Instance Profile 확인
  → 현재 JAR timestamp backup
  → gather.jar.new을 gather.jar로 교체
  → systemctl restart gather
  → 90초 대기
  → http://localhost:8080/health
  → 성공 또는 이전 JAR 복원 시도
```

GitHub Actions가 사용하는 repository secret은 다음과 같다.

| Secret | 용도 |
| --- | --- |
| `EC2_SSH_KEY` | EC2 public-key SSH 인증 |
| `EC2_HOST` | 배포 대상 host |
| `EC2_USER` | SSH 사용자 |

실제 값은 repository나 이 문서에 기록하지 않는다.

### 배포 전 검증 범위

현재 `develop`의 배포 script가 JAR 교체 전에 검사하는 항목은 다음과 같다.

- `/etc/gather/gather.env` 읽기 가능 여부
- S3 필수 변수 3개 존재 여부
- 재가입 제한 HMAC secret과 key version
- social account 암호화 key와 key version
- Kakao Admin 및 unlink worker 활성화 조합
- OCTOMO API key
- 이메일 모드가 `smtp`인지와 SMTP 사용자·비밀번호 존재 여부
- Refresh Cookie의 Secure 값이 `true`인지
- EC2 Instance Profile의 기대 역할 연결 여부

DB, JWT, Kakao OAuth와 JVM UTC 운영 계약은 아래 표에 포함되지만 현재 `develop`의 `validate-deploy-env.sh`가 모두 강제하는 것은 아니다. 이 값들은 JVM 또는 Spring Boot 시작 시 실패할 수 있다.

## 8. 운영 환경변수 계약

운영 환경파일 예시는 [`gather.env.example`](gather.env.example)을 참고한다. 실제 값을 채운 파일은 서버의 `/etc/gather/gather.env`에서만 관리한다.

### Datasource

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | 필수 | Spring Boot/Flyway 기동 |
| `SPRING_DATASOURCE_USERNAME` | DB 사용자 | 필수 | Spring Boot/Flyway 기동 |
| `SPRING_DATASOURCE_PASSWORD` | DB 비밀번호 | 필수 | Spring Boot/Flyway 기동 |

운영 JAR는 Git에서 제외된 `application-local.yml`에 의존하지 않는다. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`는 현재 production JAR의 datasource 계약이 아니다.

### JVM timezone

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `JAVA_TOOL_OPTIONS` | JVM 기본 timezone을 UTC로 고정 | 운영에서 `-Duser.timezone=UTC` 필수 | 애플리케이션 기동 전 검증 |

`JAVA_TOOL_OPTIONS`는 Spring Boot 환경변수가 아니라 JVM launcher가 읽는 값이다. 현재 deploy validator는 이 값을 검사하지 않는다. 다만 이 옵션이 적용되지 않아 JVM 기본 timezone이 UTC와 동등하지 않거나 다른 timezone을 지정하면 애플리케이션이 기동하지 않는다.

### Auth와 cookie

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `JWT_SECRET` | Access Token 서명 | 필수 | 애플리케이션 기동 |
| `GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET` | 재가입 제한 식별자 HMAC | 필수 | deploy validator + 애플리케이션 기동 |
| `GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION` | HMAC key version | 필수 | deploy validator + 애플리케이션 기동 |
| `GATHER_AUTH_EMAIL_VERIFICATION_HMAC_SECRET` | 이메일 인증 코드 HMAC | 필수 | deploy validator + 애플리케이션 기동 |
| `GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY` | social account AES-256-GCM key | 필수 | deploy validator + 애플리케이션 기동 |
| `GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION` | 암호화 key version | 필수 | deploy validator + 애플리케이션 기동 |
| `GATHER_REFRESH_COOKIE_SECURE` | HTTPS 전용 Refresh Cookie | 운영에서 `true` 필수 | deploy validator + 애플리케이션 binding |
| `GATHER_REFRESH_COOKIE_SAME_SITE` | Refresh Cookie SameSite | 선택, 기본 `Lax` | 애플리케이션 binding |

`GATHER_AUTH_EMAIL_VERIFICATION_HMAC_SECRET`은 Base64 디코딩 후 32바이트 이상이어야 하며, `JWT_SECRET`이나 `GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET`과 다른 값을 써야 한다. 한 키가 유출됐을 때 다른 용도까지 번지지 않도록 용도별로 키를 분리한다.

#### 이메일 인증 HMAC 키 교체

key version이나 keyring을 두지 않았으므로, 키를 바꾸면 기존에 발급된 인증 코드는 모두 검증할 수 없다. 따라서 교체는 전량 파기 방식으로만 한다. 순서를 지키지 않으면 옛 키로 발급된 코드가 새 키 환경에서 계속 실패한다.

1. 이메일 인증 트래픽을 중단하거나 빠진다.
2. 애플리케이션을 정지해 신규 인증 코드 발급이 불가능한 상태를 만든다.
3. `email_verification` 테이블을 전량 삭제한다.
4. `/etc/gather/gather.env`의 `GATHER_AUTH_EMAIL_VERIFICATION_HMAC_SECRET`을 교체한다.
5. `scripts/validate-deploy-env.sh /etc/gather/gather.env`로 검증한다.
6. 애플리케이션을 기동한다.
7. health와 readiness를 확인한다.
8. 이메일 인증 트래픽을 재개한다.

행을 먼저 지운 뒤 옛 키를 쓰는 애플리케이션이 계속 코드를 발급하면 검증 불가능한 행이 다시 쌓이므로, 반드시 2번(신규 발급 차단)을 3번(전량 삭제)보다 먼저 수행한다.

### Kakao

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `KAKAO_REST_API_KEY` | Kakao OAuth client id | 필수 | 애플리케이션 기동 |
| `KAKAO_CLIENT_SECRET` | Kakao OAuth client secret | 필수 | 애플리케이션 기동 |
| `KAKAO_ADMIN_ENABLED` | Kakao Admin 기능 활성화 | 필수, `true`/`false` 명시 | deploy validator + 애플리케이션 기동 |
| `KAKAO_UNLINK_WORKER_ENABLED` | unlink worker 활성화 | 필수, `true`/`false` 명시 | deploy validator + 애플리케이션 기동 |
| `KAKAO_ADMIN_KEY` | Kakao Admin key | `KAKAO_ADMIN_ENABLED=true`일 때 필수 | deploy validator + 애플리케이션 기동 |

`KAKAO_UNLINK_WORKER_ENABLED=true`이면 `KAKAO_ADMIN_ENABLED=true`여야 한다.

### Mail

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `GATHER_EMAIL_MODE` | 이메일 sender 선택 | 운영에서 `smtp` 필수 | deploy validator + 애플리케이션 binding |
| `SPRING_MAIL_USERNAME` | SMTP 사용자 | 운영에서 필수 | deploy validator + 메일 발송 시 사용 |
| `SPRING_MAIL_PASSWORD` | SMTP app password | 운영에서 필수 | deploy validator + 메일 발송 시 사용 |

현재 `develop`의 기본 이메일 모드는 `log`지만 deploy validator는 운영 배포에서 `smtp`만 허용한다. SMTP 사용자나 비밀번호가 누락되면 JAR 교체 전에 배포를 중단한다.

### S3

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `GATHER_AWS_REGION` | AWS region | 필수 | deploy script + 애플리케이션 binding |
| `GATHER_AWS_S3_BUCKET` | 객체 저장 bucket | 필수 | deploy script + 애플리케이션 binding |
| `GATHER_AWS_S3_PUBLIC_BASE_URL` | 공개 객체 URL prefix | 필수 | deploy script + 애플리케이션 binding |

AWS access key와 secret key는 환경파일에 두지 않는다. 애플리케이션은 EC2 Instance Profile과 AWS SDK 기본 credential chain을 사용한다.

### 외부 API

| 변수 | 용도 | 필수/조건부 | 현재 검증 위치 |
| --- | --- | --- | --- |
| `VOLUNTEER_API_SERVICE_KEY` | 1365 봉사공고 동기화 | 해당 기능 운영 시 필수 | 현재 deploy validator 미검증, API 호출 시 사용 |
| `OCTOMO_API_KEY` | OCTOMO 휴대폰 점유 인증 | 필수 | deploy validator + OCTOMO API 호출 시 사용 |

`OCTOMO_BASE_URL`과 `OCTOMO_RECEIVER_NUMBER`는 애플리케이션 기본값이 있으므로 운영 환경파일의 필수 항목이 아니다. 공급자 계약이 변경되어 기본값을 덮어써야 할 때만 명시한다.

현재 `develop`에 없는 변경의 환경변수는 이 계약에 포함하지 않는다.

## 9. 기본 운영 확인

다음 명령은 secret 값을 출력하지 않는다.

```bash
sudo systemctl status gather --no-pager
sudo journalctl -u gather -n 100 --no-pager
curl --fail http://localhost:8080/health
curl --fail https://api.gathernow.kr/health
sudo docker ps --filter name=gather-mysql
```

Nginx와 인증서 갱신 상태:

```bash
sudo systemctl status nginx --no-pager
sudo nginx -t
systemctl list-timers --all | grep -Ei 'certbot|acme'
```

환경변수 값이 아니라 존재 여부만 확인하려면 다음 방식을 사용한다.

```bash
for key in JWT_SECRET SPRING_DATASOURCE_URL KAKAO_REST_API_KEY GATHER_AWS_REGION
do
  if sudo grep -qE "^${key}=.+$" /etc/gather/gather.env; then
    echo "${key}: PRESENT"
  else
    echo "${key}: MISSING"
  fi
done
```

`cat /etc/gather/gather.env` 또는 값이 포함되는 `grep` 결과를 채팅, issue, PR이나 screenshot으로 공유하지 않는다.

## 10. Recovery와 미구현 운영 기능

### 현재 존재

- 기존 JAR timestamp backup
- 새 JAR 교체 후 내부 health check
- health check 실패 시 이전 JAR 복원 및 서비스 재시작 시도
- Docker named volume을 통한 MySQL 데이터 영속화

### 현재 보장하지 않음

- Flyway migration rollback
- DB backup과 restore
- `systemctl restart` 자체가 실패한 경우의 완전한 자동 복구
- JAR backup retention과 자동 정리
- 5xx monitoring과 alert
- process down monitoring
- disk, MySQL, batch failure alert

JAR rollback 성공을 DB rollback 또는 전체 시스템 복구 성공으로 표현하지 않는다. DB backup/restore와 monitoring은 별도 Release Blocker 또는 후속 운영 작업으로 관리한다.

### 이메일 인증 평문 행 파기와 rollback

V65 이후 애플리케이션은 기동 시점에 구 버전이 남긴 평문 인증 행을 파기한다. 파기에 실패하면 예외를 그대로 전파해 기동을 실패시키고, 배포 스크립트의 health check 실패 경로를 통해 이전 JAR로 되돌아간다.

#### 최초 HMAC 전환 배포의 사용자 영향

파기 대상은 rollback 중에 생긴 행만이 아니다. 최초 V65/HMAC 전환 배포에서도 기존 평문 `email_verification` 행 전체가 파기 대상이므로, 배포 순간 유효한 인증 절차를 진행 중이던 사용자에게 다음 영향이 있다.

- 발송됐지만 아직 입력하지 않은 10분짜리 인증 코드가 무효화될 수 있다.
- 인증까지 마쳤지만 회원가입을 끝내지 않은 30분짜리 인증 결과가 무효화될 수 있다.
- 영향을 받은 사용자는 이메일 인증을 처음부터 다시 진행해야 한다. 별도의 안내나 데이터 복구 수단은 없다.

영향 범위는 배포 순간 유효한 기존 인증 행을 가진 사용자로 한정된다. 전체 사용자나 이미 가입을 마친 사용자는 영향을 받지 않으며 서비스 중단도 필요하지 않다. 다만 재인증을 요구받는 사용자 수를 줄이기 위해 최초 전환 배포는 이메일 인증 트래픽이 낮은 시간대에 수행하는 것을 권장한다.

#### rollback 시 영향

이 파기는 rollback 시 사용자 영향의 범위를 줄이지만 모든 기동 실패에서 기존 인증 코드가 보존됨을 보장하지는 않는다.

- 파기 이전 단계에서 기동이 실패하면 기존 행이 남아 있어, 이전 JAR로 되돌린 뒤 기존 인증 코드와 인증 결과를 계속 쓸 수 있다.
- 파기가 성공한 뒤 다른 기동 단계에서 실패하면 기존 행은 이미 삭제된 상태다. 이전 JAR로 되돌릴 수는 있지만 기존 인증 코드와 인증 결과는 복구되지 않고, 사용자는 인증 코드를 다시 발송받아야 한다.

기동 러너는 준비 완료 이전 관문일 뿐이며 웹 포트가 먼저 열릴 수 있다. 따라서 인증 확인과 회원가입 요청 경로에서도 평문 행을 별도로 차단한다.

## 11. 장애 확인 순서

### 배포가 JAR 교체 전에 중단된 경우

1. GitHub Actions의 실패 step을 확인한다.
2. 누락된 변수 이름만 확인하고 실제 값은 출력하지 않는다.
3. Instance Profile 연결과 기대 역할을 확인한다.
4. 설정을 바로잡은 뒤 전체 배포 workflow를 다시 실행한다.

### 서비스가 기동하지 않는 경우

```bash
sudo systemctl status gather --no-pager
sudo journalctl -u gather -n 100 --no-pager
```

Datasource, Flyway, 필수 auth 설정 binding 오류를 우선 확인한다.

### 외부 HTTPS health만 실패하는 경우

```bash
curl --fail http://localhost:8080/health
sudo systemctl status nginx --no-pager
sudo nginx -t
systemctl list-timers --all | grep -Ei 'certbot|acme'
```

내부 health가 성공하고 외부 HTTPS만 실패하면 Nginx, DNS, 인증서와 Security Group을 확인한다.

## 12. 관련 저장소 파일

- `.github/workflows/deploy.yml`
- `scripts/deploy.sh`
- `scripts/validate-deploy-env.sh`
- `src/main/resources/application.yml`
- `src/main/resources/application-secret.yml.example`
- `src/main/resources/application-local.yml.example`
- `document/gather.env.example`
