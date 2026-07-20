# 프로필 이미지 업로드 연동

## 프론트엔드 처리 순서

1. `POST /api/v1/users/me/profile-image/presigned-url`에 이미지의 `contentType`과 바이트 크기
   `fileSize`를 보냅니다.
2. 응답의 `uploadUrl`에 이미지 파일을 `PUT`합니다. 이때 URL 발급 요청과 동일한
   `Content-Type`, 정확한 파일 크기 및 `If-None-Match: *` 헤더를 사용해야 합니다.
   같은 URL로 두 번째 PUT을 시도하면 S3가 `412 Precondition Failed`로 거부합니다.
3. 업로드 성공 후 응답의 `objectKey`를
   `PATCH /api/v1/users/me/profile-image`에 보내 프로필 이미지로 반영합니다.
4. 반영 응답의 `profileImageUrl`은 공개 조회 URL이므로 이미지 `src`로 사용할 수 있습니다.

허용 형식은 JPEG, PNG, WebP이며 기본 최대 크기는 5MB입니다.
사용자별로 만료되지 않은 미반영 업로드는 기본 3개까지만 발급됩니다. 발급 URL은 한 번 업로드한
key를 덮어쓸 수 없으며, 프로필 반영 API는 서버가 발급하고 아직 소비하지 않은 key만 허용합니다.
만료된 미반영 객체와 삭제 실패한 이전 객체는 백엔드 정리 작업이 주기적으로 재시도합니다.
기존 객체는 DB 반영 직후 한 번 삭제하고, 과거 Presigned URL로 삭제된 key가 재생성되는
경우까지 제거하도록 URL 최대 유효기간이 지난 뒤 같은 key를 다시 삭제합니다.

`If-None-Match`는 단순 헤더가 아니라 브라우저가 사전 요청(OPTIONS)을 보내므로, 버킷 CORS에
프론트 Origin과 이 헤더가 허용돼 있어야 업로드가 시작됩니다. 운영에 적용한 설정은 다음과 같습니다.

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

## 운영 환경변수

```text
GATHER_AWS_REGION
GATHER_AWS_S3_BUCKET
GATHER_AWS_S3_PUBLIC_BASE_URL
```

운영 EC2에는 `GatherBackendProfileImageRole`을 연결합니다.
이 Role의 `GatherProfileImagesS3Policy`는 `profiles/*`에 대한
`s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`와, 버킷 자체에 대한 `s3:ListBucket`을
허용합니다. `AmazonS3FullAccess`와 정적 Access Key/Secret Key는 사용하지 않습니다.

AWS Access Key와 Secret Key는 애플리케이션 설정에 넣지 않습니다. 운영 EC2에 연결된 IAM Role과
AWS SDK 기본 자격 증명 체인을 사용합니다. `HeadObject`는 IAM에서 `s3:GetObject`로 평가됩니다.

`s3:ListBucket`은 응답 코드 때문에 필요합니다. 이 권한이 없으면 S3는 존재하지 않는 객체에 대한
HeadObject에 404가 아니라 403을 반환합니다. `S3ObjectStorage`는 404만 객체 없음으로 판정하므로,
사용자가 Presigned URL을 발급받고 업로드에 실패한 뒤 반영 API를 호출하면 404 대신 502가 나갑니다.
`s3:prefix` 조건은 붙이지 않습니다. 이 조건 키는 목록 조회 요청에만 존재해서 HeadObject에서는
조건이 거짓이 되고, 권한을 주지 않은 것과 같아집니다.

배포 전에 `/etc/gather/gather.env`에 위 세 환경변수를 먼저 추가하고 EC2 Instance Profile을
연결해야 합니다. 배포 스크립트는 두 조건을 JAR 교체 전에 검사합니다.

버킷 정책으로 `profiles/*`의 `s3:GetObject`만 공개합니다. 정책을 저장했더라도 퍼블릭 액세스 차단이
켜져 있으면 조회가 막히므로, 버킷과 계정 양쪽 설정을 함께 확인해야 합니다.

## 인프라 설정 검증

```bash
# 1. 공개 조회. 실제 객체를 올린 뒤 확인해야 한다.
#    객체가 없으면 정책이 정상이어도 403이 나온다(익명 사용자에게 ListBucket이 없어서
#    S3가 존재 여부를 숨긴다). 200이면 버킷 정책과 퍼블릭 액세스 차단이 모두 정상이다.
aws s3 cp /tmp/test.txt s3://<bucket>/profiles/test.txt
curl -I https://<bucket>.s3.ap-northeast-2.amazonaws.com/profiles/test.txt
aws s3 rm s3://<bucket>/profiles/test.txt

# 2. ListBucket 적용 여부. EC2 안에서 실행해야 인스턴스 역할로 평가된다.
#    403이면 미적용, 404면 정상이다.
aws s3api head-object --bucket <bucket> --key profiles/does-not-exist.jpg
```
