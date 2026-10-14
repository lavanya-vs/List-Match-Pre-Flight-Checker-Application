package com.iqvia.preflight.dto;



import com.iqvia.preflight.model.MatchType;

import com.iqvia.preflight.model.ReadinessScore;

import com.iqvia.preflight.model.StandardField;



import java.util.ArrayList;

import java.util.List;

import java.util.Map;



public class PreflightResult {



    private final String fileName;

    private final int totalRows;

    private final ReadinessScore score;

    private final String summary;

    private final Map<StandardField, String> detectedColumns;

    private final List<String> unmappedColumns;

    private final List<MatchTypeAssessment> matchTypes;

    private final List<RowWarning> rowWarnings;

    private final WarningTotals warningTotals;

    private final List<String> globalErrors;

    private final double estimatedMatchDifficulty;

    private final List<AdExportColumnClash> adExportClashes;

    private final List<ColumnMappingEntry> columnMappings;

    private final List<ClientListHeaderPreview> simulatedClientListHeaders;



    public PreflightResult(

            String fileName,

            int totalRows,

            ReadinessScore score,

            String summary,

            Map<StandardField, String> detectedColumns,

            List<String> unmappedColumns,

            List<MatchTypeAssessment> matchTypes,

            List<RowWarning> rowWarnings,

            WarningTotals warningTotals,

            List<String> globalErrors,

            double estimatedMatchDifficulty

    ) {

        this(fileName, totalRows, score, summary, detectedColumns, unmappedColumns, matchTypes,

                rowWarnings, warningTotals, globalErrors, estimatedMatchDifficulty,

                List.of(), List.of(), List.of());

    }



    public PreflightResult(

            String fileName,

            int totalRows,

            ReadinessScore score,

            String summary,

            Map<StandardField, String> detectedColumns,

            List<String> unmappedColumns,

            List<MatchTypeAssessment> matchTypes,

            List<RowWarning> rowWarnings,

            WarningTotals warningTotals,

            List<String> globalErrors,

            double estimatedMatchDifficulty,

            List<AdExportColumnClash> adExportClashes,

            List<ColumnMappingEntry> columnMappings,

            List<ClientListHeaderPreview> simulatedClientListHeaders

    ) {

        this.fileName = fileName;

        this.totalRows = totalRows;

        this.score = score;

        this.summary = summary;

        this.detectedColumns = detectedColumns;

        this.unmappedColumns = unmappedColumns;

        this.matchTypes = matchTypes;

        this.rowWarnings = rowWarnings;

        this.warningTotals = warningTotals != null ? warningTotals : WarningTotals.empty();

        this.globalErrors = globalErrors;

        this.estimatedMatchDifficulty = estimatedMatchDifficulty;

        this.adExportClashes = adExportClashes != null ? adExportClashes : List.of();

        this.columnMappings = columnMappings != null ? columnMappings : List.of();

        this.simulatedClientListHeaders = simulatedClientListHeaders != null ? simulatedClientListHeaders : List.of();

    }



    public PreflightResult withAdExportQc(

            List<ColumnMappingEntry> mappings,

            List<ClientListHeaderPreview> simulatedHeaders,

            List<AdExportColumnClash> clashes

    ) {

        List<String> errors = new ArrayList<>(globalErrors);

        for (AdExportColumnClash clash : clashes) {

            errors.add(String.format(

                    "AD export column clash: %s would be duplicated after export (fix mapping before IQVIATools load).",

                    clash.getAdExportName()));

        }

        ReadinessScore newScore = score;

        String newSummary = summary;

        if (!clashes.isEmpty()) {

            newScore = ReadinessScore.NEEDS_WORK;

            newSummary = String.format(

                    "Needs work — %d AD export column name clash(es) detected. Resolve mapping before loading into IQVIATools.",

                    clashes.size());

        }

        return new PreflightResult(

                fileName, totalRows, newScore, newSummary, detectedColumns, unmappedColumns, matchTypes,

                rowWarnings, warningTotals, errors, estimatedMatchDifficulty,

                clashes, mappings, simulatedHeaders

        );

    }



