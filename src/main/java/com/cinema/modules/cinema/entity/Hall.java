package com.cinema.modules.cinema.entity;

import com.cinema.modules.cinema.converter.ScreenTypeConverter;
import com.cinema.modules.cinema.enums.HallStatus;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.common.entity.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "halls")
public class Hall extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cinema_id", nullable = false)
    private Long cinemaId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Convert(converter = ScreenTypeConverter.class)
    @Column(name = "screen_type", nullable = false, length = 50)
    private ScreenType screenType = ScreenType.SCREEN_2D;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private HallStatus status = HallStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "seat_map_updated_at")
    private Instant seatMapUpdatedAt;
}
