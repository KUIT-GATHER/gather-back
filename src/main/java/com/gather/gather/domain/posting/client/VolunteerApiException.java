package com.gather.gather.domain.posting.client;

public class VolunteerApiException extends RuntimeException {

    public VolunteerApiException(String message) {
        super(message);
    }

    public VolunteerApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
