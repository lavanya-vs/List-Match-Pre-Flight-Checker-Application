package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.AdExportColumnClash;
import com.iqvia.preflight.model.StandardField;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AdExportColumnClashServiceTest {

    private final AdExportColumnClashService service = new AdExportColumnClashService();
    private final ColumnDetectorService detector =
            new ColumnDetectorService(new FieldValidationService());

    @Test
    void detectsCityStateClashWhenCleanColumnsMapped() {
        List<String> headers = List.of(
                "NPI", "FIRST_NAME", "LAST_NAME", "CITY", "STATE", "CLEAN_CITY", "CLEAN_STATE", "ZIP", "ADDRESS1");
        Map<StandardField, String> detected = new EnumMap<>(StandardField.class);
        detected.put(StandardField.NPI, "NPI");
        detected.put(StandardField.FIRST_NAME, "FIRST_NAME");
        detected.put(StandardField.LAST_NAME, "LAST_NAME");
        detected.put(StandardField.CITY, "CLEAN_CITY");
        detected.put(StandardField.STATE, "CLEAN_STATE");
        detected.put(StandardField.ZIP, "ZIP");
        detected.put(StandardField.ADDRESS, "ADDRESS1");

        List<AdExportColumnClash> clashes = service.detect(headers, detected);

        Set<String> adNames = clashes.stream()
                .map(AdExportColumnClash::getAdExportName)
                .collect(Collectors.toSet());
        assertTrue(adNames.contains("tl_CITY"));
        assertTrue(adNames.contains("tl_STATE"));
    }

    @Test
    void noClashWhenOnlyCleanCityMappedAndPlainCityAbsent() {
        List<String> headers = List.of("NPI", "LAST_NAME", "CLEAN_CITY", "CLEAN_STATE", "ZIP");
        Map<StandardField, String> detected = new EnumMap<>(StandardField.class);
        detected.put(StandardField.NPI, "NPI");
        detected.put(StandardField.LAST_NAME, "LAST_NAME");
        detected.put(StandardField.CITY, "CLEAN_CITY");
        detected.put(StandardField.STATE, "CLEAN_STATE");
        detected.put(StandardField.ZIP, "ZIP");

        assertTrue(service.detect(headers, detected).isEmpty());
    }

    @Test
    void noClashOnPerfectSampleHeaders() {
        List<String> headers = List.of(
                "first_name", "last_name", "npi", "address", "city", "state", "zip", "email", "phone");
        Map<StandardField, String> detected = detector.detectColumns(headers);
        assertTrue(service.detect(headers, detected).isEmpty());
    }

    @Test
    void adExportNamingRules() {
        assertEquals("tl_CITY", IqviaColumnNaming.toAdExportName("CITY"));
        assertEquals("tl_CITY", IqviaColumnNaming.toAdExportName("x_CITY"));
        assertEquals("tl_NPI_NUMBER", IqviaColumnNaming.toAdExportName("NPI_NUMBER"));
    }

    @Test
    void flagsCompetingCityHeadersEvenWhenAutoMapPicksPlainCity() {
        List<String> headers = List.of("NPI", "LAST_NAME", "CITY", "CLEAN_CITY", "STATE", "CLEAN_STATE", "ZIP");
        Map<StandardField, String> detected = detector.detectColumns(headers);

        assertEquals("CITY", detected.get(StandardField.CITY));
        Set<String> adNames = service.detect(headers, detected).stream()
                .map(AdExportColumnClash::getAdExportName)
                .collect(Collectors.toSet());
        assertTrue(adNames.contains("tl_CITY"));
        assertTrue(adNames.contains("tl_STATE"));
    }
}
