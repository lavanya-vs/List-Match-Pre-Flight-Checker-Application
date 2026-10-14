package com.iqvia.preflight.service;

import com.iqvia.preflight.config.AiAnalysisProperties;
import com.iqvia.preflight.dto.AiAnalysis;
import com.iqvia.preflight.dto.ColumnInsight;
import com.iqvia.preflight.dto.DataQualityStats;
import com.iqvia.preflight.dto.PreflightResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the plain-English "analysis" panel after the file has been checked.
 *
 * Order of preference:
 * 1) optional external LLM can rephrase the same facts,
 * 2) otherwise Java fills in fixed templates ({@link LocalAnalysisService}).
 *
 * The real scoring always happens in the rules engine first — nothing here changes READY / NEEDS_WORK.
 */
@Service
public class AiAnalysisService {

    private final AiAnalysisProperties properties;
    private final DataQualityStatsService statsService;
    private final ColumnInsightService columnInsightService;
    private final LocalAnalysisService localAnalysisService;
    private final LlmClientService llmClientService;

    public AiAnalysisService(
            AiAnalysisProperties properties,
            DataQualityStatsService statsService,
            ColumnInsightService columnInsightService,
            LocalAnalysisService localAnalysisService,
            LlmClientService llmClientService
    ) {
        this.properties = properties;
        this.statsService = statsService;
        this.columnInsightService = columnInsightService;
        this.localAnalysisService = localAnalysisService;
        this.llmClientService = llmClientService;
    }

    /**
     * Run the full analysis pipeline on data we already parsed in memory.
     * The original file on disk or in the upload is never written to.
     */
    public AiAnalysis analyze(
            List<String> headers,
            List<Map<String, String>> rows,
            PreflightResult result
    ) {
        return analyze(headers, rows, result.getTotalRows(), result);
    }

    public AiAnalysis analyze(
            List<String> headers,
            List<Map<String, String>> rows,
            int totalRows,
            PreflightResult result
    ) {
        DataQualityStats stats = statsService.buildStats(rows, result.getDetectedColumns(), result);
        List<ColumnInsight> columnInsights = columnInsightService.analyzeColumns(
                headers, rows, totalRows, result.getDetectedColumns());

        AiAnalysis local = localAnalysisService.buildLocalAnalysis(result, stats, columnInsights);

        Optional<LlmClientService.LlmNarrative> llmNarrative = properties.isEnabled()
                ? llmClientService.enhanceNarrative(result, stats, columnInsights)
                : Optional.empty();

        if (llmNarrative.isPresent()) {
            return llmClientService.mergeWithLocal(local, llmNarrative.get());
        }

        return local;
    }
}
