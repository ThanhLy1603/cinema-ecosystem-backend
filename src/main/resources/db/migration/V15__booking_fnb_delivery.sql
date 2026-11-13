ALTER TABLE booking_fnb_details
    ADD COLUMN delivered_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN delivered_by_user_id BIGINT REFERENCES users(id),
    ADD CONSTRAINT chk_booking_fnb_delivered_quantity CHECK (delivered_quantity >= 0 AND delivered_quantity <= quantity);

CREATE INDEX idx_booking_fnb_details_undelivered ON booking_fnb_details (booking_id) WHERE delivered_quantity < quantity;
