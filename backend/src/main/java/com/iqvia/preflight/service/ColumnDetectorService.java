package com.iqvia.preflight.service;

import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Maps file headers onto standard match fields.
 * Headers are checked first with token-aware scoring; when a header is unusual
 * the column values decide instead, so files with non-standard naming still map.
 */
@Service
public class ColumnDetectorService {

    private static final int SAMPLE_LIMIT = 40;
    private static final double VALUE_MATCH_THRESHOLD = 0.8;

    private static final Map<StandardField, List<String>> ALIASES = Map.of(
            StandardField.FIRST_NAME, List.of(
                    "first_name", "firstname", "fname", "first", "given_name", "givenname", "given",
                    "forename", "hcpgiven", "memberfirst", "providerfirst", "provfirst", "frstnm"),
            StandardField.LAST_NAME, List.of(
                    "last_name", "lastname", "lname", "last", "surname", "family_name", "familyname",
                    "family", "hcpfamily", "memberlast", "providerlast", "provlast", "lstnm"),
            StandardField.NPI, List.of(
                    "npi", "provider_npi", "npi_number", "npinumber", "national_provider_id", "npi_num",
                    "providerid", "provid", "enumeration", "enumerationkey", "provnpi", "nationalprovider",
                    "nationalprovideridentifier", "provideridentifier", "identifier"),
            StandardField.ZIP, List.of(
                    "zip", "zipcode", "zip_code", "postal_code", "postalcode", "postal", "postcode",
                    "zip5", "postalzone", "postal_zone", "mailingzip", "practicezip"),
            StandardField.ADDRESS, List.of(
                    "address", "street", "addr", "address1", "addr1", "address_line_1", "addressline1",
                    "street_address", "line1", "practiceline", "practice_line", "mailingaddress",
                    "practiceaddress", "locationaddress"),
            StandardField.CITY, List.of(
                    "city", "town", "municipality", "city_nm", "citynm", "locale", "locale_name",
                    "localename", "practicecity", "mailingcity"),
            StandardField.STATE, List.of(
                    "state", "st", "province", "state_code", "statecode", "adminarea", "admin_area",
                    "practice_state", "mailingstate"),
            StandardField.EMAIL, List.of(
                    "email", "email_address", "emailaddress", "e_mail", "inbox", "mail", "contactemail"),
            StandardField.PHONE, List.of(
                    "phone", "telephone", "phone_number", "phonenumber", "tel", "mobile", "cell",
                    "dial", "fax", "contactphone", "workphone")
    );

    private final FieldValidationService validator;

    public ColumnDetectorService(FieldValidationService validator) {
        this.validator = validator;
    }

    public Map<StandardField, String> detectColumns(List<String> headers) {
        return detectColumns(headers, Collections.emptyList());
    }

    public Map<StandardField, String> detectColumns(List<String> headers, List<Map<String, String>> rows) {
        Map<StandardField, String> detected = new EnumMap<>(StandardField.class);
        Set<String> usedHeaders = new HashSet<>();

        for (StandardField field : StandardField.values()) {
            String match = findBestHeaderMatch(headers, ALIASES.get(field), usedHeaders);
            if (match != null) {
                detected.put(field, match);
                usedHeaders.add(match);
            }
        }

        if (!rows.isEmpty()) {
            inferFromValues(headers, rows, detected, usedHeaders);
        }

        return detected;
    }

    public List<String> unmappedColumns(List<String> headers, Map<StandardField, String> detected) {
        Set<String> mapped = new HashSet<>(detected.values());
        return headers.stream()
                .filter(h -> !mapped.contains(h))
                .collect(Collectors.toList());
    }

