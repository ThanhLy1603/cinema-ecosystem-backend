package com.cinema.modules.auth.model;

public record ClientInfo(String deviceInfo, String ipAddress) {

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
