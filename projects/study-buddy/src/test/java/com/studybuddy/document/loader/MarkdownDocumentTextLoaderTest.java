package com.studybuddy.document.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MarkdownDocumentTextLoaderTest {

    private final MarkdownDocumentTextLoader loader = new MarkdownDocumentTextLoader();

    @Test
    void supportsMarkdownExtensions() {
        assertThat(loader.supports("notes.md")).isTrue();
        assertThat(loader.supports("notes.markdown")).isTrue();
        assertThat(loader.supports("notes.txt")).isFalse();
    }

    @Test
    void extractsRawMarkdownIncludingSyntax() throws Exception {
        String markdown = "# Heading\n\n- item one\n- item two";

        String text = loader.extractText(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

        assertThat(text).isEqualTo(markdown);
    }
}
