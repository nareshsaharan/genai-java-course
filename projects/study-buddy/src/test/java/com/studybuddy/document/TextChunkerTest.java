package com.studybuddy.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.studybuddy.config.properties.RagProperties;
import dev.langchain4j.data.segment.TextSegment;

class TextChunkerTest {

    private final TextChunker chunker =
            new TextChunker(new RagProperties(400, 40, 5, 0.6));

    @Test
    void splitsLongTextIntoMultipleChunks() {
        String paragraph = "Java is a class-based, object-oriented programming language. ".repeat(120);

        List<TextSegment> chunks = chunker.chunk(paragraph);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).isNotBlank());
    }

    @Test
    void shortTextProducesExactlyOneChunk() {
        String shortText = "Java is a class-based, object-oriented programming language.";

        List<TextSegment> chunks = chunker.chunk(shortText);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo(shortText);
    }

    @Test
    void consecutiveChunksShareOverlappingSentences() {
        String paragraph = "Java is a class-based, object-oriented programming language. ".repeat(120);

        List<TextSegment> chunks = chunker.chunk(paragraph);
        List<String> texts = chunks.stream().map(TextSegment::text).collect(Collectors.toList());

        String lastSentenceOfFirstChunk = texts.get(0).strip();
        int lastPeriod = lastSentenceOfFirstChunk.lastIndexOf('.', lastSentenceOfFirstChunk.length() - 2);
        String tailSentence = lastSentenceOfFirstChunk.substring(Math.max(0, lastPeriod + 1)).strip();

        assertThat(texts.get(1)).contains(tailSentence);
    }
}
