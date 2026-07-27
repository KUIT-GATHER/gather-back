CREATE TABLE meeting_search_log (
                                    id          BIGINT       NOT NULL AUTO_INCREMENT,
                                    keyword     VARCHAR(100) NOT NULL,
                                    searched_at DATETIME(6)  NOT NULL,
                                    PRIMARY KEY (id)
);

CREATE INDEX idx_meeting_search_log_searched_at ON meeting_search_log (searched_at);

CREATE TABLE meeting_recommended_keyword (
                                             id         BIGINT      NOT NULL AUTO_INCREMENT,
                                             keyword    VARCHAR(50) NOT NULL,
                                             score      INT         NOT NULL,
                                             updated_at DATETIME(6) NOT NULL,
                                             PRIMARY KEY (id),
                                             CONSTRAINT uk_meeting_recommended_keyword_keyword UNIQUE (keyword)
);