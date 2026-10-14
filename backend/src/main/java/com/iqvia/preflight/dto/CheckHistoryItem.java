package com.iqvia.preflight.dto;

import com.iqvia.preflight.model.ReadinessScore;

import java.time.Instant;

public class CheckHistoryItem {

    private final Long id;
    private final String fileName;
    private final int totalRows;
    private final ReadinessScore score;
    private final String summary;
    private final Instant checkedAt;

    public CheckHistoryItem(Long id, String fileName, int totalRows, ReadinessScore score, String summary, Instant checkedAt) {
        this.id = id;
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.score = score;
        this.summary = summary;
        this.checkedAt = checkedAt;
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public int getTotalRows() { return totalRows; }
    public ReadinessScore getScore() { return score; }
    public String getSummary() { return summary; }
    public Instant getCheckedAt() { return checkedAt; }
}
