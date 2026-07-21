package com.studybuddy.config.properties;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tuning knobs for weak-topic classification and recommendation. See TopicProgressCalculator/TopicClassifier/TopicRecommender. */
@Validated
@ConfigurationProperties(prefix = "studybuddy.progress")
public record ProgressProperties(

        @Positive
        int minAttemptsForClassification,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double weakAccuracyThreshold,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double recencyWeight,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        double similarityTolerance
) {
}
