-- V8__fnb_catalog.sql
-- Danh mục đồ ăn & nước: xóa mềm, ràng buộc hợp lệ, chống trùng tên

UPDATE fnb_items SET status = 'AVAILABLE' WHERE status IS NULL;
UPDATE fnb_combos SET status = 'AVAILABLE' WHERE status IS NULL;

ALTER TABLE fnb_items
    ALTER COLUMN status SET NOT NULL,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_fnb_items_category CHECK (category IN ('POPCORN', 'DRINK', 'SNACK')),
    ADD CONSTRAINT chk_fnb_items_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE')),
    ADD CONSTRAINT chk_fnb_items_price CHECK (price >= 0),
    ADD CONSTRAINT chk_fnb_items_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

ALTER TABLE fnb_combos
    ALTER COLUMN status SET NOT NULL,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_fnb_combos_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE')),
    ADD CONSTRAINT chk_fnb_combos_price CHECK (price >= 0),
    ADD CONSTRAINT chk_fnb_combos_deleted_at CHECK (is_deleted = (deleted_at IS NOT NULL));

ALTER TABLE fnb_combo_items
    ADD CONSTRAINT chk_fnb_combo_items_quantity CHECK (quantity BETWEEN 1 AND 20);

CREATE UNIQUE INDEX uq_fnb_items_active_name ON fnb_items (lower(name)) WHERE NOT is_deleted;
CREATE UNIQUE INDEX uq_fnb_combos_active_name ON fnb_combos (lower(name)) WHERE NOT is_deleted;
CREATE INDEX idx_fnb_combo_items_item ON fnb_combo_items (item_id);
