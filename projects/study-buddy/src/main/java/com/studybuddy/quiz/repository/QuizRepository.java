package com.studybuddy.quiz.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class QuizRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public QuizRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(QuizRecord quiz) {
        jdbcTemplate.update(
                "INSERT INTO quizzes (id, topic, difficulty, created_at) VALUES (?, ?, ?, ?)",
                quiz.id(), quiz.topic(), quiz.difficulty(), Timestamp.from(quiz.createdAt()));

        if (quiz.questions().isEmpty()) {
            return;
        }

        String sql = "INSERT INTO quiz_questions "
                + "(id, quiz_id, question_index, question_text, options, correct_option_index, source_chunk_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (QuizQuestionRecord question : quiz.questions()) {
                    statement.setObject(1, question.id());
                    statement.setObject(2, question.quizId());
                    statement.setInt(3, question.questionIndex());
                    statement.setString(4, question.questionText());
                    statement.setObject(5, toJsonb(question.options()));
                    statement.setInt(6, question.correctOptionIndex());
                    statement.setObject(7, question.sourceChunkId());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    public Optional<QuizRecord> findById(UUID quizId) {
        List<Object[]> quizRows = jdbcTemplate.query(
                "SELECT id, topic, difficulty, created_at FROM quizzes WHERE id = ?",
                (rs, rowNum) -> new Object[]{
                        rs.getObject("id"), rs.getString("topic"), rs.getString("difficulty"), rs.getTimestamp("created_at")},
                quizId);
        if (quizRows.isEmpty()) {
            return Optional.empty();
        }
        Object[] quizRow = quizRows.get(0);

        List<QuizQuestionRecord> questions = jdbcTemplate.query(
                "SELECT id, quiz_id, question_index, question_text, options, correct_option_index, source_chunk_id "
                        + "FROM quiz_questions WHERE quiz_id = ? ORDER BY question_index",
                this::mapQuestionRow,
                quizId);

        return Optional.of(new QuizRecord(
                (UUID) quizRow[0],
                (String) quizRow[1],
                (String) quizRow[2],
                ((Timestamp) quizRow[3]).toInstant(),
                questions));
    }

    private QuizQuestionRecord mapQuestionRow(ResultSet rs, int rowNum) throws SQLException {
        return new QuizQuestionRecord(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("quiz_id"),
                rs.getInt("question_index"),
                rs.getString("question_text"),
                fromJsonb(rs.getString("options")),
                rs.getInt("correct_option_index"),
                (UUID) rs.getObject("source_chunk_id"));
    }

    private PGobject toJsonb(List<String> options) {
        try {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(objectMapper.writeValueAsString(options));
            return jsonObject;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize quiz question options", e);
        }
    }

    private List<String> fromJsonb(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize quiz question options", e);
        }
    }
}
