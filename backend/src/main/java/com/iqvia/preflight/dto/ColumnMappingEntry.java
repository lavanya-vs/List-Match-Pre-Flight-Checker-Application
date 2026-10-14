package com.iqvia.preflight.dto;

/** One column's IQVIATools mapping choice (mapping screen equivalent). */
public class ColumnMappingEntry {

    private String headerName;
    private String mappedName;

    public ColumnMappingEntry() {
    }

    public ColumnMappingEntry(String headerName, String mappedName) {
        this.headerName = headerName;
        this.mappedName = mappedName;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getMappedName() {
        return mappedName;
    }

    public void setMappedName(String mappedName) {
        this.mappedName = mappedName;
    }
}
