package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.AdExportClashCheckResponse;
import com.iqvia.preflight.dto.AdExportColumnClash;
import com.iqvia.preflight.dto.AdExportColumnClash.ClashColumn;
import com.iqvia.preflight.dto.ClientListHeaderPreview;
import com.iqvia.preflight.dto.ColumnMappingEntry;
import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks whether two of the client's columns would end up with the same name in IQVIATools export.
 * That would break the load — like two spreadsheet columns both called "City".
 */
@Service
public class AdExportColumnClashService {

    private static final int CANDIDATE_SCORE_THRESHOLD = 35;

    private static final Map<StandardField, List<String>> ALIASES = Map.of(
            StandardField.FIRST_NAME, List.of(
                    "first_name", "firstname", "fname", "first", "given_name", "givenname", "given"),
            StandardField.LAST_NAME, List.of(
                    "last_name", "lastname", "lname", "last", "surname", "family_name", "familyname"),
            StandardField.NPI, List.of(
                    "npi", "provider_npi", "npi_number", "npinumber", "national_provider_id"),
            StandardField.ZIP, List.of(
                    "zip", "zipcode", "zip_code", "postal_code", "postalcode", "postal", "postcode"),
            StandardField.ADDRESS, List.of(
                    "address", "street", "addr", "address1", "addr1", "address_line_1", "street_address"),
            StandardField.CITY, List.of(
                    "city", "town", "municipality", "city_nm", "clean_city"),
            StandardField.STATE, List.of(
                    "state", "st", "province", "state_code", "clean_state"),
            StandardField.EMAIL, List.of("email", "email_address", "emailaddress", "e_mail"),
            StandardField.PHONE, List.of("phone", "telephone", "phone_number", "phonenumber", "tel", "mobile")
    );

    public AdExportClashCheckResponse checkMappings(List<ColumnMappingEntry> mappings) {
        List<ClientListHeaderPreview> headers = simulateClientListHeaders(mappings);
        List<AdExportColumnClash> clashes = detectFromMappings(mappings);
        return new AdExportClashCheckResponse(!clashes.isEmpty(), clashes, headers);
    }

    public List<AdExportColumnClash> detect(List<String> headers, Map<StandardField, String> detectedColumns) {
        LinkedHashMap<String, AdExportColumnClash> clashes = new LinkedHashMap<>();
        for (Map<StandardField, String> mapping : buildMappingScenarios(headers, detectedColumns)) {
            List<ColumnMappingEntry> plan = mappingToEntries(headers, mapping);
            for (AdExportColumnClash clash : detectFromMappings(plan)) {
                clashes.putIfAbsent(clash.getAdExportName(), clash);
            }
        }
        return new ArrayList<>(clashes.values());
    }

    public List<ClientListHeaderPreview> simulateClientListHeaders(List<ColumnMappingEntry> mappings) {
        List<ClientListHeaderPreview> rows = new ArrayList<>();
        int index = 0;
        for (ColumnMappingEntry entry : mappings) {
            String dbName = IqviaColumnNaming.dbNameFromMappedName(entry.getMappedName(), entry.getHeaderName());
            rows.add(new ClientListHeaderPreview(
                    index,
                    entry.getHeaderName(),
                    normalizeMappedName(entry.getMappedName()),
                    dbName,
                    IqviaColumnNaming.toAdExportName(dbName)
            ));
            index++;
        }
        return rows;
    }

    /** Group columns by their export name; any group with two or more columns is a clash. */
    public List<AdExportColumnClash> detectFromMappings(List<ColumnMappingEntry> mappings) {
        Map<String, List<ClashColumn>> byAdName = new LinkedHashMap<>();
        for (ColumnMappingEntry entry : mappings) {
            String dbName = IqviaColumnNaming.dbNameFromMappedName(entry.getMappedName(), entry.getHeaderName());
            String adName = IqviaColumnNaming.toAdExportName(dbName);
            boolean mapped = !IqviaColumnNaming.EXTRA_DATA.equalsIgnoreCase(entry.getMappedName());
            byAdName.computeIfAbsent(adName, k -> new ArrayList<>())
                    .add(new ClashColumn(entry.getHeaderName(), dbName, mapped));
        }

        List<AdExportColumnClash> clashes = new ArrayList<>();
        for (Map.Entry<String, List<ClashColumn>> group : byAdName.entrySet()) {
            if (group.getValue().size() > 1) {
                clashes.add(new AdExportColumnClash(group.getKey(), group.getValue()));
            }
        }
        return clashes;
    }

    private List<ColumnMappingEntry> mappingToEntries(List<String> headers, Map<StandardField, String> mapping) {
        Map<String, StandardField> headerToField = new HashMap<>();
        for (Map.Entry<StandardField, String> entry : mapping.entrySet()) {
            if (entry.getValue() != null) {
                headerToField.put(entry.getValue(), entry.getKey());
            }
        }
        List<ColumnMappingEntry> entries = new ArrayList<>();
        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            String trimmed = header.trim();
            StandardField field = headerToField.get(trimmed);
            String mappedName = field != null
                    ? IqviaColumnNaming.mappedNameForStandardField(field)
                    : IqviaColumnNaming.EXTRA_DATA;
            entries.add(new ColumnMappingEntry(trimmed, mappedName));
        }
        return entries;
    }

    private List<Map<StandardField, String>> buildMappingScenarios(
            List<String> headers,
            Map<StandardField, String> detectedColumns
    ) {
        LinkedHashSet<String> scenarioKeys = new LinkedHashSet<>();
        List<Map<StandardField, String>> scenarios = new ArrayList<>();
        addScenario(scenarios, scenarioKeys, detectedColumns);

        for (StandardField field : StandardField.values()) {
            List<String> candidates = findCandidates(headers, field);
            if (candidates.size() < 2) {
                continue;
            }
            for (String candidate : candidates) {
                EnumMap<StandardField, String> mapping = new EnumMap<>(detectedColumns);
                mapping.put(field, candidate);
                addScenario(scenarios, scenarioKeys, mapping);
            }
        }
        return scenarios;
    }

    private void addScenario(
            List<Map<StandardField, String>> scenarios,
            Set<String> keys,
            Map<StandardField, String> mapping
    ) {
        String key = mapping.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("|"));
        if (keys.add(key)) {
            scenarios.add(new EnumMap<>(mapping));
        }
    }

    private String normalizeMappedName(String mappedName) {
        if (mappedName == null || mappedName.isBlank()) {
            return IqviaColumnNaming.EXTRA_DATA;
        }
        if (IqviaColumnNaming.EXTRA_DATA.equalsIgnoreCase(mappedName)) {
            return IqviaColumnNaming.EXTRA_DATA;
        }
        return mappedName.trim();
    }

    List<String> findCandidates(List<String> headers, StandardField field) {
        List<String> aliases = ALIASES.get(field);
        if (aliases == null) {
            return Collections.emptyList();
        }
        List<String> candidates = new ArrayList<>();
        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            if (scoreHeader(header, aliases) >= CANDIDATE_SCORE_THRESHOLD) {
                candidates.add(header.trim());
            }
        }
        return candidates;
    }

    private int scoreHeader(String header, List<String> aliases) {
        String normalized = IqviaColumnNaming.normalizeToken(header);
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
}
