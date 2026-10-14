package com.iqvia.preflight.dto;

import java.util.List;

public class AdExportClashCheckResponse {

    private final boolean hasClashes;
    private final List<AdExportColumnClash> clashes;
    private final List<ClientListHeaderPreview> clientListHeaders;

    public AdExportClashCheckResponse(
            boolean hasClashes,
            List<AdExportColumnClash> clashes,
            List<ClientListHeaderPreview> clientListHeaders
    ) {
        this.hasClashes = hasClashes;
        this.clashes = clashes;
        this.clientListHeaders = clientListHeaders;
    }

    public boolean isHasClashes() {
        return hasClashes;
    }

    public List<AdExportColumnClash> getClashes() {
        return clashes;
    }

    public List<ClientListHeaderPreview> getClientListHeaders() {
        return clientListHeaders;
    }
}
