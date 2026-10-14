package com.iqvia.preflight.dto;

import java.util.List;

/**
 * The full read-only analysis report shown after a file check.
 * The uploaded file is never modified — this is explanation only.
 */
public class AiAnalysis {

    /** True when an external LLM wrote or polished the narrative sections. */
    private final boolean aiEnhanced;

    /** Short label for the UI, e.g. "Smart analysis" or "AI-enhanced analysis". */
    private final String analysisSource;

    /** Two or three sentences a human can read at a glance. */
    private final String executiveSummary;

    /** Which match path looks strongest for this file. */
    private final String recommendedMatchStrategy;

    /** Things to be aware of before sending the file to IQVIATools. */
    private final List<String> riskNotes;

    /** What we think each column probably means. */
    private final List<ColumnInsight> columnInsights;

    /** Counts and pattern flags from the data itself. */
    private final DataQualityStats stats;

    public AiAnalysis(
            boolean aiEnhanced,
            String analysisSource,
            String executiveSummary,
            String recommendedMatchStrategy,
            List<String> riskNotes,
            List<ColumnInsight> columnInsights,
            DataQualityStats stats
    ) {
        this.aiEnhanced = aiEnhanced;
        this.analysisSource = analysisSource;
        this.executiveSummary = executiveSummary;
        this.recommendedMatchStrategy = recommendedMatchStrategy;
        this.riskNotes = riskNotes;
        this.columnInsights = columnInsights;
        this.stats = stats;
    }

    public boolean isAiEnhanced() {
        return aiEnhanced;
    }

    public String getAnalysisSource() {
        return analysisSource;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public String getRecommendedMatchStrategy() {
        return recommendedMatchStrategy;
    }

    public List<String> getRiskNotes() {
        return riskNotes;
    }

    public List<ColumnInsight> getColumnInsights() {
        return columnInsights;
    }

    public DataQualityStats getStats() {
        return stats;
    }
}
