package org.example.docuintel.controller;

import org.example.docuintel.config.AppConfig;
import org.example.docuintel.model.ApiModels.*;
import org.example.docuintel.service.*;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exposes the DocuIntel REST API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /}               — health check</li>
 *   <li>{@code POST /upload-pdf}      — upload and classify a PDF</li>
 *   <li>{@code POST /api/v1/extract}  — structured field extraction via RAG</li>
 *   <li>{@code POST /chat}            — document Q&amp;A via RAG</li>
 * </ul>
 */
@RestController
@CrossOrigin
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final PdfExtractionService pdfExtractor;
    private final ClassificationService classifier;
    private final VectorStoreService vectorStore;
    private final ExtractionService extractor;
    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentController(PdfExtractionService pdfExtractor,
                              ClassificationService classifier,
                              VectorStoreService vectorStore,
                              ExtractionService extractor,
                              AppConfig config) {
        this.pdfExtractor = pdfExtractor;
        this.classifier   = classifier;
        this.vectorStore  = vectorStore;
        this.extractor    = extractor;
        this.config       = config;
    }

    /** Returns application health status. */
    @GetMapping("/")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("Backend Running Successfully", "UP"));
    }

    /**
     * Accepts a PDF upload, extracts text, classifies the document,
     * and stores chunk embeddings keyed by a new session ID.
     *
     * @param file the uploaded PDF (multipart field name: {@code file})
     * @return session ID and classification result on success;
     *         400 for non-PDF, 422 for unreadable content, 500 on I/O failure
     */
    @PostMapping(value = "/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Only PDF files are allowed."));
        }

        try {
            String sessionId = UUID.randomUUID().toString();
            Path savedPath = config.uploadPath().resolve(sessionId + "_" + originalName);
            file.transferTo(savedPath);
            log.debug("Saved PDF to {}", savedPath);

            String text = pdfExtractor.extract(savedPath);
            if (text.isBlank()) {
                return ResponseEntity.unprocessableEntity()
                        .body(Map.of("detail", "Could not extract usable text from PDF."));
            }

            Map<String, Object> classificationResult = classifier.classify(text);
            String docType = (String) classificationResult.getOrDefault("document_type", "Generic");
            log.debug("Classified as: {}", docType);

            vectorStore.storeEmbeddings(
                    sessionId, text, docType,
                    objectMapper.writeValueAsString(classificationResult)
            );

            return ResponseEntity.ok(new UploadDocumentResponse(sessionId, classificationResult));

        } catch (IOException e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("detail", "File processing failed: " + e.getMessage()));
        }
    }

    /**
     * Retrieves the top-K relevant chunks for the session and extracts
     * structured fields using GPT-4o.
     *
     * @param req must contain a valid {@code session_id}
     * @return extracted fields map; 404 if the session has no stored chunks
     */
    @PostMapping("/api/v1/extract")
    public ResponseEntity<?> extractData(@RequestBody ExtractionRequest req) {
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "session_id is required."));
        }

        List<Document> docs = vectorStore.search(
                req.sessionId(), "Extract key structured fields from this document");

        if (docs.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(Map.of("detail", "No session found. Please upload a document first."));
        }

        String docType  = (String) docs.get(0).getMetadata().getOrDefault("document_type", "Generic");
        String context  = vectorStore.buildContext(docs);
        Map<String, Object> extracted = extractor.extract(docType, context);

        return ResponseEntity.ok(new ExtractionResponse(extracted));
    }

    /**
     * Answers a natural-language question using only the content of the
     * uploaded document (RAG-based Q&amp;A).
     *
     * @param req must contain {@code session_id} and {@code query}
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req) {
        if (req.sessionId() == null || req.sessionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "session_id is required."));
        }
        if (req.query() == null || req.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "query is required."));
        }

        return ResponseEntity.ok(new ChatResponse(vectorStore.chat(req.sessionId(), req.query())));
    }
}
