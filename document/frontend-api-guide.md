# 🎨 프론트엔드 API 연동 가이드 — JWT 인증

백엔드에 JWT 인증이 적용되었습니다. 이 문서는 프론트엔드에서 API를 연동할 때 알아야 할 전부입니다.

---

## 1️⃣ 모든 보호 API에 헤더 필수

공개 API(회원가입/로그인/재발급/로그아웃, 지역/카테고리 조회) 외 **모든 API 요청에 아래 헤더가 필요합니다.**

```
Authorization: Bearer <accessToken>
```

헤더가 없으면 401이 반환됩니다.

## 2️⃣ 401 응답 처리 — `error.code`로 분기

401 응답은 공통 에러 포맷 그대로입니다.

```json
{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }
```

| code | 의미 | 프론트 대응 |
|---|---|---|
| `UNAUTHORIZED` | 토큰 없음 / 헤더 형식 오류 | 로그인 화면으로 이동 |
| `EXPIRED_TOKEN` | Access Token 만료 | **재발급 후 원 요청 재시도** |
| `INVALID_TOKEN` | 무효 토큰 (서명 오류 등) | 토큰 폐기 후 재로그인 유도 |

## 3️⃣ 토큰 수명과 재발급 플로우

- Access Token: **30분** / Refresh Token: **14일**
- `EXPIRED_TOKEN` 401 수신 → `POST /api/v1/auth/reissue`(Refresh Token 사용) → 새 토큰 쌍 발급 → 원 요청 재시도, 하는 인터셉터 구현을 권장합니다
- ⚠️ **재발급하면 Refresh Token도 새것으로 교체됩니다 (rotation).** 응답으로 받은 새 Refresh Token을 반드시 저장하고 이전 것은 버리세요 — 이전 Refresh Token은 즉시 무효화되어 재사용하면 재발급이 실패합니다
- 재발급도 실패하면 (Refresh 만료 등) 재로그인을 유도하세요

## 4️⃣ 기타

- API 명세는 서버의 `/swagger-ui.html`에서 확인할 수 있고, 테스트 계정은 회원가입 API로 직접 생성하면 됩니다
- Access Token 형식이 랜덤 문자열에서 JWT(`eyJ...`)로 바뀌어 길이가 길어졌습니다 — 토큰을 문자열 그대로 저장/전달한다면 코드 수정은 불필요합니다
- 개발 중 30분 만료가 불편하면 백엔드에 요청하세요 — 서버 설정으로 늘릴 수 있습니다
- JWT payload(`sub`=userId, `role`)는 디코딩해 볼 수 있지만 **표시 용도로만** 사용하고, 권한 판단의 근거로 신뢰하지 마세요

## 🔍 문제 발생 시 빠른 진단표

| 증상 | 원인 | 해결 |
|---|---|---|
| API가 전부 401 | 토큰 없음/만료/헤더 오타 | 헤더 형식과 `error.code` 확인 |
| reissue 실패 | 이전 Refresh Token 재사용 (rotation) | 마지막으로 받은 Refresh인지 확인 |
| 로그인 직후에도 401 | `Bearer ` 접두사 누락 또는 오타 | `Authorization: Bearer <token>` 형식 확인 |
