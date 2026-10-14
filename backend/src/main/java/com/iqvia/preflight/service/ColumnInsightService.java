package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.ColumnInsight;
import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Reads every column header from the uploaded file and predicts what each one means.
 * Nothing is hard-coded per file — the list grows or shrinks with your CSV/Excel input.
 */
@Service
public class ColumnInsightService {

    private static final int MAX_SAMPLES = 5;
    private static final int MAX_PREVIEW_LENGTH = 80;
    private static final Map<StandardField, List<String>> HEADER_HINTS = Map.of(
            StandardField.NPI, List.of("npi", "provider", "provid", "national", "npinumber"),
            StandardField.LAST_NAME, List.of("last", "surname", "family", "lname"),
            StandardField.FIRST_NAME, List.of("first", "given", "fname"),
            StandardField.ZIP, List.of("zip", "postal", "postcode"),
            StandardField.ADDRESS, List.of("address", "street", "addr"),
            StandardField.CITY, List.of("city", "town"),
            StandardField.STATE, List.of("state", "province"),
            StandardField.EMAIL, List.of("email", "mail"),
            StandardField.PHONE, List.of("phone", "tel", "mobile", "fax")
    );

    private static final Set<StandardField> MATCH_FIELDS = EnumSet.of(
            StandardField.NPI, StandardField.LAST_NAME, StandardField.ZIP,
            StandardField.FIRST_NAME, StandardField.ADDRESS
    );

    private final FieldValidationService validator;

    public ColumnInsightService(FieldValidationService validator) {
        this.validator = validator;
    }

    /**
     * One insight row per column found in the uploaded file — including extras the user added.
     */
    public List<ColumnInsight> analyzeColumns(
            List<String> headers,
            List<Map<String, String>> rows,
            Map<StandardField, String> detectedColumns
    ) {
        return analyzeColumns(headers, rows, rows.size(), detectedColumns);
    }

    public List<ColumnInsight> analyzeColumns(
            List<String> headers,
            List<Map<String, String>> rows,
            int totalRows,
            Map<StandardField, String> detectedColumns
    ) {
        Map<String, StandardField> reverseMap = new HashMap<>();
        for (Map.Entry<StandardField, String> entry : detectedColumns.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }

        List<String> allHeaders = collectAllHeaders(headers, rows);

        List<ColumnInsight> insights = new ArrayList<>();
        for (String header : allHeaders) {
            ColumnStats stats = computeColumnStats(rows, header, totalRows);
            List<String> samples = stats.samples;
            StandardField mapped = reverseMap.get(header);

            if (mapped != null) {
                insights.add(buildMappedInsight(header, mapped, stats, samples));
            } else {
                insights.add(buildGuessedInsight(header, stats, samples));
            }
        }
        return insights;
    }

