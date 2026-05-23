package org.example.docuintel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * All API request and response models, declared as Java records.
 * {@code @JsonProperty} maps each component to its snake_case JSON field name.
 */
public final class ApiModels {

    /**
     * Response returned after a successful PDF upload.
     *
     * @param sessionId            UUID identifying this upload session; required for extract and chat calls
     * @param classificationResult document type, title, summary, and confidence from GPT-4o
     */
    public record UploadDocumentResponse(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("classification_result") Map<String, Object> classificationResult
    ) {}

    /**
     * @param sessionId session UUID returned by {@code /upload-pdf}
     */
    public record ExtractionRequest(
            @JsonProperty("session_id") String sessionId
    ) {}

    /**
     * @param extractedData type-specific fields extracted by GPT-4o; absent fields are {@code null}
     */
    public record ExtractionResponse(
            @JsonProperty("extracted_data") Map<String, Object> extractedData
    ) {}

    /**
     * @param sessionId session UUID returned by {@code /upload-pdf}
     * @param query     natural-language question to answer from the document
     */
    public record ChatRequest(
            @JsonProperty("session_id") String sessionId,
            @JsonProperty("query") String query
    ) {}

    /** @param response GPT-4o answer grounded in the document context */
    public record ChatResponse(
            @JsonProperty("response") String response
    ) {}

    /** @param message human-readable status; @param status short code, e.g. {@code "UP"} */
    public record HealthResponse(
            @JsonProperty("message") String message,
            @JsonProperty("status") String status
    ) {}

    private ApiModels() {}
}
