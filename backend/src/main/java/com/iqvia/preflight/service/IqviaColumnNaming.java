package com.iqvia.preflight.service;

import com.iqvia.preflight.model.StandardField;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** IQVIATools DB and AD export column naming rules. */
final class IqviaColumnNaming {

    static final String EXTRA_DATA = "EXTRA_DATA";

    static final List<String> AVAILABLE_MAPPED_NAMES = List.of(
            EXTRA_DATA,
            "NPI_NUMBER",
            "FULL_NAME",
            "FIRST_NAME",
            "MIDDLE_NAME",
            "LAST_NAME",
            "ADDRESS_1",
            "ADDRESS_2",
            "CITY",
            "STATE",
            "ZIP",
            "EMAIL",
            "PHONE"
    );

    private static final Map<StandardField, String> STANDARD_TO_MAPPED = Map.of(
            StandardField.NPI, "NPI_NUMBER",
            StandardField.FIRST_NAME, "FIRST_NAME",
            StandardField.LAST_NAME, "LAST_NAME",
            StandardField.ADDRESS, "ADDRESS_1",
            StandardField.CITY, "CITY",
            StandardField.STATE, "STATE",
            StandardField.ZIP, "ZIP",
            StandardField.EMAIL, "EMAIL",
            StandardField.PHONE, "PHONE"
    );

    private IqviaColumnNaming() {
    }

    static String mappedNameForStandardField(StandardField field) {
        return STANDARD_TO_MAPPED.getOrDefault(field, field.name());
    }

    static String dbNameFromMappedName(String mappedName, String headerName) {
        if (mappedName == null || mappedName.isBlank() || EXTRA_DATA.equalsIgnoreCase(mappedName)) {
            return dbNameForExtraHeader(headerName);
        }
        return mappedName.trim();
    }

    static String dbNameForMappedField(StandardField field) {
        return dbNameFromMappedName(mappedNameForStandardField(field), "");
    }

    static String dbNameForExtraHeader(String header) {
        return "x_" + header;
    }

    static String toAdExportName(String dbColumnName) {
        if (dbColumnName == null || dbColumnName.isEmpty()) {
            return "";
        }
        if (dbColumnName.regionMatches(true, 0, "x_", 0, 2)) {
            return "tl_" + dbColumnName.substring(2);
        }
        return "tl_" + dbColumnName;
    }

    static String normalizeToken(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
