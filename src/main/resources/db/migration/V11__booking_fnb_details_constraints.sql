-- V11__booking_fnb_details_constraints.sql
-- Ràng buộc cho bắp nước trong đơn đặt vé: mỗi dòng đúng một combo hoặc một món, số lượng 1-10, không trùng dòng

ALTER TABLE booking_fnb_details
    ADD CONSTRAINT chk_booking_fnb_quantity CHECK (quantity BETWEEN 1 AND 10),
    ADD CONSTRAINT chk_booking_fnb_unit_price CHECK (unit_price >= 0),
    ADD CONSTRAINT chk_booking_fnb_single_product CHECK ((combo_id IS NULL) <> (item_id IS NULL));

CREATE INDEX idx_booking_fnb_details_booking ON booking_fnb_details(booking_id);
CREATE UNIQUE INDEX uq_booking_fnb_combo ON booking_fnb_details(booking_id, combo_id) WHERE combo_id IS NOT NULL;
CREATE UNIQUE INDEX uq_booking_fnb_item ON booking_fnb_details(booking_id, item_id) WHERE item_id IS NOT NULL;
