package com.luodefei.memorycards.server.repository;

import com.luodefei.memorycards.server.model.Membership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;

@Repository
public class MembershipRepository {

    private static final RowMapper<Membership> ROW_MAPPER = (rs, rowNum) -> new Membership(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("level_id"),
            rs.getLong("start_at"),
            rs.getLong("end_at"),
            rs.getInt("bonus_points"),
            rs.getLong("created_at")
    );

    private final JdbcTemplate jdbc;

    public MembershipRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Membership> findLatestByUser(long userId) {
        return jdbc.query(
                "SELECT * FROM memberships WHERE user_id = ? ORDER BY end_at DESC LIMIT 1",
                ROW_MAPPER, userId
        ).stream().findFirst();
    }

    public long insert(long userId, long levelId, long startAt, long endAt, int bonusPoints) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memberships (user_id, level_id, start_at, end_at, bonus_points, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setLong(2, levelId);
            ps.setLong(3, startAt);
            ps.setLong(4, endAt);
            ps.setInt(5, bonusPoints);
            ps.setLong(6, System.currentTimeMillis());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("开通会员失败");
        }
        return key.longValue();
    }
}
