package com.iqvia.preflight.service;

import com.iqvia.preflight.dto.AdExportClashCheckResponse;
import com.iqvia.preflight.dto.ColumnMappingEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdExportClashIntegrationTest {

    private final AdExportColumnClashService clashService = new AdExportColumnClashService();

    @Test
    void michaelExampleMappingProducesDuplicateAdExportNames() {
        List<ColumnMappingEntry> mappings = List.of(
                new ColumnMappingEntry("NPI", "NPI_NUMBER"),
                new ColumnMappingEntry("FIRST_NAME", "FIRST_NAME"),
                new ColumnMappingEntry("LAST_NAME", "LAST_NAME"),
                new ColumnMappingEntry("CITY", "EXTRA_DATA"),
                new ColumnMappingEntry("STATE", "EXTRA_DATA"),
                new ColumnMappingEntry("CLEAN_CITY", "CITY"),
                new ColumnMappingEntry("CLEAN_STATE", "STATE"),
                new ColumnMappingEntry("ZIP", "ZIP"),
                new ColumnMappingEntry("ADDRESS1", "ADDRESS_1")
        );

        AdExportClashCheckResponse response = clashService.checkMappings(mappings);

        assertTrue(response.isHasClashes());
        assertEquals(2, response.getClashes().size());
        assertEquals(9, response.getClientListHeaders().size());
        assertEquals("x_CITY", response.getClientListHeaders().get(3).getDbColumnName());
        assertEquals("CITY", response.getClientListHeaders().get(5).getDbColumnName());
        assertEquals("tl_CITY", response.getClientListHeaders().get(3).getAdExportName());
        assertEquals("tl_CITY", response.getClientListHeaders().get(5).getAdExportName());
    }
}
