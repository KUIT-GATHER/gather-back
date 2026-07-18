CREATE TABLE posting_search_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    keyword     VARCHAR(100) NOT NULL,
    searched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_posting_search_log_searched_at ON posting_search_log (searched_at);

CREATE TABLE posting_recommended_keyword (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    keyword    VARCHAR(50) NOT NULL,
    score      INT         NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_posting_recommended_keyword_keyword UNIQUE (keyword)
);
