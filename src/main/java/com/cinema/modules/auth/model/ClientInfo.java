package com.cinema.modules.auth.service;

public record ClientInfo(String deviceInfo, String ipAddress) {

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
