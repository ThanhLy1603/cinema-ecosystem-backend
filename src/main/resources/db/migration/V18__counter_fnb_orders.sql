CREATE TABLE counter_fnb_orders (
    id BIGSERIAL PRIMARY KEY,
    order_code VARCHAR(30) NOT NULL UNIQUE,
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id),
    sold_by_staff_user_id BIGINT NOT NULL REFERENCES users(id),
    total_amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    cash_received NUMERIC(12, 2),
    change_amount NUMERIC(12, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_counter_fnb_orders_total CHECK (total_amount > 0),
    CONSTRAINT chk_counter_fnb_orders_payment_method CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER')),
    CONSTRAINT chk_counter_fnb_orders_cash CHECK (
        (payment_method = 'CASH' AND cash_received IS NOT NULL AND change_amount IS NOT NULL AND cash_received >= total_amount)
        OR (payment_method <> 'CASH' AND cash_received IS NULL AND change_amount IS NULL)
    )
);

CREATE TABLE counter_fnb_order_lines (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES counter_fnb_orders(id) ON DELETE CASCADE,
    combo_id BIGINT REFERENCES fnb_combos(id),
    item_id BIGINT REFERENCES fnb_items(id),
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    CONSTRAINT chk_counter_fnb_order_lines_choice CHECK ((combo_id IS NULL) <> (item_id IS NULL)),
    CONSTRAINT chk_counter_fnb_order_lines_quantity CHECK (quantity BETWEEN 1 AND 10)
);

CREATE INDEX idx_counter_fnb_orders_cinema ON counter_fnb_orders (cinema_id, created_at DESC);
CREATE INDEX idx_counter_fnb_orders_staff ON counter_fnb_orders (sold_by_staff_user_id, created_at DESC);
CREATE INDEX idx_counter_fnb_order_lines_order ON counter_fnb_order_lines (order_id);
