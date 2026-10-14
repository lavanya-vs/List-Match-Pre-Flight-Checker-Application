package com.iqvia.preflight.service;

import com.iqvia.preflight.model.StandardField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldValidationServiceTest {

    private final FieldValidationService validator = new FieldValidationService();

    @Test
    void validatesNpiChecksumNotJustLength() {
        assertTrue(validator.isValidNpi("1100000011"));
        assertTrue(validator.isValidNpi("1234567893"));
        assertFalse(validator.isValidNpi("1234567890"));
        assertFalse(validator.isValidNpi("12345"));
    }

    @Test
    void validatesCoreMatchFields() {
        assertTrue(validator.isValid(StandardField.ZIP, "02115"));
        assertTrue(validator.isValid(StandardField.ZIP, "02115-1234"));
        assertTrue(validator.isValid(StandardField.ZIP, "021151234"));
        assertTrue(validator.isValid(StandardField.ZIP, "2115"));
        assertFalse(validator.isValid(StandardField.ZIP, "12"));

        assertTrue(validator.isValid(StandardField.ADDRESS, "111 Oak Ave"));
        assertTrue(validator.isValid(StandardField.ADDRESS, "P.O. Box 120"));
        assertFalse(validator.isValid(StandardField.ADDRESS, "Unknown"));

        assertTrue(validator.isValid(StandardField.STATE, "MA"));
        assertTrue(validator.isValid(StandardField.STATE, "New York"));
        assertFalse(validator.isValid(StandardField.STATE, "XX"));
    }

    @Test
    void normalizesExcelStyleNumericStrings() {
        assertTrue(validator.isValidNpi("1234567893.0"));
        assertTrue(validator.isValid(StandardField.ZIP, "2115.0"));
    }
}
