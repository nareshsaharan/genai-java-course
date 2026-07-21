package com.studybuddy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LangChain4j's AiServices parses Claude's forced-tool-call arguments into
 * {@code List<GeneratedFlashcard>} via Jackson under the hood (see the
 * README's "typed structured output" section). This test doesn't reach into
 * LangChain4j's internals; it exercises real Jackson deserialization
 * directly against {@link GeneratedFlashcard} with the same shape of JSON
 * that mechanism produces — confirming the target type itself deserializes
 * correctly and that malformed JSON fails clearly rather than silently.
 */
class GeneratedFlashcardJsonDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesAJsonArrayIntoTypedFlashcards() throws JsonProcessingException {
        String json = """
                [
                  {"question": "What is RAG?", "answer": "Retrieval augmented generation."},
                  {"question": "What is a chunk?", "answer": "A segment of a document."}
                ]
                """;

        List<GeneratedFlashcard> cards = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0)).isEqualTo(new GeneratedFlashcard("What is RAG?", "Retrieval augmented generation."));
        assertThat(cards.get(1).question()).isEqualTo("What is a chunk?");
    }

    @Test
    void malformedJsonFailsDeserializationClearly() {
        String malformed = "{\"question\": \"What is RAG?\" \"answer\": missing comma}";

        assertThatThrownBy(() -> objectMapper.readValue(malformed, GeneratedFlashcard.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void unknownFieldsCanBeToleratedWithPermissiveConfiguration() throws JsonProcessingException {
        // A default ObjectMapper rejects unknown properties; configuring it to
        // ignore them (a reasonable stance for LLM-produced JSON, which may add
        // stray fields) still deserializes the fields GeneratedFlashcard cares about.
        ObjectMapper permissiveMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String json = """
                {"question": "What is RAG?", "answer": "Retrieval augmented generation.", "difficulty": "HARD"}
                """;

        GeneratedFlashcard card = permissiveMapper.readValue(json, GeneratedFlashcard.class);

        assertThat(card.question()).isEqualTo("What is RAG?");
        assertThat(card.answer()).isEqualTo("Retrieval augmented generation.");
    }

    @Test
    void unknownFieldsFailByDefault() {
        String json = """
                {"question": "What is RAG?", "answer": "Retrieval augmented generation.", "difficulty": "HARD"}
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, GeneratedFlashcard.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
