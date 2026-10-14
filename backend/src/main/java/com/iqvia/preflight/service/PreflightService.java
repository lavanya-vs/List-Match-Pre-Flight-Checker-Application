package com.iqvia.preflight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iqvia.preflight.dto.AiAnalysis;
import com.iqvia.preflight.dto.AdExportColumnClash;
import com.iqvia.preflight.dto.AdExportClashCheckResponse;
import com.iqvia.preflight.dto.CheckHistoryItem;
import com.iqvia.preflight.dto.ClientListHeaderPreview;
import com.iqvia.preflight.dto.ColumnMappingEntry;
import com.iqvia.preflight.dto.HeadersPreviewResponse;
import com.iqvia.preflight.dto.PreflightCheckResponse;
import com.iqvia.preflight.dto.PreflightResult;
import com.iqvia.preflight.entity.CheckHistory;
import com.iqvia.preflight.model.StandardField;
import com.iqvia.preflight.repository.CheckHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Main upload flow: parse file → map columns → run rules → check export-name clashes → write summary text.
 */
@Service
public class PreflightService {

    private static final Logger LOG = LoggerFactory.getLogger(PreflightService.class);
    private static final int SLIM_HISTORY_ROW_THRESHOLD = 10_000;

    private final FileParserService fileParser;
    private final ColumnMappingService columnMappingService;
    private final RulesEngineService rulesEngine;
    private final AiAnalysisService aiAnalysisService;
    private final CheckHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final AdExportColumnClashService adExportClashService;

    public PreflightService(
            FileParserService fileParser,
            ColumnMappingService columnMappingService,
            RulesEngineService rulesEngine,
            AiAnalysisService aiAnalysisService,
            CheckHistoryRepository historyRepository,
            ObjectMapper objectMapper,
            AdExportColumnClashService adExportClashService
    ) {
        this.fileParser = fileParser;
        this.columnMappingService = columnMappingService;
        this.rulesEngine = rulesEngine;
        this.aiAnalysisService = aiAnalysisService;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
        this.adExportClashService = adExportClashService;
    }

    public PreflightCheckResponse check(MultipartFile file, boolean saveHistory) throws IOException {
        return check(file, saveHistory, null);
    }

    public PreflightCheckResponse check(
            MultipartFile file,
            boolean saveHistory,
            List<ColumnMappingEntry> userMappings
    ) throws IOException {
        FileParserService.ParsedFile parsed = fileParser.parse(file);
        RowSource rowSource = fileParser.rowSourceFor(file, parsed);
        PreflightCheckResponse response = analyze(file.getOriginalFilename(), parsed, rowSource, userMappings);
        if (saveHistory) {
            saveHistory(response);
        }
        return response;
    }

    public HeadersPreviewResponse previewHeaders(MultipartFile file) throws IOException {
        FileParserService.ParsedFile parsed = fileParser.parse(file);
        List<String> headers = parsed.getHeaders();
        List<ColumnMappingEntry> suggested = columnMappingService.suggestMappings(
                headers, parsed.getSampleRows());
        return new HeadersPreviewResponse(
                file.getOriginalFilename(),
                headers,
                suggested,
                columnMappingService.availableMappedNames()
        );
    }

    public AdExportClashCheckResponse checkAdExportClashes(List<ColumnMappingEntry> mappings) {
        return adExportClashService.checkMappings(mappings);
    }

