package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.AdExportColumnClash;
import com.iqvia.preflight.dto.AiAnalysis;
import com.iqvia.preflight.dto.ColumnInsight;
import com.iqvia.preflight.dto.DataQualityStats;
import com.iqvia.preflight.dto.PreflightResult;
import com.iqvia.preflight.model.ReadinessScore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default "analysis" writer when an optional LLM is not used.
 * Fills in sentence templates from counts the rules engine already produced.
 */
@Service
public class LocalAnalysisService {

    /**
     * Turn the check result and our computed stats into plain-English analysis text.
     */
    public AiAnalysis buildLocalAnalysis(
            PreflightResult result,
            DataQualityStats stats,
            List<ColumnInsight> columnInsights
    ) {
        String executiveSummary = buildExecutiveSummary(result, stats);
        String strategy = buildMatchStrategy(result);
        List<String> riskNotes = buildRiskNotes(result, stats, columnInsights);

        return new AiAnalysis(
                false,
                "Java rules and built-in summary templates",
                executiveSummary,
                strategy,
                riskNotes,
                columnInsights,
                stats
        );
    }

    /** A short story about the file for someone who will not read the technical table. */
    private String buildExecutiveSummary(PreflightResult result, DataQualityStats stats) {
        String fileName = result.getFileName();
        int rows = stats.getTotalRows();
        ReadinessScore score = result.getScore();

        if (score == ReadinessScore.READY) {
            return String.format(
                    "%s contains %,d rows and looks ready for IQVIATools list matching. "
                            + "Required fields are present on most rows, so matching should run smoothly with few surprises.",
                    fileName, rows);
        }
        if (score == ReadinessScore.NEEDS_WORK) {
            Optional<PreflightResult.MatchTypeAssessment> best = bestMatchType(result);
            if (best.isPresent() && best.get().getCoveragePercent() >= 85) {
                return String.format(
                        "%s contains %,d rows and can be matched using %s, which covers about %.0f%% of rows. "
                                + "This path works without NPI or zip, but it identifies providers more broadly, so expect more candidates to review.",
                        fileName, rows, best.get().getLabel(), best.get().getCoveragePercent());
            }
            return String.format(
                    "%s has %,d rows but only part of the file is match-ready. "
                            + "About %.0f%% of rows have data gaps — matching may run, but many providers could be missed unless the source data improves.",
                    fileName, rows, stats.getWarningRowPercent());
        }
        if (!result.getGlobalErrors().isEmpty()) {
            return String.format(
                    "%s cannot be matched yet because required columns or values are missing at the file level. "
                            + "The structure needs attention before IQVIATools can use this list.",
                    fileName);
        }
        return String.format(
                "%s has %,d rows but too many are incomplete for reliable matching. "
                        + "The file should be reviewed with the client before sending it to IQVIATools.",
                fileName, rows);
    }

    /** Name the strongest path this file supports and how far it reaches. */
    private String buildMatchStrategy(PreflightResult result) {
        if (!result.getGlobalErrors().isEmpty()) {
            return "No match path is possible yet — the file needs an NPI column, or a last name plus at least a zip, address, city or state.";
        }

        Optional<PreflightResult.MatchTypeAssessment> best = bestMatchType(result);

        if (!best.isPresent()) {
            return "No match path has enough complete rows to rely on.";
        }

        // The rules engine writes the full recommendation, including any backup path, on the lead entry.
        return best.get().getMessage();
    }

    /** The path the engine ranked first is always the one we report on. */
    private Optional<PreflightResult.MatchTypeAssessment> bestMatchType(PreflightResult result) {
        return result.getMatchTypes().stream()
                .filter(PreflightResult.MatchTypeAssessment::isViable)
                .findFirst();
    }

    /** NPI and zip pin a provider down; anything else widens the candidate pool. */
    private boolean usesStrongIdentifier(PreflightResult.MatchTypeAssessment assessment) {
        String label = assessment.getLabel().toLowerCase();
        return label.contains("npi") || label.contains("zip");
    }

    /** Collect the most important risks as short bullet-style sentences. */
    private List<String> buildRiskNotes(
            PreflightResult result,
            DataQualityStats stats,
            List<ColumnInsight> columnInsights
    ) {
        List<String> notes = new ArrayList<>();

        for (String error : result.getGlobalErrors()) {
            notes.add("Blocking issue: " + error);
        }

        for (AdExportColumnClash clash : result.getAdExportClashes()) {
            notes.add(formatAdExportClash(clash));
        }

        Optional<PreflightResult.MatchTypeAssessment> best = bestMatchType(result);
        if (best.isPresent() && !usesStrongIdentifier(best.get())) {
            notes.add(String.format(
                    "No NPI or zip column was found, so matching relies on %s — expect broader candidate sets and more review.",
                    best.get().getLabel()));
        }

        stats.getPatternFlags().forEach(notes::add);

        if (stats.getDuplicateNpiCount() > 0) {
            notes.add(String.format(
                    "Duplicate NPI values appear on %d row(s) — the same provider may be listed more than once.",
                    stats.getDuplicateNpiCount()));
        }
        if (stats.getInvalidNpiCount() > 0) {
            notes.add(String.format(
                    "%d row(s) have NPI values that are not valid 10-digit numbers.",
                    stats.getInvalidNpiCount()));
        }
        if (stats.getMissingNpiCount() > 0 && result.getDetectedColumns().containsKey(
                com.iqvia.preflight.model.StandardField.NPI)) {
            notes.add(String.format("%d row(s) are missing an NPI.", stats.getMissingNpiCount()));
        }
        if (stats.getMissingZipCount() > 0) {
            notes.add(String.format("%d row(s) are missing a zip code for name+zip matching.", stats.getMissingZipCount()));
        }
        if (stats.getMissingLastNameCount() > 0) {
            notes.add(String.format("%d row(s) are missing a last name.", stats.getMissingLastNameCount()));
        }

        long lowConfidenceUnmapped = columnInsights.stream()
                .filter(c -> !c.isAlreadyMapped())
                .filter(c -> c.getConfidencePercent() >= 50 && !"Unmapped".equals(c.getSuggestedField()))
                .count();
        if (lowConfidenceUnmapped > 0) {
            notes.add(String.format(
                    "%d column(s) may contain match fields under non-standard names — review column mapping before matching.",
                    lowConfidenceUnmapped));
        }

        if (result.getEstimatedMatchDifficulty() >= 70) {
            if (result.getScore() == ReadinessScore.WILL_FAIL) {
                notes.add(String.format(
                        "Estimated match difficulty is %.0f%% — source data must be corrected before matching.",
                        result.getEstimatedMatchDifficulty()));
            } else {
                notes.add(String.format(
                        "Estimated match difficulty is high (%.0f%%) — expect heavy manual follow-up after matching.",
                        result.getEstimatedMatchDifficulty()));
            }
        }

        if (notes.isEmpty()) {
            notes.add("No major data-quality risks stood out beyond the standard checks.");
        }

        return notes;
    }

    private String formatAdExportClash(AdExportColumnClash clash) {
        String cols = clash.getColumns().stream()
                .map(c -> c.getHeaderName() + " (" + (c.isMapped() ? "mapped" : "extra") + ", DB: "
                        + c.getDbColumnName() + ")")
                .collect(java.util.stream.Collectors.joining("; "));
        return "AD export naming clash: " + clash.getAdExportName() + " would be duplicated — " + cols
                + ". Review column mapping before loading into IQVIATools.";
    }
}
