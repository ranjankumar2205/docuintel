package org.example.docuintel.service;

import org.example.docuintel.config.AppConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits document text into overlapping word-window chunks for embedding.
 * Overlap ensures sentences at chunk boundaries appear complete in at least one chunk.
 * Chunk size and overlap are configured via {@code app.rag.chunk-size} and
 * {@code app.rag.chunk-overlap}.
 */
@Service
public class TextChunkingService {

    private final int chunkSize;
    private final int overlap;

    public TextChunkingService(AppConfig config) {
        this.chunkSize = config.chunkSize;
        this.overlap   = config.chunkOverlap;
    }

    /**
     * @param text full document text
     * @return ordered list of overlapping word-window chunks;
     *         empty list if {@code text} is null or blank
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
        }

        return chunks;
    }
}
