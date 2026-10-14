package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.ColumnMappingEntry;
import com.iqvia.preflight.model.StandardField;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ColumnMappingService {

    private final ColumnDetectorService columnDetector;

    public ColumnMappingService(ColumnDetectorService columnDetector) {
        this.columnDetector = columnDetector;
    }

    public List<String> availableMappedNames() {
        return IqviaColumnNaming.AVAILABLE_MAPPED_NAMES;
    }

    public List<ColumnMappingEntry> suggestMappings(
            List<String> headers,
            List<Map<String, String>> sampleRows
    ) {
        Map<StandardField, String> detected = columnDetector.detectColumns(headers, sampleRows);
        Map<String, StandardField> headerToField = new HashMap<>();
        for (Map.Entry<StandardField, String> entry : detected.entrySet()) {
            headerToField.put(entry.getValue(), entry.getKey());
        }

        List<ColumnMappingEntry> mappings = new ArrayList<>();
        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }
            String trimmed = header.trim();
            StandardField field = headerToField.get(trimmed);
            String mappedName = field != null
                    ? IqviaColumnNaming.mappedNameForStandardField(field)
                    : IqviaColumnNaming.EXTRA_DATA;
            mappings.add(new ColumnMappingEntry(trimmed, mappedName));
        }
        return mappings;
    }

    public Map<StandardField, String> toDetectedColumns(List<ColumnMappingEntry> mappings) {
        Map<StandardField, String> detected = new EnumMap<>(StandardField.class);
        for (ColumnMappingEntry entry : mappings) {
            StandardField field = standardFieldForMappedName(entry.getMappedName());
            if (field != null) {
                detected.put(field, entry.getHeaderName());
            }
        }
        return detected;
    }

    public List<String> unmappedHeaders(List<ColumnMappingEntry> mappings) {
        return mappings.stream()
                .filter(e -> IqviaColumnNaming.EXTRA_DATA.equalsIgnoreCase(e.getMappedName()))
                .map(ColumnMappingEntry::getHeaderName)
                .collect(Collectors.toList());
    }

    private StandardField standardFieldForMappedName(String mappedName) {
        if (mappedName == null || IqviaColumnNaming.EXTRA_DATA.equalsIgnoreCase(mappedName)) {
            return null;
        }
        for (StandardField field : StandardField.values()) {
            if (IqviaColumnNaming.mappedNameForStandardField(field).equalsIgnoreCase(mappedName)) {
                return field;
            }
        }
        return null;
    }
}
