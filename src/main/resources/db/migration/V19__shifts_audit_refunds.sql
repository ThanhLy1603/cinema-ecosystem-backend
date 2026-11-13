CREATE TABLE staff_shifts (
    id BIGSERIAL PRIMARY KEY,
    staff_user_id BIGINT NOT NULL REFERENCES users(id),
    cinema_id BIGINT NOT NULL REFERENCES cinemas(id),
    status VARCHAR(10) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    opening_cash NUMERIC(12, 2) NOT NULL,
    expected_cash NUMERIC(12, 2),
    counted_cash NUMERIC(12, 2),
    cash_difference NUMERIC(12, 2),
    note VARCHAR(500),
    CONSTRAINT chk_staff_shifts_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_staff_shifts_opening_cash CHECK (opening_cash >= 0),
    CONSTRAINT chk_staff_shifts_closed CHECK (
        (status = 'OPEN' AND closed_at IS NULL AND counted_cash IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND counted_cash IS NOT NULL AND expected_cash IS NOT NULL AND cash_difference IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_staff_shifts_open_staff ON staff_shifts (staff_user_id) WHERE status = 'OPEN';
CREATE INDEX idx_staff_shifts_cinema_opened ON staff_shifts (cinema_id, opened_at DESC);
CREATE INDEX idx_staff_shifts_staff_opened ON staff_shifts (staff_user_id, opened_at DESC);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id),
    cinema_id BIGINT REFERENCES cinemas(id),
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(30),
    target_code VARCHAR(100),
    detail VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_action_created ON audit_logs (action, created_at DESC);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_cinema_created ON audit_logs (cinema_id, created_at DESC);

CREATE TABLE booking_refunds (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    amount NUMERIC(12, 2) NOT NULL,
    payment_method VARCHAR(50),
    reason VARCHAR(500) NOT NULL,
    refunded_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_refunds_amount CHECK (amount > 0)
);

ALTER TABLE payments DROP CONSTRAINT chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'));
