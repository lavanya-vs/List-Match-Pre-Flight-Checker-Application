package com.iqvia.preflight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iqvia.preflight.config.AiAnalysisProperties;
import com.iqvia.preflight.dto.AiAnalysis;
import com.iqvia.preflight.dto.ColumnInsight;
import com.iqvia.preflight.dto.DataQualityStats;
import com.iqvia.preflight.dto.PreflightResult;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * Calls an external LLM with a small, token-friendly summary — never the full file.
 * If the call fails, the caller keeps the built-in local analysis instead.
 */
@Service
public class LlmClientService {

    private final AiAnalysisProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiKnowledgeService knowledgeService;

    public LlmClientService(
            AiAnalysisProperties properties,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            AiKnowledgeService knowledgeService
    ) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(45))
                .build();
        this.objectMapper = objectMapper;
        this.knowledgeService = knowledgeService;
    }

    /** Send a system + user prompt and return the assistant JSON text. */
    public String completeJson(String systemPrompt, String userPrompt) {
        if (!properties.isLlmConfigured()) {
            throw new RestClientException("LLM not configured");
        }
        return callChatApi(systemPrompt, userPrompt);
    }

    /**
     * Ask the LLM to polish the narrative sections only.
     * Returns empty when LLM is off, misconfigured, or errors out.
     */
    public Optional<LlmNarrative> enhanceNarrative(
            PreflightResult result,
            DataQualityStats stats,
            List<ColumnInsight> columnInsights
    ) {
        if (!properties.isLlmConfigured()) {
            return Optional.empty();
        }

        try {
            String compactPayload = buildCompactPayload(result, stats, columnInsights);
            String systemPrompt = knowledgeService.getKnowledgeBase();
            String userPrompt = "Write the narrative for this pre-flight result:\n" + compactPayload;

            String responseBody = completeJson(systemPrompt, userPrompt);
            return parseNarrative(responseBody);
        } catch (Exception ex) {
            // Swallow and fall back — a failed LLM call must not break the upload flow.
            return Optional.empty();
        }
    }

    /**
     * Pack only counts and labels into JSON so we spend few tokens and send no full rows.
     */
    private String buildCompactPayload(
            PreflightResult result,
            DataQualityStats stats,
            List<ColumnInsight> columnInsights
    ) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileName", result.getFileName());
        payload.put("totalRows", stats.getTotalRows());
        payload.put("score", result.getScore().name());
        payload.put("summary", result.getSummary());
        payload.put("globalErrors", result.getGlobalErrors());
        payload.put("warningRowPercent", stats.getWarningRowPercent());
        payload.put("warningCounts", stats.getWarningCounts());
        payload.put("patternFlags", stats.getPatternFlags());
        payload.put("matchDifficulty", result.getEstimatedMatchDifficulty());

        List<Map<String, Object>> matchTypes = new ArrayList<>();
        for (PreflightResult.MatchTypeAssessment mt : result.getMatchTypes()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", mt.getLabel());
            item.put("viable", mt.isViable());
            item.put("coveragePercent", mt.getCoveragePercent());
            matchTypes.add(item);
        }
        payload.put("matchTypes", matchTypes);

        // Only columns with gaps matter for the narrative — clean columns cost tokens and add nothing.
        List<Map<String, Object>> columns = new ArrayList<>();
        for (ColumnInsight insight : columnInsights) {
            if (columns.size() >= 8 || insight.getMissingPercent() <= 0) {
                continue;
            }
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("header", insight.getHeaderName());
            col.put("suggested", insight.getSuggestedField());
            col.put("missingPercent", insight.getMissingPercent());
            columns.add(col);
        }
        payload.put("columnsWithGaps", columns);

        return objectMapper.writeValueAsString(payload);
    }

    private String callChatApi(String systemPrompt, String userPrompt) {
        if ("azure".equalsIgnoreCase(properties.getProvider())) {
            return callAzure(systemPrompt, userPrompt);
        }
        return callOpenAiCompatible(systemPrompt, userPrompt);
    }

    /** Standard OpenAI-style chat completions endpoint. */
    private String callOpenAiCompatible(String systemPrompt, String userPrompt) {
        AiAnalysisProperties.OpenAi openAi = properties.getOpenai();
        return callChatCompletions(
                openAi.getBaseUrl(),
                openAi.getApiKey(),
                openAi.getModel(),
                systemPrompt,
                userPrompt
        );
    }

    private String callChatCompletions(
            String baseUrl,
            String apiKey,
            String model,
            String systemPrompt,
            String userPrompt
    ) {
        String url = baseUrl.replaceAll("/$", "") + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        return extractAssistantContent(response.getBody());
    }

    /** Azure OpenAI uses a different URL shape but the same message format. */
    private String callAzure(String systemPrompt, String userPrompt) {
        AiAnalysisProperties.Azure azure = properties.getAzure();
        String url = azure.getEndpoint().replaceAll("/$", "")
                + "/openai/deployments/" + azure.getDeployment()
                + "/chat/completions?api-version=" + azure.getApiVersion();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("temperature", 0.3);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", azure.getApiKey());

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        return extractAssistantContent(response.getBody());
    }

    /** Pull the assistant text out of the chat completion JSON. */
    private String extractAssistantContent(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            throw new RestClientException("Empty LLM response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isEmpty()) {
                throw new RestClientException("LLM returned no content");
            }
            return content.asText();
        } catch (Exception e) {
            throw new RestClientException("Could not parse LLM response: " + e.getMessage());
        }
    }

    /** Parse the JSON object the model was asked to return. */
    private Optional<LlmNarrative> parseNarrative(String jsonContent) throws Exception {
        JsonNode node = objectMapper.readTree(jsonContent);
        String summary = node.path("executiveSummary").asText("");
        String strategy = node.path("recommendedMatchStrategy").asText("");
        if (summary.isEmpty() && strategy.isEmpty()) {
            return Optional.empty();
        }

        List<String> riskNotes = new ArrayList<>();
        JsonNode risks = node.path("riskNotes");
        if (risks.isArray()) {
            for (JsonNode risk : risks) {
                String text = risk.asText("").trim();
                if (!text.isEmpty()) {
                    riskNotes.add(text);
                }
            }
        }

        return Optional.of(new LlmNarrative(summary, strategy, riskNotes));
    }

    /** Lightweight holder for the three narrative pieces we ask the LLM to rewrite. */
    public static class LlmNarrative {
        public final String executiveSummary;
        public final String recommendedMatchStrategy;
        public final List<String> riskNotes;

        public LlmNarrative(String executiveSummary, String recommendedMatchStrategy, List<String> riskNotes) {
            this.executiveSummary = executiveSummary;
            this.recommendedMatchStrategy = recommendedMatchStrategy;
            this.riskNotes = riskNotes.isEmpty() ? Collections.emptyList() : riskNotes;
        }
    }

    /**
     * Merge LLM text on top of the local analysis — stats and column insights stay from Java.
     */
    public AiAnalysis mergeWithLocal(AiAnalysis local, LlmNarrative narrative) {
        String summary = narrative.executiveSummary.isEmpty()
                ? local.getExecutiveSummary() : narrative.executiveSummary;
        String strategy = narrative.recommendedMatchStrategy.isEmpty()
                ? local.getRecommendedMatchStrategy() : narrative.recommendedMatchStrategy;
        LinkedHashSet<String> mergedRisks = new LinkedHashSet<>(local.getRiskNotes());
        mergedRisks.addAll(narrative.riskNotes);
        List<String> risks = new ArrayList<>(mergedRisks);
        if (risks.size() > 6) {
            risks = new ArrayList<>(risks.subList(0, 6));
        }

        return new AiAnalysis(
                true,
                "LLM-enhanced analysis",
                summary,
                strategy,
                risks,
                local.getColumnInsights(),
                local.getStats()
        );
    }
}
