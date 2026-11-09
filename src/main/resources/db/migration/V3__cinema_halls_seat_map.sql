-- V3__cinema_halls_seat_map.sql
-- Phân hệ CIN: cụm rạp, tiện ích, phòng chiếu và ma trận ghế

-- 1. cinema_facilities ------------------------------------------------------
ALTER TABLE cinema_facilities
    ADD COLUMN description TEXT;

CREATE UNIQUE INDEX uq_cinema_facilities_name ON cinema_facilities (lower(name));

-- 2. cinemas -----------------------------------------------------------------
UPDATE cinemas SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE cinemas
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT chk_cinemas_status CHECK (status IN ('ACTIVE','INACTIVE','DELETED')),
    ADD CONSTRAINT chk_cinemas_coordinates CHECK (
        (latitude IS NULL) = (longitude IS NULL)
        AND (latitude IS NULL OR latitude BETWEEN -90 AND 90)
        AND (longitude IS NULL OR longitude BETWEEN -180 AND 180));

CREATE INDEX idx_cinemas_city ON cinemas(city);

-- 3. seat_types: số ô cột mà một ghế chiếm trên lưới --------------------------
UPDATE seat_types SET default_surcharge = 0.00 WHERE default_surcharge IS NULL;

ALTER TABLE seat_types
    ALTER COLUMN default_surcharge SET NOT NULL,
    ADD COLUMN seat_span SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_seat_types_surcharge CHECK (default_surcharge >= 0),
    ADD CONSTRAINT chk_seat_types_span CHECK (seat_span BETWEEN 1 AND 2);

UPDATE seat_types SET seat_span = 2 WHERE code = 'SWEETBOX';

-- 4. halls -------------------------------------------------------------------
UPDATE halls SET screen_type = '2D' WHERE screen_type IS NULL;
UPDATE halls SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE halls
    ALTER COLUMN screen_type SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN seat_map_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_halls_screen_type CHECK (screen_type IN ('2D','3D','IMAX','4DX')),
    ADD CONSTRAINT chk_halls_status CHECK (status IN ('ACTIVE','MAINTENANCE')),
    ADD CONSTRAINT chk_halls_total_seats CHECK (total_seats >= 0);

CREATE UNIQUE INDEX uq_halls_cinema_name ON halls (cinema_id, lower(name));
CREATE INDEX idx_halls_cinema ON halls(cinema_id);

-- 5. seats ---------------------------------------------------------------------
UPDATE seats SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE seats
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT chk_seats_status CHECK (status IN ('ACTIVE','MAINTENANCE')),
    ADD CONSTRAINT chk_seats_row CHECK (seat_row ~ '^[A-Z]$'),
    ADD CONSTRAINT chk_seats_number CHECK (seat_number BETWEEN 1 AND 40);
