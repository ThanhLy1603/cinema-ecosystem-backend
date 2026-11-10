package com.cinema.modules.media.enums;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public enum MediaPurpose {
    MOVIE_POSTER("image", "cinema/movies/posters", List.of("jpg", "jpeg", "png", "webp"), 5L * 1024 * 1024,
            Set.of("ROLE_ADMIN", "ROLE_MANAGER")),
    MOVIE_BANNER("image", "cinema/movies/banners", List.of("jpg", "jpeg", "png", "webp"), 8L * 1024 * 1024,
            Set.of("ROLE_ADMIN", "ROLE_MANAGER")),
    MOVIE_TRAILER("video", "cinema/movies/trailers", List.of("mp4", "webm", "mov"), 100L * 1024 * 1024,
            Set.of("ROLE_ADMIN", "ROLE_MANAGER")),
    PERSON_AVATAR("image", "cinema/persons", List.of("jpg", "jpeg", "png", "webp"), 3L * 1024 * 1024,
            Set.of("ROLE_ADMIN", "ROLE_MANAGER")),
    FNB_IMAGE("image", "cinema/fnb", List.of("jpg", "jpeg", "png", "webp"), 3L * 1024 * 1024,
            Set.of("ROLE_ADMIN", "ROLE_MANAGER")),
    FACILITY_ICON("image", "cinema/facilities", List.of("jpg", "jpeg", "png", "webp", "svg"), 2L * 1024 * 1024,
            Set.of("ROLE_ADMIN")),
    USER_AVATAR("image", "cinema/users", List.of("jpg", "jpeg", "png", "webp"), 3L * 1024 * 1024, Set.of());

    private final String resourceType;
    private final String folder;
    private final List<String> allowedFormats;
    private final long maxFileSizeBytes;
    private final Set<String> allowedRoles;

    MediaPurpose(
            String resourceType,
            String folder,
            List<String> allowedFormats,
            long maxFileSizeBytes,
            Set<String> allowedRoles) {
        this.resourceType = resourceType;
        this.folder = folder;
        this.allowedFormats = allowedFormats;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedRoles = allowedRoles;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getFolder() {
        return folder;
    }

    public List<String> getAllowedFormats() {
        return allowedFormats;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public boolean isAllowedFor(Collection<String> roles) {
        return allowedRoles.isEmpty() || roles.stream().anyMatch(allowedRoles::contains);
    }
}
