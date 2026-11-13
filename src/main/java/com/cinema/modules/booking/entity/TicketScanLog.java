package com.cinema.modules.booking.entity;

import com.cinema.modules.booking.enums.CheckInResultCode;
import com.cinema.modules.booking.enums.TicketScanAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ticket_scan_logs")
public class TicketScanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "cinema_id")
    private Long cinemaId;

    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;

    @Column(name = "scanned_code", nullable = false, length = 100)
    private String scannedCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private TicketScanAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_code", nullable = false, length = 30)
    private CheckInResultCode resultCode;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
