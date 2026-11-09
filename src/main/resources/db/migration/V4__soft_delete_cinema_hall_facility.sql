-- V4__soft_delete_cinema_hall_facility.sql
-- Xóa mềm bằng cờ is_deleted cho cụm rạp, phòng chiếu và tiện ích

-- 1. cinemas -------------------------------------------------------------------
ALTER TABLE cinemas
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ;

UPDATE cinemas
SET is_deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, status = 'INACTIVE'
WHERE status = 'DELETED';

ALTER TABLE cinemas
    DROP CONSTRAINT chk_cinemas_status,
    ADD CONSTRAINT chk_cinemas_status CHECK (status IN ('ACTIVE','INACTIVE')),
    ADD CONSTRAINT chk_cinemas_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL)),
    DROP CONSTRAINT cinemas_slug_key;

CREATE UNIQUE INDEX uq_cinemas_slug_active ON cinemas (slug) WHERE NOT is_deleted;
CREATE INDEX idx_cinemas_is_deleted ON cinemas (is_deleted);

-- 2. halls ---------------------------------------------------------------------
ALTER TABLE halls
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_halls_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

DROP INDEX uq_halls_cinema_name;
CREATE UNIQUE INDEX uq_halls_cinema_name_active ON halls (cinema_id, lower(name)) WHERE NOT is_deleted;
CREATE INDEX idx_halls_is_deleted ON halls (is_deleted);

-- 3. cinema_facilities ---------------------------------------------------------
ALTER TABLE cinema_facilities
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_cinema_facilities_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

DROP INDEX uq_cinema_facilities_name;
CREATE UNIQUE INDEX uq_cinema_facilities_name_active ON cinema_facilities (lower(name)) WHERE NOT is_deleted;
CREATE INDEX idx_cinema_facilities_is_deleted ON cinema_facilities (is_deleted);
