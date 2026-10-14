package com.iqvia.preflight.entity;

import com.iqvia.preflight.model.ReadinessScore;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "check_history")
public class CheckHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;
    private int totalRows;

    @Enumerated(EnumType.STRING)
    private ReadinessScore score;

    @Column(length = 500)
    private String summary;

    @Column(columnDefinition = "CLOB")
    private String resultJson;

    private Instant checkedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public ReadinessScore getScore() { return score; }
    public void setScore(ReadinessScore score) { this.score = score; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant checkedAt) { this.checkedAt = checkedAt; }
}
