package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.PreflightResult;
import com.iqvia.preflight.model.ReadinessScore;
import com.iqvia.preflight.model.StandardField;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealWorldDatasetTest {

    private final FileParserService parser = new FileParserService();
    private final FieldValidationService validator = new FieldValidationService();
    private final ColumnDetectorService detector = new ColumnDetectorService(validator);
    private final RulesEngineService rules = new RulesEngineService(validator);

    @Test
    void healthcareExportMapsStandardColumns() throws Exception {
        PreflightResult result = evaluateExternal("US_Healthcare_Providers_Dataset_2026.csv");

        assertEquals("npi", result.getDetectedColumns().get(StandardField.NPI));
        assertEquals("zip_code", result.getDetectedColumns().get(StandardField.ZIP));
        assertFalse(result.getMatchTypes().isEmpty());
    }

    @Test
    void clientStyleHeadersMapCorrectly() throws Exception {
        PreflightResult result = evaluateExternal("client_list_complex_realworld.csv");

        Map<StandardField, String> columns = result.getDetectedColumns();
        assertEquals("FNAME", columns.get(StandardField.FIRST_NAME));
        assertEquals("LNAME", columns.get(StandardField.LAST_NAME));
        assertEquals("NPI_NUM", columns.get(StandardField.NPI));
        assertEquals("POSTAL_CODE", columns.get(StandardField.ZIP));
        assertNotNull(columns.get(StandardField.CITY));
        assertNotNull(columns.get(StandardField.STATE));
    }

    @Test
    void titleRowsDoNotBreakCsvParsing() throws Exception {
        String csv = "Monthly HCP upload\n"
                + "Prepared by operations\n"
                + "FNAME,LNAME,NPI_NUM,POSTAL_CODE\n"
                + "Maria,Patel,1234567893,02115\n"
                + "James,Chen,1100000029,60611\n"
                + "Priya,Garcia,1100000037,98101\n";

        PreflightResult result = evaluateBytes("client_with_titles.csv", csv);

        assertEquals("FNAME", result.getDetectedColumns().get(StandardField.FIRST_NAME));
        assertEquals(3, result.getTotalRows());
    }

    @Test
    void oddHeadersStillReady() throws Exception {
        PreflightResult result = evaluateClasspath("sample_odd_headers.csv");
        assertEquals(ReadinessScore.READY, result.getScore());
    }

    private PreflightResult evaluateExternal(String fileName) throws IOException {
        Path path = resolveSampleData(fileName);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Missing dataset: " + fileName);
        }
        byte[] bytes = Files.readAllBytes(path);
        return evaluateBytes(fileName, new String(bytes, StandardCharsets.UTF_8));
    }

    private PreflightResult evaluateClasspath(String fileName) throws IOException {
        String resource = "static/samples/" + fileName;
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing dataset: " + resource);
            }
            return evaluateBytes(fileName, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Path resolveSampleData(String fileName) {
        Path base = Paths.get("..", "sample-data").normalize();
        List<String> folders = List.of("", "demos", "client-lists", "large", "ad-export-clash");
        for (String folder : folders) {
            Path candidate = folder.isEmpty()
                    ? base.resolve(fileName)
                    : base.resolve(folder).resolve(fileName);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
        }
        return base.resolve(fileName);
    }

    private PreflightResult evaluateBytes(String fileName, String csvBody) throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "text/csv", csvBody.getBytes(StandardCharsets.UTF_8));
        FileParserService.ParsedFile parsed = parser.parse(file);
        Map<StandardField, String> columns =
                detector.detectColumns(parsed.getHeaders(), parsed.getRows());
        return rules.evaluate(
                fileName,
                parsed.getTotalRows(),
                columns,
                detector.unmappedColumns(parsed.getHeaders(), columns),
                parser.rowSourceFor(file, parsed));
    }
}
