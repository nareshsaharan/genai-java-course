package com.studybuddy.quiz.repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QuizAttemptRepository {

    private static final String INSERT_ANSWER_SQL =
            "INSERT INTO quiz_answers (id, attempt_id, question_id, selected_option_index, is_correct) "
                    + "VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public QuizAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void save(QuizAttemptRecord attempt) {
        jdbcTemplate.update(
                "INSERT INTO quiz_attempts (id, quiz_id, topic, correct_count, total_count, submitted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                attempt.id(), attempt.quizId(), attempt.topic(), attempt.correctCount(), attempt.totalCount(),
                Timestamp.from(attempt.submittedAt()));

        if (attempt.answers().isEmpty()) {
            return;
        }

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ANSWER_SQL)) {
                for (QuizAnswerRecord answer : attempt.answers()) {
                    statement.setObject(1, answer.id());
                    statement.setObject(2, answer.attemptId());
                    statement.setObject(3, answer.questionId());
                    statement.setInt(4, answer.selectedOptionIndex());
                    statement.setBoolean(5, answer.correct());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }
}
