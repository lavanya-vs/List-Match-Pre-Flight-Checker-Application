package com.iqvia.preflight.dto;

/** Simulated CLIENT_LIST_HEADERS row plus derived AD export name. */
public class ClientListHeaderPreview {

    private final int columnNumber;
    private final String headerName;
    private final String mappedName;
    private final String dbColumnName;
    private final String adExportName;

    public ClientListHeaderPreview(
            int columnNumber,
            String headerName,
            String mappedName,
            String dbColumnName,
            String adExportName
    ) {
        this.columnNumber = columnNumber;
        this.headerName = headerName;
        this.mappedName = mappedName;
        this.dbColumnName = dbColumnName;
        this.adExportName = adExportName;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public String getHeaderName() {
        return headerName;
    }

    public String getMappedName() {
        return mappedName;
    }

    public String getDbColumnName() {
        return dbColumnName;
    }

    public String getAdExportName() {
        return adExportName;
    }
}
