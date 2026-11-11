-- V12__payment_tickets_settlement.sql
-- Giá ghế chốt lúc giữ chỗ, chống trùng vé theo (suất, ghế), ràng buộc thanh toán, chống cộng điểm hai lần

ALTER TABLE seat_reservations
    ADD COLUMN price NUMERIC(12, 2);

ALTER TABLE tickets
    ADD CONSTRAINT uq_tickets_showtime_seat UNIQUE (showtime_id, seat_id);

UPDATE payments SET status = 'PENDING' WHERE status IS NULL;

ALTER TABLE payments
    ALTER COLUMN status SET NOT NULL,
    ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    ADD CONSTRAINT chk_payments_amount CHECK (amount > 0);

ALTER TABLE payments ALTER COLUMN created_at SET NOT NULL;

CREATE UNIQUE INDEX uq_loyalty_earn_booking
    ON loyalty_point_transactions (booking_id)
    WHERE transaction_type = 'EARN';
