package org.example.docuintel.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Binds application properties and performs startup initialisation.
 * Ensures the upload directory exists before the app begins serving requests.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** Number of chunks to retrieve per RAG query. */
    @Value("${app.rag.top-k}")
    public int ragTopK;

    /** Maximum word count per text chunk. */
    @Value("${app.rag.chunk-size}")
    public int chunkSize;

    /** Word overlap between consecutive chunks to preserve boundary context. */
    @Value("${app.rag.chunk-overlap}")
    public int chunkOverlap;

    /** Creates the upload directory on first startup if it does not exist. */
    @PostConstruct
    public void init() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }
    }

    public Path uploadPath() {
        return Paths.get(uploadDir);
    }

    public String getUploadPath() {
        return uploadDir;
    }
}
