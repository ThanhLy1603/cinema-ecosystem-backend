package com.cinema.common.entity.interfaces;

import java.time.Instant;

public interface ISoftDelete {

    boolean isDeleted();

    Instant getDeletedAt();

    void markDeleted(Instant deletedAt);

    void restore();
}
