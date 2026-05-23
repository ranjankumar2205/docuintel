package org.example.docuintel.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;

/**
 * Extracts plain text from PDF files using Apache PDFBox.
 * Duplicate lines (e.g. repeated headers/footers) are removed after extraction.
 */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extracts and deduplicates text from all pages of the given PDF.
     *
     * @param pdfPath path to the saved PDF file
     * @return extracted text, or an empty string if the file is unreadable or has no text layer
     */
    public String extract(Path pdfPath) {
        try (PDDocument document = Loader.loadPDF(new File(pdfPath.toString()))) {
            String rawText = new PDFTextStripper().getText(document);

            if (rawText == null || rawText.isBlank()) {
                log.warn("No text extracted from PDF: {}", pdfPath);
                return "";
            }

            String deduplicated = deduplicate(rawText);
            log.debug("Extracted {} chars from {}", deduplicated.length(), pdfPath.getFileName());
            return deduplicated;

        } catch (IOException e) {
            log.error("PDF extraction failed for {}: {}", pdfPath, e.getMessage());
            return "";
        }
    }

    /**
     * Removes duplicate lines while preserving original order.
     * Uses a {@link LinkedHashSet} as a case-insensitive seen-set.
     */
    private String deduplicate(String text) {
        String[] lines = text.split("\\r?\\n");
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            String key = line.strip().toLowerCase();
            if (!key.isEmpty() && seen.add(key)) {
                result.append(line.strip()).append("\n");
            }
        }

        return result.toString().strip();
    }
}
