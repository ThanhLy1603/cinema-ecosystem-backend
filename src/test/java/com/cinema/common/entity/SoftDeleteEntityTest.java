package com.cinema.common.entity;

import com.cinema.common.entity.interfaces.ISoftDelete;
import com.cinema.modules.cinema.entity.Hall;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDeleteEntityTest {

    private static final Instant DELETED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void newEntityIsNotDeleted() {
        ISoftDelete softDelete = new Hall();

        assertFalse(softDelete.isDeleted());
        assertNull(softDelete.getDeletedAt());
    }

    @Test
    void markDeletedSetsFlagAndTimestamp() {
        ISoftDelete softDelete = new Hall();

        softDelete.markDeleted(DELETED_AT);

        assertTrue(softDelete.isDeleted());
        assertEquals(DELETED_AT, softDelete.getDeletedAt());
    }

    @Test
    void markingTwiceIsRejected() {
        ISoftDelete softDelete = new Hall();
        softDelete.markDeleted(DELETED_AT);

        assertThrows(IllegalStateException.class, () -> softDelete.markDeleted(DELETED_AT.plusSeconds(1)));
        assertEquals(DELETED_AT, softDelete.getDeletedAt());
    }

    @Test
    void restoreClearsFlagAndTimestamp() {
        ISoftDelete softDelete = new Hall();
        softDelete.markDeleted(DELETED_AT);

        softDelete.restore();

        assertFalse(softDelete.isDeleted());
        assertNull(softDelete.getDeletedAt());
    }

    @Test
    void restoringAnActiveEntityIsRejected() {
        ISoftDelete softDelete = new Hall();

        assertThrows(IllegalStateException.class, softDelete::restore);
    }
}
