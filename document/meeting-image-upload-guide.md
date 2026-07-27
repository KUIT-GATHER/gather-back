# 모임 이미지 업로드 연동

모임(Meeting) 이미지는 profile 이미지와 동일한 Presigned PUT 방식을 사용합니다.
차이는 **모임당 최대 3장**, **모임장(HOST) 전용**, **같은 버킷의 `meetings/*` prefix** 세 가지입니다.
저장 데이터는 전체 URL이 아니라 objectKey(`meetings/{meetingId}/{uuid}.{ext}`)만 보관합니다.

## 프론트엔드 처리 순서

이미지는 모임이 생성된 뒤(모임 ID 확보) 붙입니다.

1. `POST /api/v1/meetings/{meetingId}/images/presigned-url`에 이미지의 `contentType`과 바이트 크기
   `fileSize`를 보냅니다. 모임장만 발급됩니다. 3장을 올리려면 이 호출을 3번 해 각각 URL을 발급받습니다.
2. 각 응답의 `uploadUrl`에 이미지 파일을 `PUT`합니다. 이때 URL 발급 요청과 동일한
   `Content-Type`, 정확한 파일 크기 및 `If-None-Match: *` 헤더를 사용해야 합니다.
   같은 URL로 두 번째 PUT을 시도하면 S3가 `412 Precondition Failed`로 거부합니다.
3. 업로드에 성공한 `objectKey`들을 **노출 순서대로 배열**로 모아
   `PATCH /api/v1/meetings/{meetingId}/images`의 `objectKeys`에 보내 모임 이미지로 반영합니다.
   유지할 기존 이미지 key와 새로 올린 key를 함께 담을 수 있습니다(최대 3개).
4. 반영 응답의 `imageUrls`는 공개 조회 URL 목록(노출 순서)이므로 이미지 `src`로 사용할 수 있습니다.
   현재 이미지는 `GET /api/v1/meetings/{meetingId}/images`로도 조회합니다.

허용 형식은 JPEG, PNG, WebP이며 기본 최대 크기는 장당 5MB입니다. 이미지는 모임당 최대 3장입니다.
모임별로 만료되지 않은 미반영 업로드는 기본 3개까지만 발급됩니다. 발급 URL은 한 번 업로드한
key를 덮어쓸 수 없으며, 반영 API는 서버가 발급하고 아직 소비하지 않은 key(또는 이미 반영된
현재 이미지 key)만 허용합니다.

반영은 **새 이미지 세트를 먼저 DB에 커밋**한 뒤, 교체로 빠진 기존 객체를 삭제 대상으로 표시합니다.
DB 반영이 실패했을 때 기존 이미지까지 사라지는 것을 막기 위한 순서입니다. 실제 S3 삭제와,
만료된 미반영 객체 삭제는 백엔드 정리 배치가 주기적으로 수행하고 실패 시 재시도합니다.

`If-None-Match`는 단순 헤더가 아니라 브라우저가 사전 요청(OPTIONS)을 보내므로, 버킷 CORS에
프론트 Origin과 이 헤더가 허용돼 있어야 업로드가 시작됩니다. profile 이미지와 **같은 버킷**을 쓰므로
운영에 이미 적용된 CORS가 prefix 무관하게 그대로 적용됩니다. 참고 설정은 다음과 같습니다.

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": [
      "http://localhost:5173",
      "https://dev.gathernow.kr",
      "https://gathernow.kr"
    ],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

`If-None-Match: *`는 Presigned URL의 필수 서명 헤더라 클라이언트가 빼면 서명 검증에서 거부됩니다.
따라서 버킷 정책으로 조건부 쓰기를 따로 강제할 필요는 없습니다.

## `412 Precondition Failed` 처리

Presigned PUT 요청은 `If-None-Match: *` 헤더를 반드시 포함합니다. 동일한 URL로 두 번째 PUT을
시도하면 S3가 `412 Precondition Failed`로 거절합니다. 프론트는 412를 받으면 기존 URL을 재시도하지
않고, Presigned URL 발급 API를 다시 호출해 새 `uploadUrl`과 `objectKey`로 업로드해야 합니다.

## 운영 환경변수

profile 이미지와 **같은 버킷**을 사용하므로 기존 세 환경변수를 그대로 씁니다. 새로 필요한 값은 없습니다.

