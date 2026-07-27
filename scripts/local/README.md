# 로컬 모임 시드 데이터

`seed-meetings.sql`은 로컬 개발 DB에 테스트용 모임 5개를 추가합니다.
Flyway 마이그레이션이 아니므로 서버 시작 시 자동 실행되지 않으며 운영 DB에 반영되지 않습니다.

## 실행 전 조건

- Flyway 마이그레이션이 완료되어 있어야 합니다.
- `users` 테이블에 `ACTIVE` 상태 사용자가 최소 1명 있어야 합니다.
- `region` 테이블에 지역 데이터가 있어야 합니다.

활성 사용자 중 ID가 가장 작은 사용자가 시드 모임의 모임장이 됩니다.

## 실행 방법

IntelliJ의 Database 창에서 로컬 `gather_db` 데이터 소스를 선택한 다음
`seed-meetings.sql`을 열고 전체 SQL을 실행합니다.

터미널에서는 다음과 같이 실행할 수 있습니다.

```bash
mysql -u <사용자명> -p gather_db < scripts/local/seed-meetings.sql
```

같은 SQL을 다시 실행해도 동일한 이름의 모임과 모임장 멤버십은 중복 생성되지 않습니다.
