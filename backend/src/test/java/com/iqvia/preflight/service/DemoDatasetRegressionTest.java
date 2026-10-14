package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.PreflightResult;
import com.iqvia.preflight.model.ReadinessScore;
import com.iqvia.preflight.model.StandardField;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDatasetRegressionTest {

    private final FileParserService parser = new FileParserService();
    private final FieldValidationService validator = new FieldValidationService();
    private final ColumnDetectorService detector = new ColumnDetectorService(validator);
    private final RulesEngineService rules = new RulesEngineService(validator);

    @Test
    void perfectDatasetIsReady() throws Exception {
        PreflightResult result = evaluate("sample_perfect.csv");

        assertEquals(ReadinessScore.READY, result.getScore());
        assertEquals("NPI matching", result.getMatchTypes().get(0).getLabel());
        assertEquals(100.0, result.getMatchTypes().get(0).getCoveragePercent());
        assertTrue(result.getGlobalErrors().isEmpty());
    }

    @Test
    void okayishDatasetNeedsWork() throws Exception {
        PreflightResult result = evaluate("sample_okayish.csv");

        assertEquals(ReadinessScore.NEEDS_WORK, result.getScore());
        assertEquals("NPI matching", result.getMatchTypes().get(0).getLabel());
        assertTrue(result.getMatchTypes().get(0).getCoveragePercent() >= 60.0);
        assertTrue(result.getMatchTypes().get(0).getCoveragePercent() < 85.0);
        assertFalse(result.getRowWarnings().isEmpty());
    }

    @Test
    void badDatasetWillFail() throws Exception {
        PreflightResult result = evaluate("sample_bad.csv");

        assertEquals(ReadinessScore.WILL_FAIL, result.getScore());
        assertTrue(result.getMatchTypes().isEmpty());
        assertFalse(result.getGlobalErrors().isEmpty());
    }

    private PreflightResult evaluate(String fileName) throws Exception {
        String resource = "static/samples/" + fileName;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing test dataset: " + resource);
            }
            MockMultipartFile file =
                    new MockMultipartFile("file", fileName, "text/csv", input);
            FileParserService.ParsedFile parsed = parser.parse(file);
            Map<StandardField, String> columns =
                    detector.detectColumns(parsed.getHeaders(), parsed.getRows());
            List<String> unmapped = detector.unmappedColumns(parsed.getHeaders(), columns);
            return rules.evaluate(fileName, parsed.getTotalRows(), columns, unmapped,
                    parser.inMemoryRowSource(parsed.getSampleRows()));
        }
    }
}
