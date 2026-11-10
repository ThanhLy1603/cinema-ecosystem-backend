-- V10__booking_showtime_link.sql
-- Gắn đơn đặt vé với suất chiếu, thêm hạn giữ ghế, mỗi người dùng chỉ có một đơn PENDING

ALTER TABLE bookings
    ADD COLUMN showtime_id BIGINT REFERENCES showtimes(id),
    ADD COLUMN expires_at TIMESTAMPTZ;

CREATE INDEX idx_bookings_showtime ON bookings(showtime_id);

CREATE UNIQUE INDEX uq_bookings_user_pending ON bookings(user_id) WHERE status = 'PENDING';
