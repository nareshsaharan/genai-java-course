package com.studybuddy.document.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cosine-similarity search over course_chunks using pgvector's {@code <=>}
 * (cosine distance) operator. topK bounds how many rows are fetched;
 * minScore then filters that set, so a low-relevance top-K doesn't get
 * padded with near-zero matches.
 */
@Repository
public class CourseChunkSearchRepository {

    private static final String SQL_ALL_TOPICS =
            "SELECT id, content, source_file, chunk_index, 1 - (embedding <=> ?) AS similarity "
                    + "FROM course_chunks ORDER BY embedding <=> ? LIMIT ?";

    private static final String SQL_BY_TOPIC =
            "SELECT id, content, source_file, chunk_index, 1 - (embedding <=> ?) AS similarity "
                    + "FROM course_chunks WHERE topic = ? ORDER BY embedding <=> ? LIMIT ?";

    private final JdbcTemplate jdbcTemplate;

    public CourseChunkSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChunkSearchResult> search(float[] queryEmbedding, String topic, int topK, double minScore) {
        boolean filterByTopic = topic != null && !topic.isBlank();
        String sql = filterByTopic ? SQL_BY_TOPIC : SQL_ALL_TOPICS;

        return jdbcTemplate.execute((ConnectionCallback<List<ChunkSearchResult>>) connection -> {
            PGvector.addVectorType(connection);
            PGvector vector = new PGvector(queryEmbedding);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                statement.setObject(index++, vector);
                if (filterByTopic) {
                    statement.setString(index++, topic);
                }
                statement.setObject(index++, vector);
                statement.setInt(index, topK);

                List<ChunkSearchResult> results = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        double similarity = resultSet.getDouble("similarity");
                        if (similarity >= minScore) {
                            results.add(new ChunkSearchResult(
                                    (UUID) resultSet.getObject("id"),
                                    resultSet.getString("content"),
                                    resultSet.getString("source_file"),
                                    resultSet.getInt("chunk_index"),
                                    similarity));
                        }
                    }
                }
                return results;
            }
        });
    }
}
