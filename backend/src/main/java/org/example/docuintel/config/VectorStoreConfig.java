package org.example.docuintel.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link VectorStore} bean used for storing and querying document embeddings.
 *
 * <p>Uses {@link SimpleVectorStore} (in-memory). To switch to a persistent backend
 * (pgvector, Pinecone, etc.), replace this bean and update {@code application.properties}
 * — no service code needs to change.
 */
@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}