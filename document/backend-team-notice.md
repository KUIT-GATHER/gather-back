#  백엔드 필독 : develop 큰 변경 안내 (안 읽으면 서버 안 뜸)

JWT 인증이 적용됐고, 전체 코드 포맷 정리 + PR 자동 검사(CI)가 들어갔습니다.
아래 1번부터 순서대로만 따라 하면 문제없습니다.

---

## 1. develop pull 받으면 GatherApplication 실행이 안 됩니다 → 시크릿 파일을 만들면 해결

### 무슨 일이 일어나냐면

이제 서버가 시작될 때 JWT 서명용 비밀키(`jwt.secret`)를 검사합니다. 키가 없거나 잘못됐으면
`GatherApplication` 실행이 아래 같은 에러와 함께 즉시 실패합니다:

```
jwt.secret이 설정되지 않았습니다. 로컬은 application-secret.yml, 운영은 JWT_SECRET 환경변수를 확인하세요.
```

버그가 아니라 **의도된 안전장치**입니다 (키 없이 뜨는 게 더 위험하므로).

### 해결 방법 (최초 1회)

1. `src/main/resources/application-secret.yml.example`을 복사해서
   같은 위치에 `application-secret.yml`을 만듭니다. (이 파일은 gitignore라 커밋되지 않습니다)

2. Git Bash에서 아래 명령을 실행합니다:

   ```bash
   openssl rand -base64 48
   ```

   반드시 `48`로 하세요. `64`로 하면 출력이 **두 줄로 잘려 나와서** 그대로 붙여넣으면
   "jwt.secret이 Base64 형식이 아닙니다" 에러로 부팅이 실패합니다. `48`은 정확히 한 줄(64글자)로 나옵니다.

3. 나온 문자열을 파일에 이렇게 넣습니다 (YAML 중첩 구조 주의):

   ```yaml
   jwt:
     secret: 여기에_명령어_출력_문자열_붙여넣기
   ```

- 시크릿은 각자 로컬에서 생성하면 되고, 팀원끼리 같을 필요 없습니다. EC2 접속도 필요 없습니다.
- 조건: Base64 디코딩 시 32바이트 이상 (위 명령은 48바이트라 여유 있게 통과)

---

## 2. 테스트 / 빌드에는 로컬 MySQL이 필요합니다

테스트가 실제 MySQL에 붙어서 돌아갑니다. 요구사항은 딱 하나 :
**`localhost:3306`에 `gather` 데이터베이스가 있는 MySQL이 실행 중일 것.**
아래 중 자기 상황에 맞는 것만 하면 됩니다.

### A. 이미 MySQL을 쓰고 있던 사람 (설치형이든 Docker든)

그대로 쓰면 됩니다. MySQL이 **실행 중**인지, `gather` 데이터베이스가 있는지만 확인하세요:

```sql
CREATE DATABASE IF NOT EXISTS gather;
```

### B. MySQL이 아예 없는 사람

Docker Desktop 설치 후 이 한 줄이면 끝 (최초 1회만):

```bash
docker run -d --name gather-mysql -e MYSQL_ROOT_PASSWORD=원하는비밀번호 -e MYSQL_DATABASE=gather -p 3306:3306 mysql:8.0
```

이후로는 Docker Desktop에서 `gather-mysql` 컨테이너를 켜기만 하면 됩니다.

### C. 공통 — DB 접속 정보 연결 확인

`src/main/resources/application-local.yml`이 없다면 `.example`을 복사해서 만들고,
자기 DB의 주소/계정/비밀번호가 연결되도록 하세요. (이 파일도 gitignore라 각자 달라도 됩니다)

### D. 공통 — 새 DB라면 Flyway migration 확인

새로 만든 DB에는 애플리케이션 시작 시 Flyway가 `src/main/resources/db/migration`의 migration을
버전 순서대로 적용합니다. 그 뒤 Hibernate의 `ddl-auto: validate`가 Entity mapping과 실제
schema가 일치하는지 검증합니다.

Hibernate가 table을 자동 생성하거나 기존 schema를 임의로 변경하지 않습니다. 애플리케이션이
기동하지 않으면 `Flyway` 오류와 `flyway_schema_history` 상태를 먼저 확인하고, migration 파일이나
운영 DB schema를 임의로 수정하지 말고 팀에 공유하세요.

> 테스트가 무더기로 실패하면서 에러에 `Dialect`, `DataSource`가 보이면
> 대부분 MySQL이 안 켜져 있는 상황입니다. 코드 문제 아닙니다.

---

## 3. 작업 중인 브랜치가 있으면, PR 올리기 전에 이 3줄을 실행하세요

**이번에 전체 코드 포맷 정리(37개 파일)가 develop에 들어갔기 때문에**,
예전 develop 기준으로 작업하던 브랜치는 이 과정을 거쳐야 합니다:

