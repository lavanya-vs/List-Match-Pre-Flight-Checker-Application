package com.iqvia.preflight.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads the optional LLM knowledge base — domain rules and guidance for narrative text.
 */
@Service
public class AiKnowledgeService {

    private static final String KNOWLEDGE_PATH = "ai/knowledge-base.md";
    private volatile String cachedKnowledge;

    /** Returns the full knowledge document used as an LLM system prompt when enabled. */
    public String getKnowledgeBase() {
        if (cachedKnowledge != null) {
            return cachedKnowledge;
        }
        synchronized (this) {
            if (cachedKnowledge == null) {
                cachedKnowledge = loadKnowledge();
            }
            return cachedKnowledge;
        }
    }

    private String loadKnowledge() {
        try {
            ClassPathResource resource = new ClassPathResource(KNOWLEDGE_PATH);
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load AI knowledge base at " + KNOWLEDGE_PATH, e);
        }
    }
}
