package com.gather.gather.domain.posting.crawler;

/** VMS 크롤링(HTTP fetch/HTML 파싱) 실패를 나타낸다. REST 에러 체계와 분리해 배치 실패로만 다룬다. */
public class VmsCrawlException extends RuntimeException {

    public VmsCrawlException(String message, Throwable cause) {
        super(message, cause);
    }

    public VmsCrawlException(String message) {
        super(message);
    }
}