```bash
git merge origin/develop
./gradlew spotlessApply
./gradlew build
```

셋 다 통과하면 커밋/푸시 하면 끝입니다.
(새로 시작하는 작업은 develop을 pull 받고 브랜치를 파면 되고, 이 과정이 필요 없습니다)

### 중요: merge에서 CONFLICT가 뜨는 경우

본인이 수정한 파일을 develop이 포맷 정리해서 그런 겁니다.
**정답은 항상 "내 로직"이고, 포맷은 명령어가 알아서 맞춰주니까 틀릴 방법이 없는 충돌입니다.**

1. 충돌 난 파일을 열면 이런 표시가 있습니다:

   ```
   <<<<<<< HEAD
   (내 브랜치 코드)
   =======
   (develop 코드 — 대부분 들여쓰기/줄바꿈만 다름)
   >>>>>>> origin/develop
   ```

2. **내가 작성한 로직이 살아있게** 남기고, `<<<<<<<` `=======` `>>>>>>>` 이 세 줄은 삭제하세요.
   들여쓰기가 어느 쪽이 맞는지는 **고민하지 마세요**. 다음 명령이 알아서 맞춰줍니다.

3. 그 다음 그대로 진행:

   ```bash
   ./gradlew spotlessApply
   ./gradlew build
   git add -A
   git commit
   git push
   ```

> 꼬였다 싶으면 `git merge --abort` — 머지 전 상태로 완전히 되돌아갑니다 (몇 번이든 재시도 가능).
> 그래도 막히면 부르세요, 같이 해결해요.

---

## 4. PR을 올리면 자동 검사가 돌아갑니다 (실패하면 머지 불가)

PR마다 GitHub Actions가 **포맷 검사(Spotless) + 전체 테스트**를 자동 실행합니다.

- **빨간불이 뜨면** 십중팔구 포맷 문제입니다. `./gradlew spotlessApply` 실행 후 커밋 / 푸시하면 해결
- 푸시 전에 미리 확인하고 싶으면 `./gradlew build` (CI와 동일한 검사, 로컬 MySQL 필요)
- 포맷 규칙을 외울 필요는 전혀 없습니다. `spotlessApply`가 항상 정답으로 고쳐줍니다.

---

## 5. 이제 API가 잠겼습니다

대부분의 API가 JWT 토큰 없이는 401을 반환합니다.

- **Swagger에서 테스트하는 방법**: 
  1. 로그인 API로 accessToken 발급
  2. 우측 상단 **Authorize** 버튼에 붙여넣기
   - 이후 요청에 자동으로 붙습니다
- 공개 API (토큰 불필요): `/health`, `/api/v1/auth/**`, `/api/v1/regions`, `/api/v1/categories`, Swagger
- 공개 API에도 Swagger에 자물쇠 아이콘이 뜨지만 표시만 그런 것. 실제로는 토큰 없이 호출됩니다
- **새 API는 기본이 "인증 필요"**
  - 아무 설정 없이 만들면 자동으로 보호됩니다.
  - 공개해야 하는 API만 `SecurityConfig`의 permitAll 목록에 추가하세요 (추가 전 팀 논의)
- 코드에서 로그인 유저 ID가 필요하면 `SecurityUtil.getCurrentUserId()` 사용
  (이제 실제 로그인 유저의 ID를 반환합니다)
- posting 담당: `POST /api/v1/postings/sync`(로컬 수동 동기화)도 이제 토큰이 필요합니다

---

## 🔍 문제 발생 시 빠른 진단표

| 증상 | 원인 | 해결 |
|---|---|---|
| 서버 부팅 실패 (JwtProperties 에러) | `jwt.secret` 없음/형식 오류 | 1번 참고 — `application-secret.yml` 확인 |
| 테스트 무더기 실패 (Dialect/DataSource 에러) | 로컬 MySQL 안 떠 있음 | 2번 참고 — MySQL 시작 |
| Flyway 또는 schema validate 실패 | migration 실패 또는 Entity/schema 불일치 | 2-D 참고 — Flyway 로그와 `flyway_schema_history` 확인 |
| CI에서 spotlessCheck 실패 | 포맷 위반 | `./gradlew spotlessApply` 후 커밋 |
| API가 전부 401 | 토큰 없음/만료 | Swagger Authorize 또는 헤더 확인 |

---

## 📄 참고

- 프론트엔드 API 연동 가이드(Bearer 헤더, 401 분기, 토큰 재발급): `document/frontend-api-guide.md`
- 운영 배포 구조와 기본 장애 대응: `document/deployment-runbook.md`
- 배포는 배포 담당자가 진행합니다. 팀원은 develop 머지까지만 신경 쓰면 됩니다.
