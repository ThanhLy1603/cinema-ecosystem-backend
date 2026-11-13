package com.cinema.modules.payment.service;

import com.cinema.modules.payment.dto.response.CounterMethodTotalResponse;
import com.cinema.modules.payment.dto.response.CounterTransactionResponse;
import com.cinema.modules.payment.dto.response.RevenueChannelResponse;
import com.cinema.modules.payment.dto.response.RevenueStaffResponse;
import com.cinema.modules.payment.dto.response.RevenueSummaryResponse;
import com.cinema.modules.payment.enums.PaymentMethod;
import com.cinema.modules.payment.service.interfaces.IRevenueReportService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RevenueReportService implements IRevenueReportService {

    private static final int MAX_TRANSACTIONS = 300;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public RevenueReportService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Transactional(readOnly = true)
    @Override
    public List<CounterMethodTotalResponse> sumCounterSalesByMethod(Long staffUserId, Instant from, Instant to) {
        MapSqlParameterSource parameters = rangeParameters(from, to).addValue("staffUserId", staffUserId);
        Map<PaymentMethod, long[]> orderCounts = new EnumMap<>(PaymentMethod.class);
        Map<PaymentMethod, BigDecimal[]> amounts = new EnumMap<>(PaymentMethod.class);
        namedParameterJdbcTemplate.query(
                "select p.payment_method as method, count(*) as order_count, coalesce(sum(p.amount), 0) as amount "
                        + "from payments p join bookings b on b.id = p.booking_id "
                        + "where p.status = 'SUCCESS' and b.sales_channel = 'COUNTER' "
                        + "and b.sold_by_staff_user_id = :staffUserId "
                        + "and p.paid_at >= :from and p.paid_at < :to group by p.payment_method",
                parameters,
                resultSet -> {
                    PaymentMethod paymentMethod = PaymentMethod.valueOf(resultSet.getString("method"));
                    orderCounts.computeIfAbsent(paymentMethod, key -> new long[2])[0] = resultSet.getLong("order_count");
                    amounts.computeIfAbsent(paymentMethod, key -> zeroPair())[0] = resultSet.getBigDecimal("amount");
                });
        namedParameterJdbcTemplate.query(
                "select payment_method as method, count(*) as order_count, coalesce(sum(total_amount), 0) as amount "
                        + "from counter_fnb_orders where sold_by_staff_user_id = :staffUserId "
                        + "and created_at >= :from and created_at < :to group by payment_method",
                parameters,
                resultSet -> {
                    PaymentMethod paymentMethod = PaymentMethod.valueOf(resultSet.getString("method"));
                    orderCounts.computeIfAbsent(paymentMethod, key -> new long[2])[1] = resultSet.getLong("order_count");
                    amounts.computeIfAbsent(paymentMethod, key -> zeroPair())[1] = resultSet.getBigDecimal("amount");
                });
        return orderCounts.keySet().stream()
                .map(paymentMethod -> new CounterMethodTotalResponse(
                        paymentMethod,
                        orderCounts.get(paymentMethod)[0],
                        amounts.get(paymentMethod)[0],
                        orderCounts.get(paymentMethod)[1],
                        amounts.get(paymentMethod)[1]))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CounterTransactionResponse> findCounterTransactions(Long staffUserId, Instant from, Instant to) {
        MapSqlParameterSource parameters = rangeParameters(from, to)
                .addValue("staffUserId", staffUserId)
                .addValue("limit", MAX_TRANSACTIONS);
        return namedParameterJdbcTemplate.query(
                "select * from ("
                        + "select 'TICKET' as type, b.booking_code as code, p.payment_method as method, "
                        + "p.amount as amount, p.paid_at as occurred_at "
                        + "from payments p join bookings b on b.id = p.booking_id "
                        + "where p.status = 'SUCCESS' and b.sales_channel = 'COUNTER' "
                        + "and b.sold_by_staff_user_id = :staffUserId and p.paid_at >= :from and p.paid_at < :to "
                        + "union all "
                        + "select 'FNB', order_code, payment_method, total_amount, created_at "
                        + "from counter_fnb_orders where sold_by_staff_user_id = :staffUserId "
                        + "and created_at >= :from and created_at < :to) transactions "
                        + "order by occurred_at desc limit :limit",
                parameters,
                (resultSet, rowNumber) -> new CounterTransactionResponse(
                        resultSet.getString("type"),
                        resultSet.getString("code"),
                        PaymentMethod.valueOf(resultSet.getString("method")),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getTimestamp("occurred_at").toInstant()));
    }

    @Transactional(readOnly = true)
    @Override
    public RevenueSummaryResponse summarize(Collection<Long> cinemaIds, Instant from, Instant to) {
        MapSqlParameterSource parameters = rangeParameters(from, to);
        String paymentCinemaFilter = "";
        String fnbCinemaFilter = "";
        if (cinemaIds != null) {
            if (cinemaIds.isEmpty()) {
                return new RevenueSummaryResponse(List.of(), List.of());
            }
            parameters.addValue("cinemaIds", cinemaIds);
            paymentCinemaFilter = " and b.cinema_id in (:cinemaIds)";
            fnbCinemaFilter = " and cinema_id in (:cinemaIds)";
        }
        String paymentBase = "from payments p join bookings b on b.id = p.booking_id "
                + "where p.status = 'SUCCESS' and p.paid_at >= :from and p.paid_at < :to" + paymentCinemaFilter;
        String channelSql = "select 'ONLINE' as channel, count(*) as order_count, coalesce(sum(p.amount), 0) as amount "
                + paymentBase + " and b.sales_channel = 'ONLINE' "
                + "union all select 'COUNTER_TICKET', count(*), coalesce(sum(p.amount), 0) "
                + paymentBase + " and b.sales_channel = 'COUNTER' "
                + "union all select 'COUNTER_FNB', count(*), coalesce(sum(total_amount), 0) "
                + "from counter_fnb_orders where created_at >= :from and created_at < :to" + fnbCinemaFilter;
        List<RevenueChannelResponse> channels = namedParameterJdbcTemplate.query(
                channelSql,
                parameters,
                (resultSet, rowNumber) -> new RevenueChannelResponse(
                        resultSet.getString("channel"),
                        resultSet.getLong("order_count"),
                        resultSet.getBigDecimal("amount")));
        String staffSql = "select staff_user_id, sum(order_count) as order_count, sum(amount) as amount from ("
                + "select b.sold_by_staff_user_id as staff_user_id, count(*) as order_count, sum(p.amount) as amount "
                + paymentBase + " and b.sales_channel = 'COUNTER' group by b.sold_by_staff_user_id "
                + "union all select sold_by_staff_user_id, count(*), sum(total_amount) "
                + "from counter_fnb_orders where created_at >= :from and created_at < :to" + fnbCinemaFilter
                + " group by sold_by_staff_user_id) staff_sales "
                + "group by staff_user_id order by amount desc";
        List<RevenueStaffResponse> staff = namedParameterJdbcTemplate.query(
                staffSql,
                parameters,
                (resultSet, rowNumber) -> new RevenueStaffResponse(
                        resultSet.getLong("staff_user_id"),
                        resultSet.getLong("order_count"),
                        resultSet.getBigDecimal("amount")));
        return new RevenueSummaryResponse(channels, staff);
    }

    private MapSqlParameterSource rangeParameters(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to));
    }

    private BigDecimal[] zeroPair() {
        return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO};
    }
}