```text
GATHER_AWS_REGION
GATHER_AWS_S3_BUCKET
GATHER_AWS_S3_PUBLIC_BASE_URL
```

모임 이미지 prefix는 기본값이 `meetings`라 설정하지 않아도 됩니다. 다른 prefix로 바꾸려면 선택적으로:

```text
GATHER_AWS_S3_MEETING_OBJECT_PREFIX   # 기본값 meetings
```

S3 API 호출 타임아웃(전체 20초 / 시도 10초)과 정리 배치 설정도 profile과 공유합니다. 필요 시:

```text
GATHER_AWS_S3_API_CALL_TIMEOUT_SECONDS
GATHER_AWS_S3_API_CALL_ATTEMPT_TIMEOUT_SECONDS
```

운영 EC2에는 `GatherBackendProfileImageRole`을 그대로 사용합니다(정적 Access Key/Secret Key 미사용).
이 Role의 `GatherProfileImagesS3Policy`에 **`meetings/*`를 `profiles/*`와 동일하게 추가**합니다.
`s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`의 Resource에 `meetings/*`를 넣고, 버킷 자체에 대한
`s3:ListBucket`은 버킷 레벨이라 이미 있으면 그대로 둡니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ObjectRW",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::<bucket>/profiles/*",
        "arn:aws:s3:::<bucket>/meetings/*"
      ]
    },
    {
      "Sid": "ListForHeadObject404",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::<bucket>"
    }
  ]
}
```

AWS Access Key와 Secret Key는 애플리케이션 설정에 넣지 않습니다. 운영 EC2에 연결된 IAM Role과
AWS SDK 기본 자격 증명 체인을 사용합니다. `HeadObject`는 IAM에서 `s3:GetObject`로 평가됩니다.

`s3:ListBucket`은 응답 코드 때문에 필요합니다. 이 권한이 없으면 S3는 존재하지 않는 객체에 대한
HeadObject에 404가 아니라 403을 반환합니다. `S3ObjectStorage`는 404만 객체 없음으로 판정하므로,
사용자가 Presigned URL을 발급받고 업로드에 실패한 뒤 반영 API를 호출하면 404 대신 502가 나갑니다.
`s3:prefix` 조건은 붙이지 않습니다. 이 조건 키는 목록 조회 요청에만 존재해서 HeadObject에서는
조건이 거짓이 되고, 권한을 주지 않은 것과 같아집니다.

버킷 정책으로 `meetings/*`의 `s3:GetObject`를 `profiles/*`와 함께 공개합니다.

```json
{
  "Sid": "PublicReadImages",
  "Effect": "Allow",
  "Principal": "*",
  "Action": "s3:GetObject",
  "Resource": [
    "arn:aws:s3:::<bucket>/profiles/*",
    "arn:aws:s3:::<bucket>/meetings/*"
  ]
}
```

정책을 저장했더라도 퍼블릭 액세스 차단이 켜져 있으면 조회가 막히므로, 버킷과 계정 양쪽 설정을
함께 확인해야 합니다. 배포 스크립트는 S3 환경변수와 EC2 Instance Profile을 JAR 교체 전에 검사합니다.

## 인프라 설정 검증

```bash
# 1. 공개 조회. 실제 객체를 올린 뒤 확인해야 한다.
#    객체가 없으면 정책이 정상이어도 403이 나온다(익명 사용자에게 ListBucket이 없어서
#    S3가 존재 여부를 숨긴다). 200이면 버킷 정책과 퍼블릭 액세스 차단이 모두 정상이다.
aws s3 cp /tmp/test.txt s3://<bucket>/meetings/test.txt
curl -I https://<bucket>.s3.ap-northeast-2.amazonaws.com/meetings/test.txt
aws s3 rm s3://<bucket>/meetings/test.txt

# 2. ListBucket 적용 여부. EC2 안에서 실행해야 인스턴스 역할로 평가된다.
#    403이면 미적용, 404면 정상이다.
aws s3api head-object --bucket <bucket> --key meetings/does-not-exist.jpg
```

기대 결과: 1번 public GET `200`, 2번 없는 객체 HeadObject `404`.
둘 중 하나라도 다르면 위 IAM/버킷 정책의 `meetings/*` 추가 또는 `s3:prefix`/`ListBucket` 상태를
다시 확인합니다.