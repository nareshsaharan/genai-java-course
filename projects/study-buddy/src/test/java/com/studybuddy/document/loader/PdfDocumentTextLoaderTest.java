package com.studybuddy.document.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfDocumentTextLoaderTest {

    private final PdfDocumentTextLoader loader = new PdfDocumentTextLoader();

    @Test
    void supportsPdfExtensionOnly() {
        assertThat(loader.supports("notes.pdf")).isTrue();
        assertThat(loader.supports("notes.txt")).isFalse();
    }

    @Test
    void extractsTextFromAGeneratedPdf() throws Exception {
        byte[] pdfBytes = generateSimplePdf("hello pdf course notes");

        String text = loader.extractText(new ByteArrayInputStream(pdfBytes));

        assertThat(text).contains("hello pdf course notes");
    }

    @Test
    void corruptedPdfRaisesIOException() {
        byte[] garbage = "not a real pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.extractText(new ByteArrayInputStream(garbage)))
                .isInstanceOf(IOException.class);
    }

    private static byte[] generateSimplePdf(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
