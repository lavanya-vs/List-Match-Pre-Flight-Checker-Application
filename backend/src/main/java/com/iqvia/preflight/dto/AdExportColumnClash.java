package com.iqvia.preflight.dto;

import java.util.List;

public class AdExportColumnClash {

    private final String adExportName;
    private final List<ClashColumn> columns;

    public AdExportColumnClash(String adExportName, List<ClashColumn> columns) {
        this.adExportName = adExportName;
        this.columns = columns;
    }

    public String getAdExportName() {
        return adExportName;
    }

    public List<ClashColumn> getColumns() {
        return columns;
    }

    public static class ClashColumn {
        private final String headerName;
        private final String dbColumnName;
        private final boolean mapped;

        public ClashColumn(String headerName, String dbColumnName, boolean mapped) {
            this.headerName = headerName;
            this.dbColumnName = dbColumnName;
            this.mapped = mapped;
        }

        public String getHeaderName() {
            return headerName;
        }

        public String getDbColumnName() {
            return dbColumnName;
        }

        public boolean isMapped() {
            return mapped;
        }
    }
}
