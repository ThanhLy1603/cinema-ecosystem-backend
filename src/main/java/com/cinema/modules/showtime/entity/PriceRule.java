package com.cinema.modules.showtime.entity;

import com.cinema.common.entity.SoftDeleteEntity;
import com.cinema.modules.cinema.converter.ScreenTypeConverter;
import com.cinema.modules.cinema.enums.ScreenType;
import com.cinema.modules.showtime.enums.PriceDayType;
import com.cinema.modules.showtime.enums.PriceRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "price_rules")
public class PriceRule extends SoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 150)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 50)
    private PriceDayType dayType = PriceDayType.ALL;

    @Column(name = "time_from")
    private LocalTime timeFrom;

    @Column(name = "time_to")
    private LocalTime timeTo;

    @Column(name = "surcharge_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal surchargeAmount = BigDecimal.ZERO;

    @Convert(converter = ScreenTypeConverter.class)
    @Column(name = "screen_type", length = 50)
    private ScreenType screenType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public PriceRuleType resolveRuleType() {
        if (screenType != null) {
            return PriceRuleType.SCREEN_TYPE;
        }
        if (timeFrom != null) {
            return PriceRuleType.TIME_SLOT;
        }
        return PriceRuleType.DAY_TYPE;
    }
}
