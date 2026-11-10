-- V6__movies_showtimes_pricing.sql
-- Phân hệ MOV/SCH: phim, suất chiếu chống trùng phòng (kèm 15 phút dọn phòng) và quy tắc giá động

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 1. movies ---------------------------------------------------------------------
UPDATE movies SET status = 'NOW_SHOWING' WHERE status IS NULL;

ALTER TABLE movies
    ALTER COLUMN status SET NOT NULL,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_movies_rated CHECK (rated IN ('P','C13','C16','C18')),
    ADD CONSTRAINT chk_movies_status CHECK (status IN ('COMING_SOON','NOW_SHOWING','ENDED')),
    ADD CONSTRAINT chk_movies_duration CHECK (duration_minutes BETWEEN 1 AND 600),
    ADD CONSTRAINT chk_movies_dates CHECK (end_date IS NULL OR end_date >= release_date),
    ADD CONSTRAINT chk_movies_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

CREATE INDEX idx_movies_status_release ON movies (status, release_date DESC) WHERE NOT is_deleted;

-- 2. movie_genres / movie_casts ------------------------------------------------
CREATE INDEX idx_movie_genres_genre ON movie_genres (genre_id);

ALTER TABLE movie_casts
    ADD CONSTRAINT chk_movie_casts_role_type CHECK (role_type IN ('DIRECTOR','ACTOR','PRODUCER'));

CREATE UNIQUE INDEX uq_movie_casts_movie_person_role ON movie_casts (movie_id, person_id, role_type);
CREATE INDEX idx_movie_casts_person ON movie_casts (person_id);

-- 3. showtimes: end_time là giờ hết phim thực tế; 15 phút dọn phòng nằm trong khoảng chiếm phòng ---
CREATE FUNCTION showtime_occupied_range(showtime_start TIMESTAMPTZ, showtime_end TIMESTAMPTZ)
    RETURNS tstzrange
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
    SELECT tstzrange(showtime_start, showtime_end + INTERVAL '15 minutes', '[)')
$$;

UPDATE showtimes SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE showtimes
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT chk_showtimes_status CHECK (status IN ('ACTIVE','CANCELLED')),
    ADD CONSTRAINT chk_showtimes_time_order CHECK (end_time > start_time),
    ADD CONSTRAINT chk_showtimes_base_price CHECK (base_price >= 0),
    ADD CONSTRAINT ex_showtimes_hall_no_overlap EXCLUDE USING gist (
        hall_id WITH =,
        showtime_occupied_range(start_time, end_time) WITH &&
    ) WHERE (status = 'ACTIVE');

CREATE INDEX idx_showtimes_start_active ON showtimes (start_time) WHERE status = 'ACTIVE';

-- 4. price_rules: thêm loại màn hình, hiệu lực theo ngày (ngày lễ) và xóa mềm -----
UPDATE price_rules SET surcharge_amount = 0.00 WHERE surcharge_amount IS NULL;

ALTER TABLE price_rules
    ALTER COLUMN surcharge_amount SET NOT NULL,
    ADD COLUMN screen_type VARCHAR(50),
    ADD COLUMN valid_from DATE,
    ADD COLUMN valid_to DATE,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_price_rules_day_type CHECK (day_type IN ('ALL','WEEKDAY','WEEKEND','HOLIDAY')),
    ADD CONSTRAINT chk_price_rules_screen_type CHECK (screen_type IS NULL OR screen_type IN ('2D','3D','IMAX','4DX')),
    ADD CONSTRAINT chk_price_rules_surcharge CHECK (surcharge_amount >= 0),
    ADD CONSTRAINT chk_price_rules_time_window CHECK (
        (time_from IS NULL) = (time_to IS NULL) AND (time_from IS NULL OR time_from <> time_to)),
    ADD CONSTRAINT chk_price_rules_validity CHECK (valid_from IS NULL OR valid_to IS NULL OR valid_to >= valid_from),
    ADD CONSTRAINT chk_price_rules_holiday_dates CHECK (
        day_type <> 'HOLIDAY' OR (valid_from IS NOT NULL AND valid_to IS NOT NULL)),
    ADD CONSTRAINT chk_price_rules_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

CREATE UNIQUE INDEX uq_price_rules_name_active ON price_rules (lower(rule_name)) WHERE NOT is_deleted;
CREATE INDEX idx_price_rules_match_active ON price_rules (day_type, screen_type) WHERE NOT is_deleted;
CREATE INDEX idx_showtime_price_modifiers_rule ON showtime_price_modifiers (price_rule_id);

-- 5. Dữ liệu mặc định theo đặc tả: VIP +20.000, Sweetbox +40.000, cuối tuần +15.000 ------
UPDATE seat_types SET default_surcharge = 20000.00 WHERE code = 'VIP' AND default_surcharge = 15000.00;
UPDATE seat_types SET default_surcharge = 40000.00 WHERE code = 'SWEETBOX' AND default_surcharge = 45000.00;

INSERT INTO price_rules (rule_name, day_type, surcharge_amount)
VALUES ('Phụ thu cuối tuần', 'WEEKEND', 15000.00);
