package com.cinema.common.entity;

import com.cinema.common.entity.interfaces.ISoftDelete;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;

@Getter
@MappedSuperclass
public abstract class SoftDeleteEntity implements ISoftDelete {

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Override
    public void markDeleted(Instant deletedAt) {
        if (deleted) {
            throw new IllegalStateException("Đối tượng đã bị xóa");
        }
        this.deleted = true;
        this.deletedAt = deletedAt;
    }

    @Override
    public void restore() {
        if (!deleted) {
            throw new IllegalStateException("Đối tượng chưa bị xóa");
        }
        this.deleted = false;
        this.deletedAt = null;
    }
}
