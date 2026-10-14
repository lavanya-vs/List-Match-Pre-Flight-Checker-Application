package com.iqvia.preflight.dto;

import java.util.List;

public class AdExportClashCheckRequest {

    private List<ColumnMappingEntry> columnMappings;

    public List<ColumnMappingEntry> getColumnMappings() {
        return columnMappings;
    }

    public void setColumnMappings(List<ColumnMappingEntry> columnMappings) {
        this.columnMappings = columnMappings;
    }
}
