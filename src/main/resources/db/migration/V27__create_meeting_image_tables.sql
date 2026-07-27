-- 모임 이미지(최대 3장). URL 아닌 objectKey(meetings/{meetingId}/{uuid}.{ext})만 저장.
CREATE TABLE meeting_image (
                               id         BIGINT       NOT NULL AUTO_INCREMENT,
                               meeting_id BIGINT       NOT NULL,
                               object_key VARCHAR(255) NOT NULL,
                               sort_order INT          NOT NULL,
                               created_at DATETIME(6)  NOT NULL,
                               PRIMARY KEY (id),
                               CONSTRAINT uk_meeting_image_object_key UNIQUE (object_key),
                               CONSTRAINT uk_meeting_image_meeting_sort UNIQUE (meeting_id, sort_order),
                               CONSTRAINT fk_meeting_image_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id)
);

-- 서버가 발급한 key만 반영을 허용하기 위한 업로드 추적.
-- status: PENDING(발급) / APPLIED(반영, 현재 사용중) / SUPERSEDED(교체되어 삭제 대기).
-- object_deleted=false 인 SUPERSEDED 행을 정리 배치가 S3에서 삭제한다.
CREATE TABLE meeting_image_upload (
                                      id              BIGINT       NOT NULL AUTO_INCREMENT,
                                      meeting_id      BIGINT       NOT NULL,
                                      issuer_user_id  BIGINT       NOT NULL,
                                      object_key      VARCHAR(255) NOT NULL,
                                      content_type    VARCHAR(50)  NOT NULL,
                                      expected_size   BIGINT       NOT NULL,
                                      status          VARCHAR(20)  NOT NULL,
                                      object_deleted  BIT(1)       NOT NULL DEFAULT b'1',
                                      expires_at      DATETIME(6)  NOT NULL,
                                      created_at      DATETIME(6)  NOT NULL,
                                      applied_at      DATETIME(6)  NULL,
                                      PRIMARY KEY (id),
                                      CONSTRAINT uk_meeting_image_upload_object_key UNIQUE (object_key),
                                      CONSTRAINT fk_meeting_image_upload_meeting FOREIGN KEY (meeting_id) REFERENCES meeting (id),
                                      INDEX idx_meeting_image_upload_pending (meeting_id, status, expires_at),
                                      INDEX idx_meeting_image_upload_cleanup (status, object_deleted)
);