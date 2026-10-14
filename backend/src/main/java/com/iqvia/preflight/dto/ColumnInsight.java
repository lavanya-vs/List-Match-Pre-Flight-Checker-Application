package com.iqvia.preflight.dto;

/**
 * A read-only guess about what one column in the file probably represents.
 * Built dynamically from whatever headers the uploaded CSV or Excel file contains.
 */
public class ColumnInsight {

    /** The exact header name from the client's file. */
    private final String headerName;

    /** Human label, e.g. "NPI", "Custom column", or "Zip". */
    private final String suggestedField;

    /** How sure we are about the meaning, from 0 to 100. */
    private final int confidencePercent;

    /** Plain-English reason for this prediction. */
    private final String reason;

    /** True when our standard column detector already mapped this header. */
    private final boolean alreadyMapped;

    /** True when this column is used for IQVIATools list matching. */
    private final boolean usedForMatching;

    /** Percent of rows where this column is blank or missing. */
    private final double missingPercent;

    /** How many rows have a non-empty value in this column. */
    private final int filledRows;

    /** Total rows analyzed in the file. */
    private final int totalRows;

    /** A short preview of sample values from this column (read-only). */
    private final String samplePreview;

    public ColumnInsight(
            String headerName,
            String suggestedField,
            int confidencePercent,
            String reason,
            boolean alreadyMapped,
            boolean usedForMatching,
            double missingPercent,
            int filledRows,
            int totalRows,
            String samplePreview
    ) {
        this.headerName = headerName;
        this.suggestedField = suggestedField;
        this.confidencePercent = confidencePercent;
        this.reason = reason;
        this.alreadyMapped = alreadyMapped;
        this.usedForMatching = usedForMatching;
        this.missingPercent = missingPercent;
        this.filledRows = filledRows;
        this.totalRows = totalRows;
        this.samplePreview = samplePreview;
    }

    public String getHeaderName() { return headerName; }
    public String getSuggestedField() { return suggestedField; }
    public int getConfidencePercent() { return confidencePercent; }
    public String getReason() { return reason; }
    public boolean isAlreadyMapped() { return alreadyMapped; }
    public boolean isUsedForMatching() { return usedForMatching; }
    public double getMissingPercent() { return missingPercent; }
    public int getFilledRows() { return filledRows; }
    public int getTotalRows() { return totalRows; }
    public String getSamplePreview() { return samplePreview; }
}
