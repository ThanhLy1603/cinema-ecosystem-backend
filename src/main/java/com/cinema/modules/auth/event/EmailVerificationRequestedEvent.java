package com.cinema.modules.auth.event;

public record EmailVerificationRequestedEvent(String email, String fullName, String rawToken) {

    @Override
    public String toString() {
        return "EmailVerificationRequestedEvent[email=" + email + ", rawToken=***]";
    }
}
