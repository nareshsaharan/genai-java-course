package com.studybuddy.document;

import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.config.properties.RagProperties;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;

/**
 * Splits extracted document text into overlapping chunks (~400 tokens each,
 * ~40 token overlap by default — see {@link RagProperties}), recursively
 * falling back from paragraphs to lines to sentences to words so each chunk
 * fits the configured size.
 */
@Component
public class TextChunker {

    private final DocumentSplitter splitter;

    public TextChunker(RagProperties ragProperties) {
        this.splitter = DocumentSplitters.recursive(
                ragProperties.chunkSizeTokens(), ragProperties.chunkOverlapTokens());
    }

    public List<TextSegment> chunk(String text) {
        return splitter.split(Document.from(text));
    }
}
