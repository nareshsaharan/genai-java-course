package com.studybuddy.flashcard.repository;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FlashcardRepository {

    private final JdbcTemplate jdbcTemplate;

    public FlashcardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void saveAll(List<FlashcardRecord> cards) {
        for (FlashcardRecord card : cards) {
            jdbcTemplate.update(
                    "INSERT INTO flashcards (id, topic, difficulty, question, answer, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    card.id(), card.topic(), card.difficulty(), card.question(), card.answer(),
                    Timestamp.from(card.createdAt()));

            for (var chunkId : card.sourceChunkIds()) {
                jdbcTemplate.update(
                        "INSERT INTO flashcard_sources (flashcard_id, chunk_id) VALUES (?, ?)",
                        card.id(), chunkId);
            }
        }
    }
}
