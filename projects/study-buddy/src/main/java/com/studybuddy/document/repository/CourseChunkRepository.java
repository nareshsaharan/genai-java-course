package com.studybuddy.document.repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CourseChunkRepository {

    private static final String INSERT_SQL =
            "INSERT INTO course_chunks "
                    + "(id, document_id, content, embedding, source_file, topic, chunk_index, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public CourseChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(List<CourseChunkRecord> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            PGvector.addVectorType(connection);
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
                for (CourseChunkRecord chunk : chunks) {
                    statement.setObject(1, chunk.id());
                    statement.setObject(2, chunk.documentId());
                    statement.setString(3, chunk.content());
                    statement.setObject(4, new PGvector(chunk.embedding()));
                    statement.setString(5, chunk.sourceFile());
                    statement.setString(6, chunk.topic());
                    statement.setInt(7, chunk.chunkIndex());
                    statement.setTimestamp(8, Timestamp.from(chunk.createdAt()));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }
}
