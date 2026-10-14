package com.iqvia.preflight.dto;

import java.util.List;
import java.util.Map;

/**
 * Hard numbers about the file — computed in Java, not guessed by AI.
 * These stats feed both the local analysis and the optional LLM prompt.
 */
public class DataQualityStats {

    private final int totalRows;
    private final int rowsWithWarnings;
    private final double warningRowPercent;
    private final Map<String, Integer> warningCounts;
    private final int duplicateNpiCount;
    private final int invalidNpiCount;
    private final int missingNpiCount;
    private final int missingZipCount;
    private final int missingLastNameCount;
    /** Share of rows missing at least one required value (NPI, zip, or last name). */
    private final double missingRowPercent;
    private final double missingNpiPercent;
    private final double missingZipPercent;
    private final double missingLastNamePercent;
    private final List<String> patternFlags;

    public DataQualityStats(
            int totalRows,
            int rowsWithWarnings,
            double warningRowPercent,
            Map<String, Integer> warningCounts,
            int duplicateNpiCount,
            int invalidNpiCount,
            int missingNpiCount,
            int missingZipCount,
            int missingLastNameCount,
            double missingRowPercent,
            double missingNpiPercent,
            double missingZipPercent,
            double missingLastNamePercent,
            List<String> patternFlags
    ) {
        this.totalRows = totalRows;
        this.rowsWithWarnings = rowsWithWarnings;
        this.warningRowPercent = warningRowPercent;
        this.warningCounts = warningCounts;
        this.duplicateNpiCount = duplicateNpiCount;
        this.invalidNpiCount = invalidNpiCount;
        this.missingNpiCount = missingNpiCount;
        this.missingZipCount = missingZipCount;
        this.missingLastNameCount = missingLastNameCount;
        this.missingRowPercent = missingRowPercent;
        this.missingNpiPercent = missingNpiPercent;
        this.missingZipPercent = missingZipPercent;
        this.missingLastNamePercent = missingLastNamePercent;
        this.patternFlags = patternFlags;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getRowsWithWarnings() {
        return rowsWithWarnings;
    }

    public double getWarningRowPercent() {
        return warningRowPercent;
    }

    public Map<String, Integer> getWarningCounts() {
        return warningCounts;
    }

    public int getDuplicateNpiCount() {
        return duplicateNpiCount;
    }

    public int getInvalidNpiCount() {
        return invalidNpiCount;
    }

    public int getMissingNpiCount() {
        return missingNpiCount;
    }

    public int getMissingZipCount() {
        return missingZipCount;
    }

    public int getMissingLastNameCount() {
        return missingLastNameCount;
    }

    public double getMissingRowPercent() {
        return missingRowPercent;
    }

    public double getMissingNpiPercent() {
        return missingNpiPercent;
    }

    public double getMissingZipPercent() {
        return missingZipPercent;
    }

    public double getMissingLastNamePercent() {
        return missingLastNamePercent;
    }

    public List<String> getPatternFlags() {
        return patternFlags;
    }
}
