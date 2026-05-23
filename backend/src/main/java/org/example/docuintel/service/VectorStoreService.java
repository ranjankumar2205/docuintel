package org.example.docuintel.service;

import org.example.docuintel.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages vector store ingestion and retrieval for the RAG pipeline.
 *
 * <p>All chunks are tagged with a {@code session_id} metadata field so that
 * search results are always scoped to a single upload session.
 * The service programs to the {@link VectorStore} interface; swapping the
 * backend requires only a dependency and property change.
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final VectorStore vectorStore;
    private final TextChunkingService chunker;
    private final ChatClient chatClient;
    private final int topK;

    public VectorStoreService(VectorStore vectorStore,
                              TextChunkingService chunker,
                              ChatClient.Builder chatClientBuilder,
                              AppConfig config) {
        this.vectorStore = vectorStore;
        this.chunker     = chunker;
        this.chatClient  = chatClientBuilder.build();
        this.topK        = config.ragTopK;
    }

    /**
     * Chunks the document text, embeds each chunk, and persists it with
     * session and classification metadata.
     *
     * @param sessionId          UUID identifying this upload session
     * @param text               full extracted document text
     * @param documentType       classification result, e.g. {@code "Invoice"}
     * @param classificationJson full classification payload serialised as JSON
     */
    public void storeEmbeddings(String sessionId, String text,
                                String documentType, String classificationJson) {
        List<String> chunks = chunker.chunk(text);
        log.debug("Storing {} chunks for session {}", chunks.size(), sessionId);

        List<Document> documents = chunks.stream()
                .map(chunk -> new Document(chunk, Map.of(
                        "session_id",            sessionId,
                        "document_type",         documentType,
                        "classification_result", classificationJson
                )))
                .collect(Collectors.toList());

        vectorStore.add(documents);
        log.debug("Stored embeddings for session {}", sessionId);
    }

    /**
     * Returns the top-K chunks most semantically similar to {@code query},
     * filtered to the specified session.
     */
    public List<Document> search(String sessionId, String query) {
        var filter = new FilterExpressionBuilder()
                .eq("session_id", sessionId)
                .build();

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(filter)
                        .build());

        log.debug("Found {} chunks for session {} query '{}'", results.size(), sessionId, query);
        return results;
    }

    /**
     * Reads the document type from the first retrieved chunk's metadata.
     * Returns {@code "Generic"} if no chunks exist for the session.
     */
    public String getDocumentType(String sessionId) {
        List<Document> docs = search(sessionId, "document type classification");
        if (docs.isEmpty()) return "Generic";
        Object type = docs.get(0).getMetadata().get("document_type");
        return type != null ? type.toString() : "Generic";
    }

    /** Joins the text of a list of documents into a single context string. */
    public String buildContext(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Retrieves the most relevant chunks for the query, then asks GPT-4o to
     * answer using only that document context.
     *
     * @param sessionId session to query
     * @param userQuery natural-language question
     * @return grounded answer, or a "not found" message if no chunks match
     */
    public String chat(String sessionId, String userQuery) {
        List<Document> relevantChunks = search(sessionId, userQuery);

        if (relevantChunks.isEmpty()) {
            return "No relevant context found in the document for your query.";
        }

        String prompt = """
                You are an intelligent assistant for PDF Q&A.
                Use ONLY the following extracted document content to answer the user's question.
                Be strictly factual and cite facts from the document text below:
                -----------------------------
                %s
                -----------------------------

                User question:
                %s

                If the answer is not clearly in the document context, reply:
                "Not available in the document." and suggest questions that can be asked based on the document.
                """.formatted(buildContext(relevantChunks), userQuery);

        return chatClient
                .prompt()
                .system("Only use the document context provided to answer the user.")
                .user(prompt)
                .call()
                .content();
    }
}