    public String getFileName() { return fileName; }

    public int getTotalRows() { return totalRows; }

    public ReadinessScore getScore() { return score; }

    public String getSummary() { return summary; }

    public Map<StandardField, String> getDetectedColumns() { return detectedColumns; }

    public List<String> getUnmappedColumns() { return unmappedColumns; }

    public List<MatchTypeAssessment> getMatchTypes() { return matchTypes; }

    public List<RowWarning> getRowWarnings() { return rowWarnings; }

    public WarningTotals getWarningTotals() { return warningTotals; }

    public List<String> getGlobalErrors() { return globalErrors; }

    public double getEstimatedMatchDifficulty() { return estimatedMatchDifficulty; }

    public List<AdExportColumnClash> getAdExportClashes() { return adExportClashes; }

    public List<ColumnMappingEntry> getColumnMappings() { return columnMappings; }

    public List<ClientListHeaderPreview> getSimulatedClientListHeaders() { return simulatedClientListHeaders; }



    public static class MatchTypeAssessment {

        private final MatchType type;

        private final String label;

        private final boolean viable;

        private final double coveragePercent;

        private final String message;



        public MatchTypeAssessment(MatchType type, String label, boolean viable, double coveragePercent, String message) {

            this.type = type;

            this.label = label;

            this.viable = viable;

            this.coveragePercent = coveragePercent;

            this.message = message;

        }



        public MatchType getType() { return type; }

        public String getLabel() { return label; }

        public boolean isViable() { return viable; }

        public double getCoveragePercent() { return coveragePercent; }

        public String getMessage() { return message; }

    }



    public static class RowWarning {

        private final int rowNumber;

        private final List<String> warnings;



        public RowWarning(int rowNumber, List<String> warnings) {

            this.rowNumber = rowNumber;

            this.warnings = warnings;

        }



        public int getRowNumber() { return rowNumber; }

        public List<String> getWarnings() { return warnings; }

    }



    public static class WarningTotals {

        private final int rowsWithWarnings;

        private final int rowsWithMissing;

        private final Map<String, Integer> warningCounts;

        private final int duplicateNpiCount;

        private final int invalidNpiCount;

        private final int missingNpiCount;

        private final int missingZipCount;

        private final int missingLastNameCount;

        private final boolean sampleWarningsTruncated;



        public WarningTotals(

                int rowsWithWarnings,

                int rowsWithMissing,

                Map<String, Integer> warningCounts,

                int duplicateNpiCount,

                int invalidNpiCount,

                int missingNpiCount,

                int missingZipCount,

                int missingLastNameCount,

                boolean sampleWarningsTruncated

        ) {

            this.rowsWithWarnings = rowsWithWarnings;

            this.rowsWithMissing = rowsWithMissing;

            this.warningCounts = warningCounts;

            this.duplicateNpiCount = duplicateNpiCount;

            this.invalidNpiCount = invalidNpiCount;

            this.missingNpiCount = missingNpiCount;

            this.missingZipCount = missingZipCount;

            this.missingLastNameCount = missingLastNameCount;

            this.sampleWarningsTruncated = sampleWarningsTruncated;

        }



        public static WarningTotals empty() {

            return new WarningTotals(0, 0, Map.of(), 0, 0, 0, 0, 0, false);

        }



        public int getRowsWithWarnings() { return rowsWithWarnings; }

        public int getRowsWithMissing() { return rowsWithMissing; }

        public Map<String, Integer> getWarningCounts() { return warningCounts; }

        public int getDuplicateNpiCount() { return duplicateNpiCount; }

        public int getInvalidNpiCount() { return invalidNpiCount; }

        public int getMissingNpiCount() { return missingNpiCount; }

        public int getMissingZipCount() { return missingZipCount; }

        public int getMissingLastNameCount() { return missingLastNameCount; }

        public boolean isSampleWarningsTruncated() { return sampleWarningsTruncated; }

    }

}


