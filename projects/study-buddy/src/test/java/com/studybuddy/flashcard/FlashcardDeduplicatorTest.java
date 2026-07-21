package com.studybuddy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class FlashcardDeduplicatorTest {

    @Test
    void keepsAllDistinctQuestions() {
        List<GeneratedFlashcard> cards = List.of(
                new GeneratedFlashcard("What is dependency injection?", "A design pattern..."),
                new GeneratedFlashcard("What is a bean in Spring?", "A managed object..."));

        List<GeneratedFlashcard> result = FlashcardDeduplicator.deduplicate(cards);

        assertThat(result).hasSize(2);
    }

    @Test
    void removesExactDuplicateQuestionIgnoringCaseAndPunctuation() {
        List<GeneratedFlashcard> cards = List.of(
                new GeneratedFlashcard("What is dependency injection?", "Answer one"),
                new GeneratedFlashcard("what is DEPENDENCY INJECTION", "Answer two"));

        List<GeneratedFlashcard> result = FlashcardDeduplicator.deduplicate(cards);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).answer()).isEqualTo("Answer one");
    }

    @Test
    void removesNearDuplicateQuestionWithMinorWordingDifference() {
        List<GeneratedFlashcard> cards = List.of(
                new GeneratedFlashcard("What is dependency injection in Spring?", "Answer one"),
                new GeneratedFlashcard("What's dependency injection in Spring?", "Answer two"));

        List<GeneratedFlashcard> result = FlashcardDeduplicator.deduplicate(cards);

        assertThat(result).hasSize(1);
    }

    @Test
    void preservesOriginalOrderOfKeptCards() {
        List<GeneratedFlashcard> cards = List.of(
                new GeneratedFlashcard("Question A", "Answer A"),
                new GeneratedFlashcard("Question B", "Answer B"),
                new GeneratedFlashcard("Question C", "Answer C"));

        List<GeneratedFlashcard> result = FlashcardDeduplicator.deduplicate(cards);

        assertThat(result).extracting(GeneratedFlashcard::question)
                .containsExactly("Question A", "Question B", "Question C");
    }

    @Test
    void emptyListReturnsEmptyList() {
        assertThat(FlashcardDeduplicator.deduplicate(List.of())).isEmpty();
    }
}
