package com.iqvia.preflight.dto;

/**
 * Everything the UI needs after one upload:
 * the rule-based check result plus the read-only analysis layer.
 */
public class PreflightCheckResponse {

    private final PreflightResult check;
    private final AiAnalysis analysis;

    public PreflightCheckResponse(PreflightResult check, AiAnalysis analysis) {
        this.check = check;
        this.analysis = analysis;
    }

    public PreflightResult getCheck() {
        return check;
    }

    public AiAnalysis getAnalysis() {
        return analysis;
    }
}
