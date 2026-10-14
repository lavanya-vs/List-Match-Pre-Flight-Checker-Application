package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.PreflightResult;
import com.iqvia.preflight.dto.PreflightResult.MatchTypeAssessment;
import com.iqvia.preflight.dto.PreflightResult.RowWarning;
import com.iqvia.preflight.model.MatchType;
import com.iqvia.preflight.model.ReadinessScore;
import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Scores validated values against every matching path IQVIATools can run.
 * READY requires a strong NPI or ZIP path; broad fallbacks never turn weak input green.
 */
@Service
public class RulesEngineService {

    private static final double READY_COVERAGE = 85.0;
    private static final double USABLE_COVERAGE = 40.0;
    private static final int MAX_DUPLICATE_WARNINGS = 50;
    private static final int MAX_ROW_WARNINGS = 200;

    private final FieldValidationService validator;

    public RulesEngineService(FieldValidationService validator) {
        this.validator = validator;
    }

    /**
     * Match paths in order of how precisely they identify a provider.
     * The engine picks whichever one the uploaded file actually supports best.
     */
    private static final List<Strategy> STRATEGIES = List.of(
            new Strategy(MatchType.NPI, "NPI matching", 100,
                    List.of(StandardField.NPI)),
            new Strategy(MatchType.NAME_ADDRESS, "Name + zip matching", 92,
                    List.of(StandardField.LAST_NAME, StandardField.ZIP)),
            new Strategy(MatchType.NAME_ADDRESS, "Name + full address matching", 86,
                    List.of(StandardField.LAST_NAME, StandardField.ADDRESS, StandardField.CITY, StandardField.STATE)),
            new Strategy(MatchType.NAME_ADDRESS, "Name + street/city matching", 80,
                    List.of(StandardField.LAST_NAME, StandardField.ADDRESS, StandardField.CITY)),
            new Strategy(MatchType.NAME_ADDRESS, "Name + city/state matching", 68,
                    List.of(StandardField.LAST_NAME, StandardField.CITY, StandardField.STATE)),
            new Strategy(MatchType.NAME_ONLY, "Name + state matching (broad)", 52,
                    List.of(StandardField.LAST_NAME, StandardField.STATE)),
            new Strategy(MatchType.NAME_ONLY, "Name-only matching (very broad)", 38,
                    List.of(StandardField.LAST_NAME, StandardField.FIRST_NAME))
    );

    public PreflightResult evaluate(
            String fileName,
            List<Map<String, String>> rows,
            Map<StandardField, String> detectedColumns,
            List<String> unmappedColumns
    ) {
        return evaluate(fileName, rows.size(), detectedColumns, unmappedColumns,
                consumer -> {
                    for (int i = 0; i < rows.size(); i++) {
                        consumer.accept(rows.get(i), i);
                    }
                });
    }

    public PreflightResult evaluate(
            String fileName,
            int totalRows,
            Map<StandardField, String> detectedColumns,
            List<String> unmappedColumns,
            RowSource rowSource
    ) {
        List<String> globalErrors = new ArrayList<>();

        if (totalRows == 0) {
            globalErrors.add("No data rows found in file.");
            return buildResult(fileName, 0, ReadinessScore.WILL_FAIL,
                    "File contains no data rows.", detectedColumns, unmappedColumns,
                    Collections.emptyList(), new ArrayList<>(), PreflightResult.WarningTotals.empty(),
                    globalErrors, 100.0);
        }

        // Same NPI on more than one row is flagged — we remember which numbers repeat.
        Set<String> duplicateNpis = findDuplicateNpis(rowSource, detectedColumns.get(StandardField.NPI));
        List<Scored> scored = scoreStrategies(rowSource, detectedColumns, duplicateNpis, totalRows);

        if (scored.isEmpty()) {
            globalErrors.add("No usable match path — the file needs an NPI column, or a last name plus at least a zip, address, city or state.");
            return buildResult(fileName, totalRows, ReadinessScore.WILL_FAIL,
                    String.format("Will fail — no matchable identifier found across ~%,d rows.", totalRows),
                    detectedColumns, unmappedColumns, Collections.emptyList(),
                    new ArrayList<>(), PreflightResult.WarningTotals.empty(), globalErrors, 100.0);
        }

        Plan plan = buildPlan(scored, totalRows, rowSource, detectedColumns, duplicateNpis);
        RowWarningScan scan =
                buildRowWarnings(rowSource, detectedColumns, plan.primary.strategy, duplicateNpis);
        List<MatchTypeAssessment> matchTypes = toAssessments(scored, plan);

        ReadinessScore score = determineScore(plan);
        if (score == ReadinessScore.WILL_FAIL) {
            globalErrors.add(String.format(
                    "No reliable match path reaches %.0f%% valid-row coverage.", USABLE_COVERAGE));
        }
        String summary = buildSummary(score, totalRows, plan);
        double difficulty = estimateDifficulty(score, plan, scan.totals.getRowsWithWarnings(), totalRows);

        return buildResult(fileName, totalRows, score, summary, detectedColumns, unmappedColumns,
                matchTypes, scan.samples, scan.totals, globalErrors, difficulty);
    }

