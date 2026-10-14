package com.iqvia.preflight.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserServiceTest {

    private final FileParserService parser = new FileParserService();

    @Test
    void stripsBomAndSkipsBlankRows() throws Exception {
        String csv = "\uFEFFFIRST_NAME,LAST_NAME\nMaria,Patel\n,\n";
        MockMultipartFile file = csv("test.csv", csv);

        FileParserService.ParsedFile parsed = parser.parse(file);

        assertEquals("FIRST_NAME", parsed.getHeaders().get(0));
        assertEquals(1, parsed.getRows().size());
    }

    @Test
    void renamesDuplicateHeadersInsteadOfRejecting() throws Exception {
        MockMultipartFile file = csv("test.csv", "NPI,npi\n1234567893,1234567893\n");

        FileParserService.ParsedFile parsed = parser.parse(file);

        assertEquals(2, parsed.getHeaders().size());
        assertTrue(parsed.getHeaders().get(1).contains("("));
    }

    @Test
    void detectsHeaderRowBelowTitleRows() throws Exception {
        String csv = "Client Provider List Export\nGenerated 2026-09-01\n"
                + "NPI,FIRST_NAME,LAST_NAME,ZIP\n1234567893,Maria,Patel,02115\n";
        MockMultipartFile file = csv("test.csv", csv);

        FileParserService.ParsedFile parsed = parser.parse(file);

        assertEquals("NPI", parsed.getHeaders().get(0));
        assertEquals(1, parsed.getRows().size());
        assertEquals("Maria", parsed.getRows().get(0).get("FIRST_NAME"));
    }

    @Test
    void padsShortRowsInsteadOfRejecting() throws Exception {
        MockMultipartFile file = csv("test.csv", "FIRST_NAME,LAST_NAME\nMaria\n");

        FileParserService.ParsedFile parsed = parser.parse(file);

        assertEquals(1, parsed.getRows().size());
        assertEquals("", parsed.getRows().get(0).get("LAST_NAME"));
    }

    @Test
    void detectsHeaderRowIndexFromMixedRows() throws Exception {
        List<String[]> rows = Arrays.asList(
                new String[]{"Provider export"},
                new String[]{"NPI", "FIRST_NAME", "LAST_NAME", "ZIP"},
                new String[]{"1234567893", "Maria", "Patel", "02115"}
        );

        int index = parser.detectHeaderRowIndex(rows);

        assertEquals(1, index);
    }

    @Test
    void assignsNamesToEmptyHeaders() throws Exception {
        MockMultipartFile file = csv("test.csv", "NPI,,LAST_NAME\n1234567893,Maria,Patel\n");

        FileParserService.ParsedFile parsed = parser.parse(file);

        assertEquals("Column_2", parsed.getHeaders().get(1));
    }

    private MockMultipartFile csv(String name, String body) {
        return new MockMultipartFile(
                "file", name, "text/csv", body.getBytes(StandardCharsets.UTF_8));
    }
}
