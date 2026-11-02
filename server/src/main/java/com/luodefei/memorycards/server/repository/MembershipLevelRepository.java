package com.luodefei.memorycards.server.repository;

import com.luodefei.memorycards.server.model.MembershipLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MembershipLevelRepository {

    private static final RowMapper<MembershipLevel> ROW_MAPPER = (rs, rowNum) -> new MembershipLevel(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("price_cents"),
            rs.getInt("duration_days"),
            rs.getInt("bonus_points"),
            rs.getString("description")
    );

    private final JdbcTemplate jdbc;

    public MembershipLevelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MembershipLevel> findAll() {
        return jdbc.query("SELECT * FROM membership_levels ORDER BY price_cents ASC", ROW_MAPPER);
    }

    public Optional<MembershipLevel> findByCode(String code) {
        return jdbc.query("SELECT * FROM membership_levels WHERE code = ?", ROW_MAPPER, code)
                .stream().findFirst();
    }

    public Optional<MembershipLevel> findById(long id) {
        return jdbc.query("SELECT * FROM membership_levels WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public long insert(String code, String name, int priceCents, int durationDays,
                       int bonusPoints, String description) {
        jdbc.update("INSERT INTO membership_levels (code, name, price_cents, duration_days, bonus_points, description) VALUES (?, ?, ?, ?, ?, ?)",
                code, name, priceCents, durationDays, bonusPoints, description);
        return findByCode(code).orElseThrow().id();
    }
}