    /**
     * Matching runs as a cascade: the strongest path first, then a second path to pick up the rows
     * it could not cover. Reporting both is what the file can realistically achieve.
     */
    private Plan buildPlan(
            List<Scored> scored,
            int totalRows,
            RowSource rowSource,
            Map<StandardField, String> detectedColumns,
            Set<String> duplicateNpis
    ) {
        Scored primary = scored.get(0);
        int combined = primary.coveredCount;

        Scored fallback = null;
        for (int i = 1; i < scored.size(); i++) {
            Scored candidate = scored.get(i);
            int exclusive = countExclusiveCoverage(
                    rowSource, primary.strategy, candidate.strategy, detectedColumns, duplicateNpis);
            if (exclusive * 100.0 / totalRows >= 2.0) {
                fallback = candidate;
                combined = primary.coveredCount + exclusive;
                break;
            }
        }

        return new Plan(primary, fallback, pct(combined, totalRows));
    }

    private int countExclusiveCoverage(
            RowSource rowSource,
            Strategy primary,
            Strategy candidate,
            Map<StandardField, String> detectedColumns,
            Set<String> duplicateNpis
    ) {
        int[] exclusive = {0};
        rowSource.forEach((row, index) -> {
            if (!rowMatchesStrategy(row, primary, detectedColumns, duplicateNpis)
                    && rowMatchesStrategy(row, candidate, detectedColumns, duplicateNpis)) {
                exclusive[0]++;
            }
        });
        return exclusive[0];
    }