    private void inferFromValues(
            List<String> headers,
            List<Map<String, String>> rows,
            Map<StandardField, String> detected,
            Set<String> usedHeaders
    ) {
        List<StandardField> inferable = List.of(
                StandardField.NPI, StandardField.ZIP, StandardField.EMAIL,
                StandardField.PHONE, StandardField.STATE, StandardField.ADDRESS,
                StandardField.CITY, StandardField.FIRST_NAME, StandardField.LAST_NAME
        );

        for (StandardField field : inferable) {
            if (detected.containsKey(field)) {
                continue;
            }
            String bestHeader = null;
            double bestScore = 0;
            for (String header : headers) {
                if (usedHeaders.contains(header)) {
                    continue;
                }
                List<String> samples = sampleValues(rows, header);
                if (!valuesLookLike(field, samples)) {
                    continue;
                }
                double score = headerHintScore(field, header);
                if (score > bestScore) {
                    bestScore = score;
                    bestHeader = header;
                }
            }
            if (bestHeader != null && (bestScore > 0 || isSafeValueOnlyField(field))) {
                detected.put(field, bestHeader);
                usedHeaders.add(bestHeader);
            }
        }
    }

    private boolean isSafeValueOnlyField(StandardField field) {
        return field == StandardField.NPI || field == StandardField.ZIP || field == StandardField.EMAIL
                || field == StandardField.PHONE || field == StandardField.STATE;
    }

    private double headerHintScore(StandardField field, String header) {
        List<String> aliases = ALIASES.get(field);
        if (aliases == null) {
            return 0;
        }
        String normalized = normalize(header);
        List<String> tokens = tokenize(header);
        double best = 0;
        for (String alias : aliases) {
            if (normalized.equals(alias)) {
                return 100;
            }
            if (normalized.contains(alias) && alias.length() >= 3) {
                best = Math.max(best, 40 + alias.length());
            }
            if (tokens.contains(alias)) {
                best = Math.max(best, 55);
            }
            for (String token : tokens) {
                if (token.equals(alias)) {
                    best = Math.max(best, 50);
                } else if (alias.length() >= 4 && token.contains(alias)) {
                    best = Math.max(best, 35);
                }
            }
        }
        return best;
    }

    private List<String> sampleValues(List<Map<String, String>> rows, String header) {
        List<String> values = new ArrayList<>();
        for (Map<String, String> row : rows) {
            if (values.size() >= SAMPLE_LIMIT) {
                break;
            }
            String value = row.getOrDefault(header, "");
            if (value != null && !value.trim().isEmpty()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private boolean valuesLookLike(StandardField field, List<String> samples) {
        if (samples.size() < 3) {
            return false;
        }
        long hits = samples.stream().filter(value -> matchesShape(field, value)).count();
        return (double) hits / samples.size() >= VALUE_MATCH_THRESHOLD;
    }

    private boolean matchesShape(StandardField field, String value) {
        return validator.isValid(field, value);
    }

    private String findBestHeaderMatch(List<String> headers, List<String> aliases, Set<String> used) {
        String bestHeader = null;
        int bestScore = 0;

        for (String header : headers) {
            if (used.contains(header)) {
                continue;
            }
            int score = scoreHeader(header, aliases);
            if (score > bestScore) {
                bestScore = score;
                bestHeader = header;
            }
        }

        return bestScore >= 35 ? bestHeader : null;
    }

    private int scoreHeader(String header, List<String> aliases) {
        String normalized = normalize(header);
        List<String> tokens = tokenize(header);
        int best = 0;

        for (String alias : aliases) {
            if (normalized.equals(alias)) {
                return 100;
            }
            if (tokens.contains(alias)) {
                best = Math.max(best, 70 + alias.length());
            }
            if (normalized.contains(alias) && alias.length() >= 3) {
                best = Math.max(best, 45 + alias.length());
            }
            for (String token : tokens) {
                if (token.equals(alias)) {
                    best = Math.max(best, 60);
                } else if (alias.length() >= 4 && token.contains(alias)) {
                    best = Math.max(best, 40);
                }
            }
        }
        return best;
    }

    private List<String> tokenize(String header) {
        String[] parts = header.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "")
                .trim();
    }
}
