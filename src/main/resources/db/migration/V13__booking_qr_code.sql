-- V13__booking_qr_code.sql
-- Mỗi đơn đặt vé có đúng một mã QR duy nhất cho toàn bộ đơn (gồm các ghế và bắp nước)

ALTER TABLE bookings
    ADD COLUMN qr_code VARCHAR(100);

UPDATE bookings
SET qr_code = booking_code
WHERE status = 'PAID' AND qr_code IS NULL;

CREATE UNIQUE INDEX uq_bookings_qr_code ON bookings (qr_code) WHERE qr_code IS NOT NULL;

COMMENT ON COLUMN bookings.qr_code IS 'Nội dung mã QR duy nhất của đơn, bao gồm tất cả ghế và bắp nước; chỉ có khi đơn đã thanh toán';
COMMENT ON COLUMN tickets.qr_code_url IS 'Không dùng: mỗi đơn chỉ có một mã QR, lưu ở bookings.qr_code';
