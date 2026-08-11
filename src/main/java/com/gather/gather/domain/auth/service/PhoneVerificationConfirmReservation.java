package com.gather.gather.domain.auth.service;

public record PhoneVerificationConfirmReservation(
        boolean alreadyVerified, String phoneNumber, String verificationCode) {

    public static PhoneVerificationConfirmReservation verified() {
        return new PhoneVerificationConfirmReservation(true, null, null);
    }

    public static PhoneVerificationConfirmReservation reserved(
            String phoneNumber, String verificationCode) {
        return new PhoneVerificationConfirmReservation(false, phoneNumber, verificationCode);
    }
}
