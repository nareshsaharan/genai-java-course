package com.studybuddy.document.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final String COLUMNS =
            "id, source_filename, topic, content_hash, chunk_count, status, created_at";

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DocumentRecord> findByContentHash(String contentHash) {
        List<DocumentRecord> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM documents WHERE content_hash = ?",
                this::mapRow,
                contentHash);
        return rows.stream().findFirst();
    }

    public void insert(DocumentRecord document) {
        jdbcTemplate.update(
                "INSERT INTO documents (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                document.id(),
                document.sourceFilename(),
                document.topic(),
                document.contentHash(),
                document.chunkCount(),
                document.status(),
                Timestamp.from(document.createdAt()));
    }

    private DocumentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentRecord(
                (UUID) rs.getObject("id"),
                rs.getString("source_filename"),
                rs.getString("topic"),
                rs.getString("content_hash"),
                rs.getInt("chunk_count"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
