package com.studybuddy.progress.dto;

import java.util.List;

/**
 * An aggregate view combining every weak topic with the single next-topic
 * recommendation. {@code recommendation} is {@code null} when there isn't
 * enough data (or nothing weak) to recommend anything yet — that's a valid,
 * expected outcome, not an error.
 */
public record StudyPlan(
        List<TopicProgressView> weakTopics,
        RecommendationResponse recommendation
) {
}
