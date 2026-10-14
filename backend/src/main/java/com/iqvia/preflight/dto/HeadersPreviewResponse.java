package com.iqvia.preflight.dto;

import java.util.List;

public class HeadersPreviewResponse {

    private final String fileName;
    private final List<String> headers;
    private final List<ColumnMappingEntry> suggestedMappings;
    private final List<String> availableMappedNames;

    public HeadersPreviewResponse(
            String fileName,
            List<String> headers,
            List<ColumnMappingEntry> suggestedMappings,
            List<String> availableMappedNames
    ) {
        this.fileName = fileName;
        this.headers = headers;
        this.suggestedMappings = suggestedMappings;
        this.availableMappedNames = availableMappedNames;
    }

    public String getFileName() {
        return fileName;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<ColumnMappingEntry> getSuggestedMappings() {
        return suggestedMappings;
    }

    public List<String> getAvailableMappedNames() {
        return availableMappedNames;
    }
}
