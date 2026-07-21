package com.studybuddy.document.loader;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text from one uploaded file type. Extracted text is treated
 * purely as inert data throughout ingestion: it is only ever chunked,
 * embedded and stored, never executed, evaluated, or passed anywhere as an
 * instruction — uploaded documents cannot make the application do anything.
 */
public interface DocumentTextLoader {

    /** Whether this loader handles the given filename (by extension). */
    boolean supports(String filename);

    /** Extracts the full text content of the file. */
    String extractText(InputStream inputStream) throws IOException;
}