    public List<ColumnMappingEntry> parseColumnMappings(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, new TypeReference<List<ColumnMappingEntry>>() {});
    }

    public PreflightCheckResponse checkStoredFile(
            String storedPath,
            String fileName,
            boolean saveHistory,
            IntConsumer progress
    ) throws IOException {
        return checkStoredFile(storedPath, fileName, saveHistory, progress, null);
    }

    public PreflightCheckResponse checkStoredFile(
            String storedPath,
            String fileName,
            boolean saveHistory,
            IntConsumer progress,
            List<ColumnMappingEntry> userMappings
    ) throws IOException {
        Path path = Path.of(storedPath);
        progress.accept(10);
        FileParserService.ParsedFile parsed = fileParser.parse(path, fileName);
        progress.accept(25);

        progress.accept(35);
        RowSource rowSource = fileParser.rowSourceFor(path, parsed, fileName);
        progress.accept(45);
        PreflightCheckResponse response = analyze(fileName, parsed, rowSource, userMappings);
        progress.accept(85);
        if (saveHistory) {
            saveHistory(response);
        }
        progress.accept(100);
        return response;
    }

    private PreflightCheckResponse analyze(
            String fileName,
            FileParserService.ParsedFile parsed,
            RowSource rowSource,
            List<ColumnMappingEntry> userMappings
    ) {
        List<String> headers = parsed.getHeaders();
        List<Map<String, String>> sampleRows = parsed.getSampleRows();
        int totalRows = parsed.getTotalRows();

        // Use the user's column picks when provided; otherwise guess from header names and sample values.
        List<ColumnMappingEntry> mappingPlan = userMappings != null && !userMappings.isEmpty()
                ? userMappings
                : columnMappingService.suggestMappings(headers, sampleRows);

        Map<StandardField, String> detected = columnMappingService.toDetectedColumns(mappingPlan);
        List<String> unmapped = columnMappingService.unmappedHeaders(mappingPlan);

        // Core check: coverage, score, row warnings (including duplicate NPIs).
        PreflightResult result = rulesEngine.evaluate(
                fileName, totalRows, detected, unmapped, rowSource);

        List<ClientListHeaderPreview> simulatedHeaders =
                adExportClashService.simulateClientListHeaders(mappingPlan);
        List<AdExportColumnClash> clashes = adExportClashService.detectFromMappings(mappingPlan);
        if (userMappings == null || userMappings.isEmpty()) {
            for (AdExportColumnClash scenarioClash : adExportClashService.detect(headers, detected)) {
                if (clashes.stream().noneMatch(c -> c.getAdExportName().equals(scenarioClash.getAdExportName()))) {
                    clashes = new java.util.ArrayList<>(clashes);
                    clashes.add(scenarioClash);
                }
            }
        }

        result = result.withAdExportQc(mappingPlan, simulatedHeaders, clashes);
        // Human-readable analysis (optional LLM, then Java templates).
        AiAnalysis analysis = aiAnalysisService.analyze(headers, sampleRows, totalRows, result);
        return new PreflightCheckResponse(result, analysis);
    }

    public List<CheckHistoryItem> getRecentHistory() {
        return historyRepository.findTop20ByOrderByCheckedAtDesc().stream()
                .map(h -> new CheckHistoryItem(
                        h.getId(), h.getFileName(), h.getTotalRows(),
                        h.getScore(), h.getSummary(), h.getCheckedAt()))
                .collect(Collectors.toList());
    }

    private void saveHistory(PreflightCheckResponse response) {
        PreflightResult result = response.getCheck();
        CheckHistory entry = new CheckHistory();
        entry.setFileName(result.getFileName());
        entry.setTotalRows(result.getTotalRows());
        entry.setScore(result.getScore());
        entry.setSummary(result.getSummary());
        try {
            if (result.getTotalRows() > SLIM_HISTORY_ROW_THRESHOLD) {
                entry.setResultJson(objectMapper.writeValueAsString(buildSlimHistory(response)));
            } else {
                entry.setResultJson(objectMapper.writeValueAsString(response));
            }
        } catch (JsonProcessingException e) {
            entry.setResultJson("{}");
        }
        try {
            historyRepository.save(entry);
        } catch (Exception e) {
            LOG.warn("Could not save check history for {}: {}", result.getFileName(), e.getMessage());
        }
    }

    private Map<String, Object> buildSlimHistory(PreflightCheckResponse response) {
        PreflightResult check = response.getCheck();
        AiAnalysis analysis = response.getAnalysis();
        Map<String, Object> slim = new LinkedHashMap<>();
        slim.put("fileName", check.getFileName());
        slim.put("totalRows", check.getTotalRows());
        slim.put("score", check.getScore());
        slim.put("summary", check.getSummary());
        if (analysis != null) {
            slim.put("executiveSummary", analysis.getExecutiveSummary());
            slim.put("recommendedMatchStrategy", analysis.getRecommendedMatchStrategy());
        }
        return slim;
    }
}
