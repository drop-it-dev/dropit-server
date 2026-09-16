package com.dropit.ranking.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
public class SellerSalesRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public List<SellerSales> aggregate(LocalDateTime monthStart, LocalDateTime asOf) {
        return jdbc.query("""
                SELECT p.seller_id,
                       SUM(i.quantity) AS total_quantity,
                       SUM(CASE WHEN o.created_at >= :monthStart THEN i.quantity ELSE 0 END)
                           AS monthly_quantity
                FROM orders o
                JOIN order_items i ON i.order_id = o.id
                JOIN drops d ON d.id = i.drop_id
                JOIN products p ON p.id = d.product_id
                WHERE o.status = 'ORDERED' AND o.created_at < :asOf
                GROUP BY p.seller_id
                ORDER BY p.seller_id
                """, new MapSqlParameterSource()
                .addValue("monthStart", monthStart, Types.TIMESTAMP)
                .addValue("asOf", asOf, Types.TIMESTAMP), (rs, rowNum) -> new SellerSales(
                rs.getLong("seller_id"),
                rs.getBigDecimal("total_quantity").longValueExact(),
                rs.getBigDecimal("monthly_quantity").longValueExact()));
    }
}
