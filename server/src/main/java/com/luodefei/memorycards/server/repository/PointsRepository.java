package com.luodefei.memorycards.server.repository;

import com.luodefei.memorycards.server.dto.MembershipDtos.PointsTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PointsRepository {

    private static final RowMapper<PointsTransaction> ROW_MAPPER = (rs, rowNum) -> new PointsTransaction(
            rs.getLong("id"),
            rs.getInt("amount"),
            rs.getString("type"),
            rs.getString("remark"),
            rs.getLong("created_at")
    );

    private final JdbcTemplate jdbc;

    public PointsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, int amount, String type, String remark) {
        jdbc.update("INSERT INTO points_transactions (user_id, amount, type, remark, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, amount, type, remark, System.currentTimeMillis());
    }

    public List<PointsTransaction> findByUser(long userId, int limit) {
        return jdbc.query(
                "SELECT * FROM points_transactions WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
                ROW_MAPPER, userId, limit
        );
    }
}
