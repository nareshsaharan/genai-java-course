package com.studybuddy.document.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * Markdown is read as-is (headings, lists, etc. kept intact) rather than
 * stripped: that structure is useful context for the tutor, not noise.
 */
@Component
public class MarkdownDocumentTextLoader implements DocumentTextLoader {

    @Override
    public boolean supports(String filename) {
        return filename != null && (filename.toLowerCase().endsWith(".md")
                || filename.toLowerCase().endsWith(".markdown"));
    }

    @Override
    public String extractText(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
