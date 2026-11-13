package com.cinema.modules.auth.scope;

import java.util.Set;

public record CinemaScope(boolean allCinemas, Set<Long> cinemaIds) {

    public boolean isEmpty() {
        return !allCinemas && cinemaIds.isEmpty();
    }

    public boolean contains(Long cinemaId) {
        return allCinemas || cinemaIds.contains(cinemaId);
    }
}
