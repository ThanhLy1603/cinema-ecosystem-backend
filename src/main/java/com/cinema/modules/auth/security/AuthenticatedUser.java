package com.cinema.modules.auth.security;

import java.util.List;

public record AuthenticatedUser(Long userId, String email, List<String> roles) {
}
