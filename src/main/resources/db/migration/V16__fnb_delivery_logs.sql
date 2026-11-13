CREATE TABLE fnb_delivery_logs (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    booking_fnb_detail_id BIGINT REFERENCES booking_fnb_details(id) ON DELETE SET NULL,
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id),
    staff_user_id BIGINT NOT NULL REFERENCES users(id),
    booking_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fnb_delivery_logs_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_fnb_delivery_logs_cinema_created ON fnb_delivery_logs (cinema_id, created_at DESC);
CREATE INDEX idx_fnb_delivery_logs_booking ON fnb_delivery_logs (booking_id);
