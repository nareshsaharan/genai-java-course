package com.studybuddy.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection details for the pgvector embedding store (LangChain4j's
 * PgVectorEmbeddingStore.builder() takes discrete host/port/database/user/
 * password/table fields, not a JDBC URL, so these are kept separate from
 * spring.datasource.* even though they point at the same database).
 */
@Validated
@ConfigurationProperties(prefix = "studybuddy.database")
public record DatabaseProperties(

        @NotBlank
        String host,

        @Positive
        int port,

        @NotBlank
        String database,

        @NotBlank
        String username,

        @NotBlank
        String password,

        @NotBlank
        String vectorTable,

        @Positive
        int vectorDimension
) {
}
