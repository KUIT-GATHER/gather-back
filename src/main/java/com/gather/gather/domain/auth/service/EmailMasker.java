package com.gather.gather.domain.auth.service;

final class EmailMasker {

    private EmailMasker() {}

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
