package com.cinema.modules.movie.enums;

public enum AgeRating {
    P(0),
    C13(13),
    C16(16),
    C18(18);

    private final int minimumAge;

    AgeRating(int minimumAge) {
        this.minimumAge = minimumAge;
    }

    public int getMinimumAge() {
        return minimumAge;
    }
}
