-- V5__soft_delete_seats.sql
-- Xóa mềm ghế: giữ lịch sử vé và lượt giữ chỗ, vẫn cấm trùng vị trí giữa các ghế còn hiệu lực

ALTER TABLE seats
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_seats_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL)),
    DROP CONSTRAINT uq_hall_seat;

CREATE UNIQUE INDEX uq_hall_seat_active ON seats (hall_id, seat_row, seat_number) WHERE NOT is_deleted;
CREATE INDEX idx_seats_hall_active ON seats (hall_id) WHERE NOT is_deleted;
