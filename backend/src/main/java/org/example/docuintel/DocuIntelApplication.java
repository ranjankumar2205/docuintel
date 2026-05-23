package org.example.docuintel;

import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DocuIntel application entry point.
 *
 * <p>Chroma auto-configuration is excluded because the vector store is
 * configured manually via {@link org.example.docuintel.config.VectorStoreConfig}.
 */
@SpringBootApplication(exclude = {ChromaVectorStoreAutoConfiguration.class})
public class DocuIntelApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuIntelApplication.class, args);
    }

}
