package com.studybuddy.document.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.studybuddy.common.exception.UnsupportedFileTypeException;

class DocumentTextLoaderResolverTest {

    private final DocumentTextLoaderResolver resolver = new DocumentTextLoaderResolver(
            List.of(new PdfDocumentTextLoader(), new TxtDocumentTextLoader(), new MarkdownDocumentTextLoader()));

    @Test
    void resolvesPdfLoader() {
        assertThat(resolver.resolve("notes.pdf")).isInstanceOf(PdfDocumentTextLoader.class);
    }

    @Test
    void resolvesTxtLoader() {
        assertThat(resolver.resolve("notes.txt")).isInstanceOf(TxtDocumentTextLoader.class);
    }

    @Test
    void resolvesMarkdownLoader() {
        assertThat(resolver.resolve("notes.md")).isInstanceOf(MarkdownDocumentTextLoader.class);
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> resolver.resolve("notes.docx"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }
}