    private List<Scored> scoreStrategies(
            RowSource rowSource,
            Map<StandardField, String> detectedColumns,
            Set<String> duplicateNpis,
            int totalRows
    ) {
        List<Strategy> applicable = new ArrayList<>();
        for (Strategy strategy : STRATEGIES) {
            if (hasAllColumns(strategy, detectedColumns)) {
                applicable.add(strategy);
            }
        }

        int[] coveredCounts = new int[applicable.size()];
        rowSource.forEach((row, index) -> {
            for (int i = 0; i < applicable.size(); i++) {
                if (rowMatchesStrategy(row, applicable.get(i), detectedColumns, duplicateNpis)) {
                    coveredCounts[i]++;
                }
            }
        });

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < applicable.size(); i++) {
            scored.add(new Scored(applicable.get(i), pct(coveredCounts[i], totalRows), coveredCounts[i]));
        }
        scored.sort(Comparator.comparingDouble(Scored::effectiveScore).reversed());
        return scored;
    }

    private boolean rowMatchesStrategy(
            Map<String, String> row,
            Strategy strategy,
            Map<StandardField, String> detectedColumns,
            Set<String> duplicateNpis
    ) {
        for (StandardField field : strategy.required) {
            String value = getValue(row, detectedColumns.get(field));
            if (!validator.isValid(field, value)
                    || (field == StandardField.NPI
                    && duplicateNpis.contains(validator.normalizeNpi(value)))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAllColumns(Strategy strategy, Map<StandardField, String> detectedColumns) {
        for (StandardField field : strategy.required) {
            if (detectedColumns.get(field) == null) {
                return false;
            }
        }
        return true;
    }

    private RowWarningScan buildRowWarnings(
            RowSource rowSource,
            Map<StandardField, String> detectedColumns,
            Strategy best,
            Set<String> duplicateNpis
    ) {
        List<RowWarning> rowWarnings = new ArrayList<>();
        Map<String, Integer> warningCounts = new LinkedHashMap<>();
        int[] rowsWithWarnings = {0};
        int[] rowsWithMissing = {0};
        int[] duplicateNpi = {0};
        int[] invalidNpi = {0};
        int[] missingNpi = {0};
        int[] missingZip = {0};
        int[] missingLastName = {0};
        int[] duplicateWarningsInSample = {0};

        String npiCol = detectedColumns.get(StandardField.NPI);

        rowSource.forEach((row, i) -> {
            int rowNum = i + 2;
            List<String> warnings = new ArrayList<>();

            for (StandardField field : best.required) {
                String value = getValue(row, detectedColumns.get(field));
                if (isBlank(value)) {
                    warnings.add("Missing " + labelFor(field));
                } else if (!validator.isValid(field, value)) {
                    warnings.add(validator.validationMessage(field));
                }
            }

            if (npiCol != null && !best.required.contains(StandardField.NPI)) {
                String npi = getValue(row, npiCol);
                if (!isBlank(npi) && !validator.isValidNpi(npi)) {
                    warnings.add(validator.validationMessage(StandardField.NPI));
                }
            }

            if (npiCol != null) {
                String npi = getValue(row, npiCol);
                if (!isBlank(npi) && duplicateNpis.contains(validator.normalizeNpi(npi))) {
                    warnings.add("Duplicate NPI: " + validator.normalizeNpi(npi));
                }
            }

            if (warnings.isEmpty()) {
                return;
            }

            tallyWarningTotals(
                    warnings,
                    warningCounts,
                    rowsWithWarnings,
                    rowsWithMissing,
                    duplicateNpi,
                    invalidNpi,
                    missingNpi,
                    missingZip,
                    missingLastName
            );

            if (rowWarnings.size() < MAX_ROW_WARNINGS) {
                List<String> sampleWarnings = new ArrayList<>();
                for (String warning : warnings) {
                    if (warning.startsWith("Duplicate NPI")
                            && duplicateWarningsInSample[0] >= MAX_DUPLICATE_WARNINGS) {
                        continue;
                    }
                    sampleWarnings.add(warning);
                    if (warning.startsWith("Duplicate NPI")) {
                        duplicateWarningsInSample[0]++;
                    }
                }
                if (!sampleWarnings.isEmpty()) {
                    rowWarnings.add(new RowWarning(rowNum, sampleWarnings));
                }
            }
        });

        boolean truncated = rowsWithWarnings[0] > rowWarnings.size();
        PreflightResult.WarningTotals totals = new PreflightResult.WarningTotals(
                rowsWithWarnings[0],
                rowsWithMissing[0],
                warningCounts,
                duplicateNpi[0],
                invalidNpi[0],
                missingNpi[0],
                missingZip[0],
                missingLastName[0],
                truncated
        );
        return new RowWarningScan(rowWarnings, totals);
    }

    private void tallyWarningTotals(
            List<String> warnings,
            Map<String, Integer> warningCounts,
            int[] rowsWithWarnings,
            int[] rowsWithMissing,
            int[] duplicateNpi,
            int[] invalidNpi,
            int[] missingNpi,
            int[] missingZip,
            int[] missingLastName
    ) {
        rowsWithWarnings[0]++;
        boolean hasMissing = false;
        for (String warning : warnings) {
            String key = normalizeWarningKey(warning);
            warningCounts.merge(key, 1, Integer::sum);
            if (key.startsWith("Duplicate NPI")) {
                duplicateNpi[0]++;
            } else if (key.startsWith("Invalid NPI")) {
                invalidNpi[0]++;
            } else if ("Missing NPI".equals(key)) {
                missingNpi[0]++;
                hasMissing = true;
            } else if ("Missing zip".equals(key)) {
                missingZip[0]++;
                hasMissing = true;
            } else if ("Missing last name".equals(key)) {
                missingLastName[0]++;
                hasMissing = true;
            } else if (warning.startsWith("Missing")) {
                hasMissing = true;
            }
        }
        if (hasMissing) {
            rowsWithMissing[0]++;
        }
    }

    private String normalizeWarningKey(String warning) {
        if (warning.startsWith("Duplicate NPI")) {
            return "Duplicate NPI";
        }
        return warning;
    }

    /**
     * Walk every row once: first time we see a valid NPI we remember it;
     * second time the same number appears, we mark it as a duplicate.
     */
    private Set<String> findDuplicateNpis(RowSource rowSource, String npiColumn) {
        if (npiColumn == null) {
            return Collections.emptySet();
        }

        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        rowSource.forEach((row, index) -> {
            String npi = getValue(row, npiColumn);
            if (!validator.isValidNpi(npi)) {
                return;
            }
            String normalized = validator.normalizeNpi(npi);
            if (!seen.add(normalized)) {
                duplicates.add(normalized);
            }
        });
        return duplicates;
    }

    /**
     * Report the paths that make up the plan plus the next best alternative — more than that is noise.
     */
    private List<MatchTypeAssessment> toAssessments(List<Scored> scored, Plan plan) {
        List<MatchTypeAssessment> assessments = new ArrayList<>();
        for (Scored item : scored) {
            if (assessments.size() >= 3 && item != plan.fallback) {
                break;
            }
            assessments.add(new MatchTypeAssessment(
                    item.strategy.type,
                    item.strategy.label,
                    item.coverage >= USABLE_COVERAGE,
                    item.coverage,
                    describe(item, plan)
            ));
        }
        return assessments;
    }

    private ReadinessScore determineScore(Plan plan) {
        if (plan.primary.coverage < USABLE_COVERAGE) {
            return ReadinessScore.WILL_FAIL;
        }
        if (plan.primary.coverage >= READY_COVERAGE && plan.primary.strategy.hasStrongIdentifier()) {
            return ReadinessScore.READY;
        }
        return ReadinessScore.NEEDS_WORK;
    }

    private String buildSummary(ReadinessScore score, int totalRows, Plan plan) {
        String primary = plan.primary.strategy.label;
        if (score == ReadinessScore.READY) {
            if (plan.fallback != null) {
                return String.format("OK for %s with %s as backup — ~%,d rows.",
                        primary, plan.fallback.strategy.label, totalRows);
            }
            return String.format("OK for %s — ~%,d rows.", primary, totalRows);
        }
        if (score == ReadinessScore.NEEDS_WORK) {
            if (!plan.primary.strategy.hasStrongIdentifier()) {
                return String.format(
                        "Needs review — %s is available across ~%,d rows, but no strong NPI or zip path is ready.",
                        primary, totalRows);
            }
            return String.format(
                    "Needs work — the strongest reliable path covers only %.0f%% of ~%,d rows.",
                    plan.primary.coverage, totalRows);
        }
        return String.format("Will fail — insufficient data across ~%,d rows.", totalRows);
    }

    private double estimateDifficulty(ReadinessScore score, Plan plan, int warningCount, int totalRows) {
        double base;
        switch (score) {
            case READY:
                base = 25;
                break;
            case NEEDS_WORK:
                base = 55;
                break;
            default:
                base = 90;
                break;
        }
        double coveragePenalty = (100 - plan.combinedCoverage) * 0.3;
        double precisionPenalty = (100 - plan.primary.strategy.precision) * 0.2;
        double rowWarningPenalty = totalRows > 0 ? ((double) warningCount / totalRows) * 20 : 0;
        return Math.min(100, Math.round(base + coveragePenalty + precisionPenalty + rowWarningPenalty));
    }

    /** The lead path carries the full recommendation; the rest just report their own reach. */
    private String describe(Scored item, Plan plan) {
        String label = item.strategy.label;

        if (item == plan.primary) {
            if (plan.fallback != null) {
                return String.format(
                        "Use %s for the %.0f%% of rows it reliably covers; %s may recover additional rows after cleanup. Readiness remains based on the primary path.",
                        label, item.coverage, plan.fallback.strategy.label);
            }
            if (item.coverage >= READY_COVERAGE) {
                return String.format("Use %s — %.0f%% of rows have valid data for it.", label, item.coverage);
            }
            return String.format(
                    "%s is the strongest path available, but only %.0f%% of rows are complete — treat results as partial.",
                    item.strategy.label, item.coverage);
        }

        if (item == plan.fallback) {
            return String.format("Backup path — covers %.0f%% of rows, including some the lead path misses.", item.coverage);
        }
        if (item.coverage < USABLE_COVERAGE) {
            return String.format("Insufficient coverage for %s (%.0f%%).", label, item.coverage);
        }
        if (!item.strategy.hasStrongIdentifier()) {
            return String.format(
                    "Covers %.0f%% of rows, but identifies providers less precisely than NPI or zip.", item.coverage);
        }
        return String.format("Covers %.0f%% of rows.", item.coverage);
    }

    private String labelFor(StandardField field) {
        switch (field) {
            case FIRST_NAME: return "first name";
            case LAST_NAME: return "last name";
            case NPI: return "NPI";
            case ZIP: return "zip";
            case ADDRESS: return "address";
            case CITY: return "city";
            case STATE: return "state";
            case EMAIL: return "email";
            case PHONE: return "phone";
            default: return field.name().toLowerCase();
        }
    }

    private String getValue(Map<String, String> row, String column) {
        if (column == null) return "";
        return row.getOrDefault(column, "").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private double pct(int count, int total) {
        return total == 0 ? 0 : Math.round((count * 1000.0 / total)) / 10.0;
    }

    private PreflightResult buildResult(
            String fileName, int totalRows, ReadinessScore score, String summary,
            Map<StandardField, String> detectedColumns, List<String> unmappedColumns,
            List<MatchTypeAssessment> matchTypes, List<RowWarning> rowWarnings,
            PreflightResult.WarningTotals warningTotals,
            List<String> globalErrors, double difficulty
    ) {
        rowWarnings.sort(Comparator.comparingInt(RowWarning::getRowNumber));
        return new PreflightResult(
                fileName, totalRows, score, summary,
                detectedColumns, unmappedColumns, matchTypes,
                rowWarnings, warningTotals, globalErrors, difficulty
        );
    }

    private static final class RowWarningScan {
        final List<RowWarning> samples;
        final PreflightResult.WarningTotals totals;

        RowWarningScan(List<RowWarning> samples, PreflightResult.WarningTotals totals) {
            this.samples = samples;
            this.totals = totals;
        }
    }

    /** One matching path and the fields it needs. */
    private static final class Strategy {
        final MatchType type;
        final String label;
        final int precision;
        final List<StandardField> required;

        Strategy(MatchType type, String label, int precision, List<StandardField> required) {
            this.type = type;
            this.label = label;
            this.precision = precision;
            this.required = required;
        }

        boolean hasStrongIdentifier() {
            return required.contains(StandardField.NPI) || required.contains(StandardField.ZIP);
        }
    }

    /** A strategy paired with the rows of this file it can actually match. */
    private static final class Scored {
        final Strategy strategy;
        final double coverage;
        final int coveredCount;

        Scored(Strategy strategy, double coverage, int coveredCount) {
            this.strategy = strategy;
            this.coverage = coverage;
            this.coveredCount = coveredCount;
        }

        /** Precision dominates; coverage breaks ties between otherwise comparable paths. */
        double effectiveScore() {
            return strategy.precision * 0.7 + coverage * 0.3;
        }
    }

    /** The lead path, an optional second path for the rows it misses, and their combined reach. */
    private static final class Plan {
        final Scored primary;
        final Scored fallback;
        final double combinedCoverage;

        Plan(Scored primary, Scored fallback, double combinedCoverage) {
            this.primary = primary;
            this.fallback = fallback;
            this.combinedCoverage = combinedCoverage;
        }
    }
}
