package com.cinema.modules.auth.event;

public record PasswordResetRequestedEvent(String email, String fullName, String rawToken) {

    @Override
    public String toString() {
        return "PasswordResetRequestedEvent[email=" + email + ", rawToken=***]";
    }
}
