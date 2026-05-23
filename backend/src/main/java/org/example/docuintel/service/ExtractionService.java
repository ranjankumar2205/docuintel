package org.example.docuintel.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts structured fields from RAG-retrieved document context using GPT-4o.
 *
 * <p>Supported types and their output fields:
 * <ul>
 *   <li>Invoice       — invoice_number, vendor_name, invoice_date, total_amount, currency</li>
 *   <li>Resume        — name, email, phone, skills, experience</li>
 *   <li>Receipt       — merchant_name, transaction_date, total_amount, payment_method, currency</li>
 *   <li>Payslip       — employee_name, employee_id, gross_pay, net_pay, month, designation</li>
 *   <li>Bank Statement— bank_name, account_holder, opening_balance, closing_balance, statement_period</li>
 *   <li>Offer Letter  — candidate_name, position, employer_name, salary, start_date</li>
 *   <li>Generic       — most relevant fields inferred from content</li>
 * </ul>
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExtractionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Extracts fields from the given context text for the given document type.
     *
     * @param documentType one of: Invoice, Resume, Receipt, Payslip,
     *                     Bank Statement, Offer Letter, Generic
     * @param contextText  the RAG-retrieved chunks joined together
     * @return extracted fields as a Map (serialised to JSON by Spring MVC)
     */
    public Map<String, Object> extract(String documentType, String contextText) {
        log.debug("Extracting fields for document type: {}", documentType);

        String rawResponse = chatClient
                .prompt()
                .system("You are a strict JSON extractor.")
                .user(buildPrompt(documentType, contextText))
                .call()
                .content();

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("document_type",   documentType);
        fallback.put("primary_field_1", null);
        fallback.put("primary_field_2", null);
        fallback.put("primary_field_3", null);
        fallback.put("primary_field_4", null);
        fallback.put("primary_field_5", null);
        return parseJson(rawResponse, fallback);
    }

    private String buildPrompt(String docType, String pdfText) {
        return """
                Extract the key fields based on the document type.

                Return ONLY the relevant fields for the detected document type using the EXACT field names defined below.
                If any field is not present, return null.

                Respond STRICTLY in VALID JSON.
                Do NOT include markdown, explanation, or extra text.

                FIELD DEFINITIONS:

                Invoice:
                invoice_number, vendor_name, invoice_date, total_amount, currency

                Resume:
                name, email, phone, skills, experience

                Receipt:
                merchant_name, transaction_date, total_amount, payment_method, currency

                Payslip:
                employee_name, employee_id, gross_pay, net_pay, month, designation

                Bank Statement:
                bank_name, account_holder, opening_balance, closing_balance, statement_period

                Offer Letter:
                candidate_name, position, employer_name, salary, start_date

                Generic:
                Extract most suitable data points based on the file

                Document Type: %s

                Content:
                \\"%s\\"

                Return JSON exactly like this example for Invoice:
                {
                  "document_type": "Invoice",
                  "invoice_number": "...",
                  "vendor_name": "...",
                  "invoice_date": "...",
                  "total_amount": "...",
                  "currency": "..."
                }
                """.formatted(docType, pdfText);
    }

    /** Parses the model's JSON response, stripping markdown fences. Returns {@code fallback} on error. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String raw, Map<String, Object> fallback) {
        try {
            String cleaned = raw
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .strip();
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse extraction response: {}", e.getMessage());
            return fallback;
        }
    }
}