    /**
     * Use headers from the file plus any extra keys seen in row data (edge-case Excel exports).
     */
    private List<String> collectAllHeaders(List<String> headers, List<Map<String, String>> rows) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String h : headers) {
            if (h != null && !h.trim().isEmpty()) {
                ordered.add(h.trim());
            }
        }
        for (Map<String, String> row : rows) {
            ordered.addAll(row.keySet());
        }
        return new ArrayList<>(ordered);
    }

    private ColumnInsight buildMappedInsight(
            String header, StandardField mapped, ColumnStats stats, List<String> samples
    ) {
        String reason = "Mapped automatically to " + labelFor(mapped) + ". "
                + formatMissingNote(stats.missingPercent)
                + previewNote(samples);
        return new ColumnInsight(
                header,
                labelFor(mapped),
                95,
                reason.trim(),
                true,
                MATCH_FIELDS.contains(mapped),
                stats.missingPercent,
                stats.filledRows,
                stats.totalRows,
                buildPreview(samples)
        );
    }

    private ColumnInsight buildGuessedInsight(String header, ColumnStats stats, List<String> samples) {
        Guess guess = guessField(header, samples);
        String suggested = guess.field != null ? labelFor(guess.field) : "Custom column";
        int confidence = guess.field != null ? guess.confidence : Math.max(20, 40 - (int) stats.missingPercent / 3);
        String reason = guess.field != null
                ? guess.reason + " " + formatMissingNote(stats.missingPercent) + previewNote(samples)
                : "Not mapped to a standard match field. " + formatMissingNote(stats.missingPercent) + previewNote(samples);

        return new ColumnInsight(
                header,
                suggested,
                confidence,
                reason.trim(),
                false,
                guess.field != null && MATCH_FIELDS.contains(guess.field),
                stats.missingPercent,
                stats.filledRows,
                stats.totalRows,
                buildPreview(samples)
        );
    }

    private String formatMissingNote(double missingPercent) {
        if (missingPercent <= 0) {
            return "All rows have a value.";
        }
        return String.format("%.1f%% of rows are missing a value here.", missingPercent);
    }

    private String previewNote(List<String> samples) {
        if (samples.isEmpty()) {
            return " No sample values found.";
        }
        return "";
    }

    private String buildPreview(List<String> samples) {
        if (samples.isEmpty()) {
            return "(empty)";
        }
        String joined = samples.stream()
                .limit(3)
                .map(s -> s.length() > 24 ? s.substring(0, 21) + "..." : s)
                .collect(Collectors.joining(", "));
        if (joined.length() > MAX_PREVIEW_LENGTH) {
            return joined.substring(0, MAX_PREVIEW_LENGTH - 3) + "...";
        }
        return joined;
    }

    private ColumnStats computeColumnStats(List<Map<String, String>> rows, String header, int totalRows) {
        int empty = 0;
        List<String> samples = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String value = row.getOrDefault(header, "").trim();
            if (value.isEmpty()) {
                empty++;
            } else if (samples.size() < MAX_SAMPLES) {
                samples.add(value);
            }
        }
        int filled = totalRows - empty;
        double missingPercent = totalRows == 0 ? 0
                : Math.round(empty * 1000.0 / totalRows) / 10.0;
        return new ColumnStats(totalRows, filled, missingPercent, samples);
    }

    private Guess guessField(String header, List<String> samples) {
        String normalizedHeader = normalize(header);
        Map<StandardField, Integer> scores = new EnumMap<>(StandardField.class);

        for (StandardField field : StandardField.values()) {
            int score = 0;
            for (String hint : HEADER_HINTS.get(field)) {
                if (normalizedHeader.equals(hint) || normalizedHeader.contains(hint)) {
                    score += 45;
                } else if (hint.length() > 3 && normalizedHeader.contains(hint)) {
                    score += 30;
                }
            }
            score += scoreSamples(field, samples, normalizedHeader);
            scores.put(field, score);
        }

        StandardField best = null;
        int bestScore = 0;
        for (Map.Entry<StandardField, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                best = entry.getKey();
            }
        }

        if (bestScore < 30) {
            return new Guess(null, 25, "Could not confidently match this column to a standard match field.");
        }

        int confidence = Math.min(90, 30 + bestScore);
        return new Guess(best, confidence, buildReason(best, samples));
    }

    private int scoreSamples(StandardField field, List<String> samples, String normalizedHeader) {
        if (samples.isEmpty()) {
            return 0;
        }
        // These fields need a header hint so short text values (MD, Active) are not misread.
        if (field == StandardField.FIRST_NAME || field == StandardField.LAST_NAME
                || field == StandardField.CITY || field == StandardField.STATE) {
            if (!hasHeaderHint(field, normalizedHeader)) {
                return 0;
            }
        }
        int matches = 0;
        for (String sample : samples) {
            if (matchesFieldShape(field, sample)) {
                matches++;
            }
        }
        return (matches * 55) / samples.size();
    }

    private boolean hasHeaderHint(StandardField field, String normalizedHeader) {
        for (String hint : HEADER_HINTS.get(field)) {
            if (normalizedHeader.contains(hint) || normalizedHeader.equals(hint)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFieldShape(StandardField field, String value) {
        return validator.isValid(field, value);
    }

    private String buildReason(StandardField field, List<String> samples) {
        switch (field) {
            case NPI:
                return "Header and values look like provider NPI numbers.";
            case ZIP:
                return "Header and values look like US postal zip codes.";
            case LAST_NAME:
                return "Header and text shape suggest a last name column.";
            case FIRST_NAME:
                return "Header and text shape suggest a first name column.";
            case ADDRESS:
                return "Values look like street or mailing addresses.";
            case CITY:
                return "Values look like city names.";
            case STATE:
                return "Values look like state abbreviations or names.";
            case EMAIL:
                return "Values look like email addresses.";
            case PHONE:
                return "Values look like phone numbers.";
            default:
                return "Column may relate to list matching.";
        }
    }

    private String labelFor(StandardField field) {
        if (field == null) {
            return "Custom column";
        }
        switch (field) {
            case FIRST_NAME: return "First name";
            case LAST_NAME: return "Last name";
            case NPI: return "NPI";
            case ZIP: return "Zip";
            case ADDRESS: return "Address";
            case CITY: return "City";
            case STATE: return "State";
            case EMAIL: return "Email";
            case PHONE: return "Phone";
            default: return field.name();
        }
    }

    private String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static class ColumnStats {
        final int totalRows;
        final int filledRows;
        final double missingPercent;
        final List<String> samples;

        ColumnStats(int totalRows, int filledRows, double missingPercent, List<String> samples) {
            this.totalRows = totalRows;
            this.filledRows = filledRows;
            this.missingPercent = missingPercent;
            this.samples = samples;
        }
    }

    private static class Guess {
        final StandardField field;
        final int confidence;
        final String reason;

        Guess(StandardField field, int confidence, String reason) {
            this.field = field;
            this.confidence = confidence;
            this.reason = reason;
        }
    }
}
