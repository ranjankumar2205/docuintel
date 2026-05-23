package org.example.docuintel.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Classifies an uploaded document into one of seven types
 * (Resume, Invoice, Receipt, Payslip, Bank Statement, Offer Letter, Generic)
 * using GPT-4o via Spring AI's {@link ChatClient}.
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClassificationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Classifies the document and returns a result map with keys:
     * {@code document_title}, {@code document_summary}, {@code document_type},
     * {@code confidence_score}. Returns a safe fallback map if the response
     * cannot be parsed.
     *
     * @param pdfText full text extracted from the uploaded PDF
     */
    public Map<String, Object> classify(String pdfText) {
        log.debug("Sending classification request to GPT-4o");

        String rawResponse = chatClient
                .prompt()
                .system("You are a document classification AI.")
                .user(buildPrompt(pdfText))
                .call()
                .content();

        return parseJson(rawResponse, Map.of(
                "document_type",    "Generic",
                "document_title",   "Unknown",
                "document_summary", "Could not classify document.",
                "confidence_score", "0.0"
        ));
    }

    private String buildPrompt(String pdfText) {
        return """
                You are an intelligent document classifier.

                Classify the document into ONLY ONE of the following types:
                1. Resume
                2. Invoice
                3. Receipt
                4. Payslip
                5. Bank Statement
                6. Offer Letter
                7. Generic

                Respond ONLY in JSON with no markdown formatting:

                {
                  "document_title": "",
                  "document_summary": "",
                  "document_type": "",
                  "confidence_score": ""
                }

                Content:
                \\"\\"\\"
                %s
                \\"\\"\\"
                """.formatted(pdfText);
    }

    /**
     * Parses the model's JSON response, stripping markdown code fences if present.
     * Returns {@code fallback} on parse failure.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw, Map<String, Object> fallback) {
        try {
            String cleaned = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .strip();
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse classification response: {}", e.getMessage());
            log.debug("Raw response was: {}", raw);
            return fallback;
        }
    }
}
