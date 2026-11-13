ALTER TABLE bookings
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN sold_by_staff_user_id BIGINT REFERENCES users(id),
    ADD CONSTRAINT chk_bookings_sales_channel CHECK (sales_channel IN ('ONLINE', 'COUNTER')),
    ADD CONSTRAINT chk_bookings_counter_staff CHECK (sales_channel <> 'COUNTER' OR sold_by_staff_user_id IS NOT NULL);

CREATE INDEX idx_bookings_sold_by_staff ON bookings (sold_by_staff_user_id, created_at DESC) WHERE sold_by_staff_user_id IS NOT NULL;
