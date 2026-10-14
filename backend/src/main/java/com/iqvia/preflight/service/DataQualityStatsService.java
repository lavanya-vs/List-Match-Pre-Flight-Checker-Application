package com.iqvia.preflight.service;



import com.iqvia.preflight.dto.DataQualityStats;

import com.iqvia.preflight.dto.PreflightResult;

import com.iqvia.preflight.model.StandardField;

import org.springframework.stereotype.Service;



import java.util.*;



/**

 * Counts concrete facts about the file — blanks, duplicates, warning types.

 * All of this is math on the data; no AI guesses here.

 */

@Service

public class DataQualityStatsService {



    private final FieldValidationService validator;



    public DataQualityStatsService(FieldValidationService validator) {

        this.validator = validator;

    }



    /**

     * Build stats from full-file warning totals (streaming scan) and sample rows for pattern hints.

     */

    public DataQualityStats buildStats(

            List<Map<String, String>> rows,

            Map<StandardField, String> detectedColumns,

            PreflightResult result

    ) {

        int totalRows = result.getTotalRows();

        PreflightResult.WarningTotals totals = result.getWarningTotals();



        int rowsWithWarnings = totals.getRowsWithWarnings();

        double warningRowPercent = pct(rowsWithWarnings, totalRows);



        int rowsWithMissing = totals.getRowsWithMissing();

        double missingRowPercent = pct(rowsWithMissing, totalRows);

        double missingNpiPercent = pct(totals.getMissingNpiCount(), totalRows);

        double missingZipPercent = pct(totals.getMissingZipCount(), totalRows);

        double missingLastNamePercent = pct(totals.getMissingLastNameCount(), totalRows);



        List<String> patternFlags = detectPatterns(rows, detectedColumns, result, warningRowPercent);

        if (totals.isSampleWarningsTruncated()) {

            patternFlags = new ArrayList<>(patternFlags);

            patternFlags.add("Row-level warning list shows the first "

                    + result.getRowWarnings().size() + " affected rows; counts above reflect the full file.");

        }



        return new DataQualityStats(

                totalRows,

                rowsWithWarnings,

                warningRowPercent,

                totals.getWarningCounts(),

                totals.getDuplicateNpiCount(),

                totals.getInvalidNpiCount(),

                totals.getMissingNpiCount(),

                totals.getMissingZipCount(),

                totals.getMissingLastNameCount(),

                missingRowPercent,

                missingNpiPercent,

                missingZipPercent,

                missingLastNamePercent,

                patternFlags

        );

    }



    private double pct(int count, int total) {

        return total == 0 ? 0 : Math.round(count * 1000.0 / total) / 10.0;

    }



    /**

     * Spot file-wide patterns that a single row warning might not capture on its own.

     */

    private List<String> detectPatterns(

            List<Map<String, String>> rows,

            Map<StandardField, String> detectedColumns,

            PreflightResult result,

            double warningRowPercent

    ) {

        List<String> flags = new ArrayList<>();



        if (!result.getGlobalErrors().isEmpty()) {

            flags.add("The file is missing columns required for any match path.");

        }

        if (!result.getAdExportClashes().isEmpty()) {

            flags.add(String.format(
                    "%d AD export column name clash(es) detected — duplicate tl_* names would be created on export.",
                    result.getAdExportClashes().size()));

        }

        if (warningRowPercent >= 50) {

            flags.add("More than half of the rows have at least one data problem.");

        } else if (warningRowPercent >= 20) {

            flags.add("A noticeable share of rows have missing or invalid values.");

        }

        String npiCol = detectedColumns.get(StandardField.NPI);

        if (npiCol != null) {

            int badFormat = countValuesNotMatching(

                    rows, npiCol, v -> isBlank(v) || validator.isValidNpi(v));

            if (badFormat > 0 && rows.size() > 0) {

                double rate = badFormat * 100.0 / rows.size();

                if (rate >= 10) {

                    flags.add(String.format(

                            "Roughly %.0f%% of sampled NPI values fail format or checksum validation.", rate));

                }

            }

        }



        String zipCol = detectedColumns.get(StandardField.ZIP);

        if (zipCol != null) {

            int badZip = countValuesNotMatching(

                    rows, zipCol, v -> isBlank(v) || validator.isValid(StandardField.ZIP, v));

            if (badZip > 0 && rows.size() > 0) {

                double rate = badZip * 100.0 / rows.size();

                if (rate >= 10) {

                    flags.add(String.format(

                            "About %.0f%% of sampled zip values are not standard 5-digit (or ZIP+4) format.", rate));

                }

            }

        }



        String emailCol = detectedColumns.get(StandardField.EMAIL);

        if (emailCol != null) {

            int badEmail = countValuesNotMatching(

                    rows, emailCol, v -> isBlank(v) || validator.isValid(StandardField.EMAIL, v));

            if (badEmail > 0) {

                flags.add("Some email values in the sample do not look like valid email addresses.");

            }

        }



        String addressCol = detectedColumns.get(StandardField.ADDRESS);

        if (addressCol != null) {

            int badAddress = countValuesNotMatching(

                    rows, addressCol, v -> isBlank(v) || validator.isValid(StandardField.ADDRESS, v));

            if (badAddress > 0) {

                flags.add(String.format(

                        "%d sampled row(s) contain a malformed street address.", badAddress));

            }

        }



        Optional<PreflightResult.MatchTypeAssessment> best = result.getMatchTypes().stream()

                .filter(PreflightResult.MatchTypeAssessment::isViable)

                .findFirst();

        best.ifPresent(mt -> {

            if (mt.getCoveragePercent() < 85 && mt.getCoveragePercent() >= 40) {

                flags.add(String.format(

                        "%s is usable but coverage is only %.0f%% — expect gaps during matching.",

                        mt.getLabel(), mt.getCoveragePercent()));

            }

        });



        return flags;

    }



    private int countValuesNotMatching(

            List<Map<String, String>> rows,

            String column,

            java.util.function.Predicate<String> isOk

    ) {

        int count = 0;

        for (Map<String, String> row : rows) {

            String value = row.getOrDefault(column, "").trim();

            if (!isOk.test(value)) {

                count++;

            }

        }

        return count;

    }



    private boolean isBlank(String value) {

        return value == null || value.trim().isEmpty();

    }

}


