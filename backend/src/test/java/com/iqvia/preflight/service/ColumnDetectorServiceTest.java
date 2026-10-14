package com.iqvia.preflight.service;

import com.iqvia.preflight.model.StandardField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ColumnDetectorServiceTest {

    private final ColumnDetectorService detector =
            new ColumnDetectorService(new FieldValidationService());

    @Test
    void doesNotGuessLastNameFromFreeText() {
        List<String> headers = List.of("FIRST_NAME", "EMAIL", "REGION", "NOTES");
        List<Map<String, String>> rows = List.of(
                Map.of("FIRST_NAME", "Maria", "EMAIL", "maria@example.test",
                        "REGION", "North", "NOTES", "Imported contact record"),
                Map.of("FIRST_NAME", "James", "EMAIL", "james@example.test",
                        "REGION", "South", "NOTES", "No provider identifier"),
                Map.of("FIRST_NAME", "Priya", "EMAIL", "priya@example.test",
                        "REGION", "West", "NOTES", "Awaiting client response")
        );

        Map<StandardField, String> detected = detector.detectColumns(headers, rows);

        assertFalse(detected.containsKey(StandardField.LAST_NAME));
    }

    @Test
    void infersChecksumValidNpiFromUnusualHeader() {
        List<String> headers = List.of("IDENTIFIER");
        List<Map<String, String>> rows = List.of(
                Map.of("IDENTIFIER", "1100000011"),
                Map.of("IDENTIFIER", "1100000029"),
                Map.of("IDENTIFIER", "1100000037")
        );

        Map<StandardField, String> detected = detector.detectColumns(headers, rows);

        assertEquals("IDENTIFIER", detected.get(StandardField.NPI));
    }

    @Test
    void mapsClientStyleHeadersAndValueShapes() {
        List<String> headers = List.of(
                "HCP_Given", "HCP_Family", "Enumeration_Key", "Practice_Line",
                "Locale_Name", "Admin_Area", "Postal_Zone", "Inbox", "Dial"
        );
        List<Map<String, String>> rows = List.of(
                Map.of("HCP_Given", "Maria", "HCP_Family", "Patel", "Enumeration_Key", "1100000011",
                        "Practice_Line", "111 Oak Ave", "Locale_Name", "Chicago", "Admin_Area", "IL",
                        "Postal_Zone", "60611", "Inbox", "maria.patel@clinic.example", "Dial", "6175551001"),
                Map.of("HCP_Given", "James", "HCP_Family", "Chen", "Enumeration_Key", "1100000029",
                        "Practice_Line", "122 Maple Dr", "Locale_Name", "Houston", "Admin_Area", "TX",
                        "Postal_Zone", "77030", "Inbox", "james.chen@clinic.example", "Dial", "6175551002"),
                Map.of("HCP_Given", "Priya", "HCP_Family", "Garcia", "Enumeration_Key", "1100000037",
                        "Practice_Line", "133 Park Blvd", "Locale_Name", "Seattle", "Admin_Area", "WA",
                        "Postal_Zone", "98101", "Inbox", "priya.garcia@clinic.example", "Dial", "6175551003")
        );

        Map<StandardField, String> detected = detector.detectColumns(headers, rows);

        assertEquals("HCP_Given", detected.get(StandardField.FIRST_NAME));
        assertEquals("HCP_Family", detected.get(StandardField.LAST_NAME));
        assertEquals("Enumeration_Key", detected.get(StandardField.NPI));
        assertEquals("Practice_Line", detected.get(StandardField.ADDRESS));
        assertEquals("Locale_Name", detected.get(StandardField.CITY));
        assertEquals("Admin_Area", detected.get(StandardField.STATE));
        assertEquals("Postal_Zone", detected.get(StandardField.ZIP));
        assertEquals("Inbox", detected.get(StandardField.EMAIL));
        assertEquals("Dial", detected.get(StandardField.PHONE));
    }
}
