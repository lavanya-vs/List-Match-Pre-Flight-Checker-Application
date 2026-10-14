package com.iqvia.preflight.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses CSV and Excel uploads with tolerant handling for real client files:
 * title rows above headers, trailing blank columns, duplicate/empty headers,
 * numeric NPI/zip cells, and multi-sheet workbooks.
 */
@Service
public class FileParserService {

    private static final int HEADER_SCAN_LIMIT = 30;
    private static final int SAMPLE_ROW_LIMIT = 500;

    public ParsedFile parse(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new IOException("File name is missing");
        }
        return parse(openStream(file), name);
    }

    public ParsedFile parse(java.nio.file.Path path, String fileName) throws IOException {
        return parse(openStream(path), fileName);
    }

    private ParsedFile parse(
            java.util.function.Supplier<java.io.InputStream> streamSupplier,
            String fileName
    ) throws IOException {
        try (java.io.InputStream in = streamSupplier.get()) {
            return parse(in, fileName, streamSupplier);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private ParsedFile parse(
            java.io.InputStream inputStream,
            String fileName,
            java.util.function.Supplier<java.io.InputStream> streamSupplier
    ) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return parseCsv(inputStream, streamSupplier);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(inputStream, streamSupplier);
        }
        throw new IOException("Unsupported file type. Please upload CSV or Excel (.xlsx).");
    }

    private ParsedFile parseCsv(java.io.InputStream inputStream, java.util.function.Supplier<java.io.InputStream> streamSupplier) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String[]> scanBuffer = new ArrayList<>();
            String[] row;
            while (scanBuffer.size() < HEADER_SCAN_LIMIT && (row = reader.readNext()) != null) {
                scanBuffer.add(row);
            }
            if (scanBuffer.isEmpty()) {
                throw new IOException("File is empty");
            }

            int headerIndex = detectHeaderRowIndex(scanBuffer);
            String[] headerCells = scanBuffer.get(headerIndex);
            if (headerCells.length > 0 && headerCells[0] != null) {
                headerCells[0] = stripBom(headerCells[0]);
            }
            List<String> headers = sanitizeHeaders(Arrays.asList(headerCells));

            List<Map<String, String>> sampleRows = new ArrayList<>();
            int totalRows = ingestDataRow(headers, scanBuffer, headerIndex, sampleRows, 0);

            while ((row = reader.readNext()) != null) {
                if (isBlankRow(row)) {
                    continue;
                }
                totalRows++;
                if (sampleRows.size() < SAMPLE_ROW_LIMIT) {
                    sampleRows.add(toRowMap(headers, padValues(row, headers.size())));
                }
            }
            return new ParsedFile(headers, headerIndex, sampleRows, totalRows);
        } catch (CsvException e) {
            throw new IOException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    private int ingestDataRow(
            List<String> headers,
            List<String[]> scanBuffer,
            int headerIndex,
            List<Map<String, String>> sampleRows,
            int totalRows
    ) {
        for (int i = headerIndex + 1; i < scanBuffer.size(); i++) {
            String[] values = scanBuffer.get(i);
            if (isBlankRow(values)) {
                continue;
            }
            totalRows++;
            if (sampleRows.size() < SAMPLE_ROW_LIMIT) {
                sampleRows.add(toRowMap(headers, padValues(values, headers.size())));
            }
        }
        return totalRows;
    }

    /** Re-stream CSV data rows for scoring large files (second pass). */
    public RowSource csvRowSource(java.util.function.Supplier<java.io.InputStream> streamSupplier, ParsedFile parsed) {
        if (parsed.getHeaderRowIndex() < 0) {
            throw new IllegalStateException("Cannot re-stream a file without header metadata.");
        }
        return consumer -> {
            try (CSVReader reader = new CSVReader(
                    new InputStreamReader(streamSupplier.get(), StandardCharsets.UTF_8))) {
                int line = 0;
                int rowIndex = 0;
                String[] cells;
                while ((cells = reader.readNext()) != null) {
                    if (line <= parsed.getHeaderRowIndex()) {
                        line++;
                        continue;
                    }
                    if (isBlankRow(cells)) {
                        line++;
                        continue;
                    }
                    consumer.accept(
                            toRowMap(parsed.getHeaders(), padValues(cells, parsed.getHeaders().size())),
                            rowIndex++);
                    line++;
                }
            } catch (IOException | CsvException e) {
                throw new UncheckedIOException(
                        e instanceof IOException ? (IOException) e : new IOException(e.getMessage(), e));
            }
        };
    }

    public RowSource inMemoryRowSource(List<Map<String, String>> rows) {
        return consumer -> {
            for (int i = 0; i < rows.size(); i++) {
                consumer.accept(rows.get(i), i);
            }
        };
    }

    /** Re-stream Excel data rows for scoring. */
    public RowSource excelRowSource(java.util.function.Supplier<java.io.InputStream> streamSupplier, ParsedFile parsed) {
        return consumer -> {
            try (Workbook workbook = WorkbookFactory.create(streamSupplier.get())) {
                DataFormatter formatter = new DataFormatter(Locale.US);
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                Sheet sheet = selectBestSheet(workbook);
                int rowIndex = 0;
                for (int i = parsed.getHeaderRowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) {
                        continue;
                    }
                    String[] values = new String[parsed.getHeaders().size()];
                    boolean blank = true;
                    for (int c = 0; c < parsed.getHeaders().size(); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values[c] = cell == null ? "" : getCellValue(cell, formatter, evaluator);
                        if (values[c] != null && !values[c].trim().isEmpty()) {
                            blank = false;
                        }
                    }
                    if (blank) {
                        continue;
                    }
                    consumer.accept(toRowMap(parsed.getHeaders(), values), rowIndex++);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public RowSource rowSourceFor(MultipartFile file, ParsedFile parsed) {
        return rowSourceFor(openStream(file), parsed, file.getOriginalFilename());
    }

    public RowSource rowSourceFor(java.nio.file.Path path, ParsedFile parsed, String fileName) {
        return rowSourceFor(openStream(path), parsed, fileName);
    }

    private java.util.function.Supplier<java.io.InputStream> openStream(MultipartFile file) {
        return () -> {
            try {
                return file.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private java.util.function.Supplier<java.io.InputStream> openStream(java.nio.file.Path path) {
        return () -> {
            try {
                return java.nio.file.Files.newInputStream(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private RowSource rowSourceFor(
            java.util.function.Supplier<java.io.InputStream> streamSupplier,
            ParsedFile parsed,
            String fileName
    ) {
        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            return csvRowSource(streamSupplier, parsed);
        }
        return excelRowSource(streamSupplier, parsed);
    }

    private ParsedFile parseExcel(java.io.InputStream inputStream, java.util.function.Supplier<java.io.InputStream> streamSupplier) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.US);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = selectBestSheet(workbook);

            List<String[]> scannedRows = new ArrayList<>();
            int rowLimit = Math.min(sheet.getLastRowNum() + 1, HEADER_SCAN_LIMIT + 5);
            for (int i = 0; i < rowLimit; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    scannedRows.add(new String[0]);
                    continue;
                }
                int lastCell = Math.max(row.getLastCellNum(), (short) 0);
                String[] cells = new String[lastCell];
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cells[c] = getCellValue(cell, formatter, evaluator);
                }
                scannedRows.add(cells);
            }

            if (scannedRows.isEmpty()) {
                throw new IOException("File is empty");
            }

            int headerIndex = detectHeaderRowIndex(scannedRows);
            List<String> headers = sanitizeHeaders(Arrays.asList(scannedRows.get(headerIndex)));

            List<Map<String, String>> sampleRows = new ArrayList<>();
            int totalRows = 0;
            for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String[] values = new String[headers.size()];
                boolean blank = true;
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values[c] = cell == null ? "" : getCellValue(cell, formatter, evaluator);
                    if (values[c] != null && !values[c].trim().isEmpty()) {
                        blank = false;
                    }
                }
                if (blank) {
                    continue;
                }
                totalRows++;
                if (sampleRows.size() < SAMPLE_ROW_LIMIT) {
                    sampleRows.add(toRowMap(headers, values));
                }
            }
            return new ParsedFile(headers, headerIndex, sampleRows, totalRows);
        }
    }

    /** Pick the sheet that looks most like a data table (most non-empty rows). */
    private Sheet selectBestSheet(Workbook workbook) throws IOException {
        Sheet best = workbook.getSheetAt(0);
        int bestScore = scoreSheet(best);
        for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
            Sheet candidate = workbook.getSheetAt(i);
            int score = scoreSheet(candidate);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (bestScore <= 0) {
            throw new IOException("Workbook contains no readable data.");
        }
        return best;
    }

    private int scoreSheet(Sheet sheet) {
        int score = 0;
        int limit = Math.min(sheet.getLastRowNum() + 1, HEADER_SCAN_LIMIT);
        for (int i = 0; i < limit; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            int filled = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() != CellType.BLANK) {
                    filled++;
                }
            }
            if (filled >= 2) {
                score += filled;
            }
        }
        return score;
    }

    /**
     * Find the row most likely to be column headers — not a title row or data row.
     * Scores rows with several short text labels higher than rows of mostly numbers.
     */
    int detectHeaderRowIndex(List<String[]> rows) throws IOException {
        int bestIndex = 0;
        int bestScore = Integer.MIN_VALUE;
        int limit = Math.min(rows.size(), HEADER_SCAN_LIMIT);

        for (int i = 0; i < limit; i++) {
            int score = scoreHeaderRow(rows.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        if (bestScore <= 0) {
            throw new IOException("Could not find a header row in the first "
                    + HEADER_SCAN_LIMIT + " rows. Make sure column names are present.");
        }
        return bestIndex;
    }

    private int scoreHeaderRow(String[] cells) {
        if (cells == null || cells.length == 0) {
            return 0;
        }

        int nonEmpty = 0;
        int headerLike = 0;
        int dataLike = 0;

        for (String raw : cells) {
            String value = raw == null ? "" : stripBom(raw).trim();
            if (value.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (looksLikeDataValue(value)) {
                dataLike++;
            } else if (looksLikeHeaderLabel(value)) {
                headerLike++;
            }
        }

        if (nonEmpty < 2) {
            return 0;
        }
        return headerLike * 12 + nonEmpty * 3 - dataLike * 8;
    }

    private boolean looksLikeHeaderLabel(String value) {
        if (value.length() > 80) {
            return false;
        }
        if (value.matches(".*[A-Za-z].*")) {
            return !value.matches("^\\d+$");
        }
        return false;
    }

    private boolean looksLikeDataValue(String value) {
        if (value.matches("^\\d{10}$")) {
            return true;
        }
        if (value.matches("^\\d{5}(-\\d{4})?$")) {
            return true;
        }
        if (value.matches("^[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}$")) {
            return true;
        }
        return value.matches("^\\d{4,}$");
    }

    private List<String> sanitizeHeaders(List<String> rawHeaders) throws IOException {
        List<String> trimmed = trimTrailingEmptyHeaders(rawHeaders);
        if (trimmed.isEmpty()) {
            throw new IOException("Header row is empty.");
        }
        List<String> headers = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < trimmed.size(); i++) {
            String header = stripBom(trimmed.get(i) == null ? "" : trimmed.get(i)).trim();
            if (header.isEmpty()) {
                header = "Column_" + (i + 1);
            }
            String key = header.toLowerCase(Locale.ROOT);
            int count = seen.getOrDefault(key, 0) + 1;
            seen.put(key, count);
            if (count > 1) {
                header = header + " (" + count + ")";
            }
            headers.add(header);
        }
        return headers;
    }

    private List<String> trimTrailingEmptyHeaders(List<String> rawHeaders) {
        int lastNonEmpty = -1;
        for (int i = 0; i < rawHeaders.size(); i++) {
            String value = rawHeaders.get(i) == null ? "" : stripBom(rawHeaders.get(i)).trim();
            if (!value.isEmpty()) {
                lastNonEmpty = i;
            }
        }
        if (lastNonEmpty < 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(rawHeaders.subList(0, lastNonEmpty + 1));
    }

    private String[] padValues(String[] values, int width) {
        if (values.length >= width) {
            if (values.length > width) {
                return Arrays.copyOf(values, width);
            }
            return values;
        }
        return Arrays.copyOf(values, width);
    }

    private Map<String, String> toRowMap(List<String> headers, String[] values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = i < values.length ? values[i] : "";
            row.put(headers.get(i), value == null ? "" : value.trim());
        }
        return row;
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }

    private boolean isBlankRow(String[] values) {
        if (values == null) {
            return true;
        }
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Read cell text without losing leading zeros or turning 10-digit NPIs into scientific notation.
     */
    private String getCellValue(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return formatter.formatCellValue(cell, evaluator).trim();
            }
            double numeric = cell.getNumericCellValue();
            if (Math.rint(numeric) == numeric) {
                BigDecimal asLong = BigDecimal.valueOf(numeric).setScale(0, java.math.RoundingMode.UNNECESSARY);
                String digits = asLong.toPlainString();
                if (digits.length() == 10 || digits.length() == 5 || digits.length() == 9) {
                    return digits;
                }
                if (digits.length() == 4) {
                    return String.format(Locale.ROOT, "%05d", asLong.intValue());
                }
            }
        }

        return formatter.formatCellValue(cell, evaluator).trim();
    }

    public static class ParsedFile {
        private final List<String> headers;
        private final int headerRowIndex;
        private final List<Map<String, String>> sampleRows;
        private final int totalRows;

        public ParsedFile(List<String> headers, int headerRowIndex, List<Map<String, String>> sampleRows, int totalRows) {
            this.headers = headers;
            this.headerRowIndex = headerRowIndex;
            this.sampleRows = sampleRows;
            this.totalRows = totalRows;
        }

        public List<String> getHeaders() { return headers; }
        public int getHeaderRowIndex() { return headerRowIndex; }
        public List<Map<String, String>> getSampleRows() { return sampleRows; }
        public int getTotalRows() { return totalRows; }

        /** Sample rows for column detection; same as {@link #getSampleRows()}. */
        public List<Map<String, String>> getRows() { return sampleRows; }
    }
}
