package com.studybuddy.document.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TxtDocumentTextLoaderTest {

    private final TxtDocumentTextLoader loader = new TxtDocumentTextLoader();

    @Test
    void supportsTxtExtensionOnly() {
        assertThat(loader.supports("notes.txt")).isTrue();
        assertThat(loader.supports("NOTES.TXT")).isTrue();
        assertThat(loader.supports("notes.md")).isFalse();
        assertThat(loader.supports(null)).isFalse();
    }

    @Test
    void extractsUtf8Text() throws Exception {
        String text = loader.extractText(new ByteArrayInputStream(
                "hello course notes".getBytes(StandardCharsets.UTF_8)));

        assertThat(text).isEqualTo("hello course notes");
    }
}
