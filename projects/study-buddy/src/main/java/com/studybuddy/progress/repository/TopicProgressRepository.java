package com.studybuddy.progress.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TopicProgressRepository {

    private static final String COLUMNS =
            "topic, correct_count, total_count, accuracy, last_attempt_at, last_recommended_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public TopicProgressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TopicProgressRecord> findByTopic(String topic) {
        List<TopicProgressRecord> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM topic_progress WHERE topic = ?", this::mapRow, topic);
        return rows.stream().findFirst();
    }

    public List<TopicProgressRecord> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM topic_progress ORDER BY topic", this::mapRow);
    }

    /**
     * Inserts or updates a topic's stats. Deliberately does not touch
     * {@code last_recommended_at} — that's owned exclusively by
     * {@link #markRecommended(String, Instant)}, so a quiz submission never
     * resets the recommendation-rotation state.
     */
    public void upsert(String topic, int correctCount, int totalCount, double accuracy, Instant lastAttemptAt) {
        jdbcTemplate.update(
                "INSERT INTO topic_progress (topic, correct_count, total_count, accuracy, last_attempt_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, now()) "
                        + "ON CONFLICT (topic) DO UPDATE SET "
                        + "correct_count = EXCLUDED.correct_count, "
                        + "total_count = EXCLUDED.total_count, "
                        + "accuracy = EXCLUDED.accuracy, "
                        + "last_attempt_at = EXCLUDED.last_attempt_at, "
                        + "updated_at = now()",
                topic, correctCount, totalCount, accuracy, Timestamp.from(lastAttemptAt));
    }

    public void markRecommended(String topic, Instant recommendedAt) {
        jdbcTemplate.update(
                "UPDATE topic_progress SET last_recommended_at = ? WHERE topic = ?",
                Timestamp.from(recommendedAt), topic);
    }

    private TopicProgressRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastRecommended = rs.getTimestamp("last_recommended_at");
        return new TopicProgressRecord(
                rs.getString("topic"),
                rs.getInt("correct_count"),
                rs.getInt("total_count"),
                rs.getDouble("accuracy"),
                rs.getTimestamp("last_attempt_at").toInstant(),
                lastRecommended != null ? lastRecommended.toInstant() : null,
                rs.getTimestamp("updated_at").toInstant());
    }
}
