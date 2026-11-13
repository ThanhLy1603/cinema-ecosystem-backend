CREATE TABLE ticket_scan_logs (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT REFERENCES bookings(id) ON DELETE SET NULL,
    cinema_id BIGINT REFERENCES cinemas(id),
    staff_user_id BIGINT NOT NULL REFERENCES users(id),
    scanned_code VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    result_code VARCHAR(30) NOT NULL,
    ticket_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ticket_scan_logs_action CHECK (action IN ('LOOKUP', 'CHECK_IN', 'UNDO'))
);
CREATE INDEX idx_ticket_scan_logs_cinema_created ON ticket_scan_logs (cinema_id, created_at DESC);
CREATE INDEX idx_ticket_scan_logs_booking ON ticket_scan_logs (booking_id);
CREATE INDEX idx_tickets_booking_checked ON tickets (booking_id, checked_in);
