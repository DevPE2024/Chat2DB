package ai.chat2db.spi.sql;

import ai.chat2db.spi.model.ExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Data Export Service
 * Provides comprehensive data export functionality with support for multiple formats,
 * streaming, compression, and asynchronous processing.
 * 
 * @author Chat2DB Team
 */
@Slf4j
public class DataExportService {
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Export Options
     */
    @Data
    @Builder
    public static class ExportOptions {
        @Builder.Default
        private ExportFormat format = ExportFormat.CSV;
        
        @Builder.Default
        private String delimiter = ",";
        
        @Builder.Default
        private String quoteChar = "\"";
        
        @Builder.Default
        private String escapeChar = "\\";
        
        @Builder.Default
        private String lineTerminator = "\n";
        
        @Builder.Default
        private boolean includeHeaders = true;
        
        @Builder.Default
        private boolean compressOutput = false;
        
        @Builder.Default
        private String encoding = "UTF-8";
        
        @Builder.Default
        private int batchSize = 1000;
        
        @Builder.Default
        private boolean streamingMode = false;
        
        @Builder.Default
        private String dateFormat = "yyyy-MM-dd HH:mm:ss";
        
        @Builder.Default
        private String nullValue = "";
        
        @Builder.Default
        private boolean prettyPrint = false;
        
        private List<String> includeColumns;
        private List<String> excludeColumns;
        private Map<String, String> columnMappings;
        private String whereClause;
        private String orderBy;
        private Integer maxRows;
    }
    
    /**
     * Export Formats
     */
    public enum ExportFormat {
        CSV, JSON, XML, EXCEL, SQL_INSERT, SQL_UPDATE, PARQUET, AVRO
    }
    
    /**
     * Export Result
     */
    @Data
    @Builder
    public static class ExportResult {
        private boolean success;
        private String message;
        private String filePath;
        private long recordsExported;
        private long fileSizeBytes;
        private long executionTimeMs;
        private String exportId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Map<String, Object> metadata;
    }
    
    /**
     * Export data from ResultSet
     */
    public ExportResult exportData(ResultSet resultSet, String outputPath, ExportOptions options) {
        long startTime = System.currentTimeMillis();
        String exportId = "EXPORT_" + System.currentTimeMillis();
        
        try {
            log.info("Starting data export {} to {} in {} format", exportId, outputPath, options.getFormat());
            
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            
            long recordsExported = 0;
            
            switch (options.getFormat()) {
                case CSV:
                    recordsExported = exportToCsv(resultSet, outputFile, options);
                    break;
                case JSON:
                    recordsExported = exportToJson(resultSet, outputFile, options);
                    break;
                case XML:
                    recordsExported = exportToXml(resultSet, outputFile, options);
                    break;
                case SQL_INSERT:
                    recordsExported = exportToSqlInsert(resultSet, outputFile, options);
                    break;
                case SQL_UPDATE:
                    recordsExported = exportToSqlUpdate(resultSet, outputFile, options);
                    break;
                default:
                    throw new UnsupportedOperationException("Export format not supported: " + options.getFormat());
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            long fileSize = outputFile.length();
            
            log.info("Export {} completed: {} records, {} bytes, {}ms", 
                    exportId, recordsExported, fileSize, executionTime);
            
            return ExportResult.builder()
                    .success(true)
                    .message("Export completed successfully")
                    .filePath(outputPath)
                    .recordsExported(recordsExported)
                    .fileSizeBytes(fileSize)
                    .executionTimeMs(executionTime)
                    .exportId(exportId)
                    .startTime(LocalDateTime.now().minusNanos(executionTime * 1_000_000))
                    .endTime(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Export {} failed", exportId, e);
            return ExportResult.builder()
                    .success(false)
                    .message("Export failed: " + e.getMessage())
                    .exportId(exportId)
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
    
    /**
     * Export to CSV format
     */
    private long exportToCsv(ResultSet resultSet, File outputFile, ExportOptions options) throws SQLException, IOException {
        try (Writer writer = createWriter(outputFile, options)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Write headers
            if (options.isIncludeHeaders()) {
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) writer.write(options.getDelimiter());
                    writer.write(escapeValue(metaData.getColumnName(i), options));
                }
                writer.write(options.getLineTerminator());
            }
            
            // Write data
            long recordCount = 0;
            while (resultSet.next() && (options.getMaxRows() == null || recordCount < options.getMaxRows())) {
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) writer.write(options.getDelimiter());
                    
                    Object value = resultSet.getObject(i);
                    String stringValue = formatValue(value, options);
                    writer.write(escapeValue(stringValue, options));
                }
                writer.write(options.getLineTerminator());
                recordCount++;
                
                if (recordCount % options.getBatchSize() == 0) {
                    writer.flush();
                }
            }
            
            return recordCount;
        }
    }
    
    /**
     * Export to JSON format
     */
    private long exportToJson(ResultSet resultSet, File outputFile, ExportOptions options) throws SQLException, IOException {
        try (Writer writer = createWriter(outputFile, options)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            writer.write("[\n");
            
            long recordCount = 0;
            boolean first = true;
            
            while (resultSet.next() && (options.getMaxRows() == null || recordCount < options.getMaxRows())) {
                if (!first) {
                    writer.write(",\n");
                }
                first = false;
                
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    record.put(columnName, value);
                }
                
                String json = options.isPrettyPrint() ? 
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(record) :
                        objectMapper.writeValueAsString(record);
                        
                writer.write(json);
                recordCount++;
                
                if (recordCount % options.getBatchSize() == 0) {
                    writer.flush();
                }
            }
            
            writer.write("\n]");
            return recordCount;
        }
    }
    
    /**
     * Export to XML format
     */
    private long exportToXml(ResultSet resultSet, File outputFile, ExportOptions options) throws SQLException, IOException {
        try (Writer writer = createWriter(outputFile, options)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            writer.write("<?xml version=\"1.0\" encoding=\"" + options.getEncoding() + "\"?>\n");
            writer.write("<data>\n");
            
            long recordCount = 0;
            
            while (resultSet.next() && (options.getMaxRows() == null || recordCount < options.getMaxRows())) {
                writer.write("  <record>\n");
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    String stringValue = formatValue(value, options);
                    
                    writer.write("    <" + columnName + ">");
                    writer.write(escapeXml(stringValue));
                    writer.write("</" + columnName + ">\n");
                }
                
                writer.write("  </record>\n");
                recordCount++;
                
                if (recordCount % options.getBatchSize() == 0) {
                    writer.flush();
                }
            }
            
            writer.write("</data>\n");
            return recordCount;
        }
    }
    
    /**
     * Export to SQL INSERT format
     */
    private long exportToSqlInsert(ResultSet resultSet, File outputFile, ExportOptions options) throws SQLException, IOException {
        try (Writer writer = createWriter(outputFile, options)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Build column list
            StringBuilder columnList = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) columnList.append(", ");
                columnList.append(metaData.getColumnName(i));
            }
            
            String tableName = metaData.getTableName(1);
            if (tableName == null || tableName.isEmpty()) {
                tableName = "exported_data";
            }
            
            long recordCount = 0;
            
            while (resultSet.next() && (options.getMaxRows() == null || recordCount < options.getMaxRows())) {
                writer.write("INSERT INTO " + tableName + " (" + columnList + ") VALUES (");
                
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) writer.write(", ");
                    
                    Object value = resultSet.getObject(i);
                    writer.write(formatSqlValue(value, metaData.getColumnType(i)));
                }
                
                writer.write(");\n");
                recordCount++;
                
                if (recordCount % options.getBatchSize() == 0) {
                    writer.flush();
                }
            }
            
            return recordCount;
        }
    }
    
    /**
     * Export to SQL UPDATE format
     */
    private long exportToSqlUpdate(ResultSet resultSet, File outputFile, ExportOptions options) throws SQLException, IOException {
        try (Writer writer = createWriter(outputFile, options)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            String tableName = metaData.getTableName(1);
            if (tableName == null || tableName.isEmpty()) {
                tableName = "exported_data";
            }
            
            long recordCount = 0;
            
            while (resultSet.next() && (options.getMaxRows() == null || recordCount < options.getMaxRows())) {
                writer.write("UPDATE " + tableName + " SET ");
                
                // Assume first column is the key
                Object keyValue = resultSet.getObject(1);
                String keyColumn = metaData.getColumnName(1);
                
                for (int i = 2; i <= columnCount; i++) {
                    if (i > 2) writer.write(", ");
                    
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    
                    writer.write(columnName + " = " + formatSqlValue(value, metaData.getColumnType(i)));
                }
                
                writer.write(" WHERE " + keyColumn + " = " + formatSqlValue(keyValue, metaData.getColumnType(1)) + ";\n");
                recordCount++;
                
                if (recordCount % options.getBatchSize() == 0) {
                    writer.flush();
                }
            }
            
            return recordCount;
        }
    }
    
    /**
     * Create writer with compression support
     */
    private Writer createWriter(File outputFile, ExportOptions options) throws IOException {
        OutputStream outputStream = new FileOutputStream(outputFile);
        
        if (options.isCompressOutput()) {
            outputStream = new GZIPOutputStream(outputStream);
        }
        
        return new OutputStreamWriter(outputStream, options.getEncoding());
    }
    
    /**
     * Format value according to options
     */
    private String formatValue(Object value, ExportOptions options) {
        if (value == null) {
            return options.getNullValue();
        }
        
        if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(options.getDateFormat());
            return formatter.format(((java.sql.Timestamp) value).toLocalDateTime());
        }
        
        return value.toString();
    }
    
    /**
     * Escape CSV value
     */
    private String escapeValue(String value, ExportOptions options) {
        if (value == null) {
            return options.getNullValue();
        }
        
        if (value.contains(options.getDelimiter()) || value.contains(options.getQuoteChar()) || value.contains("\n")) {
            return options.getQuoteChar() + value.replace(options.getQuoteChar(), 
                    options.getEscapeChar() + options.getQuoteChar()) + options.getQuoteChar();
        }
        
        return value;
    }
    
    /**
     * Escape XML value
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
    
    /**
     * Format SQL value
     */
    private String formatSqlValue(Object value, int sqlType) {
        if (value == null) {
            return "NULL";
        }
        
        switch (sqlType) {
            case java.sql.Types.VARCHAR:
            case java.sql.Types.CHAR:
            case java.sql.Types.LONGVARCHAR:
            case java.sql.Types.DATE:
            case java.sql.Types.TIME:
            case java.sql.Types.TIMESTAMP:
                return "'" + value.toString().replace("'", "''") + "'";
            default:
                return value.toString();
        }
    }
    
    /**
     * Async export
     */
    public CompletableFuture<ExportResult> exportDataAsync(ResultSet resultSet, String outputPath, ExportOptions options) {
        return CompletableFuture.supplyAsync(() -> exportData(resultSet, outputPath, options), executorService);
    }
    
    /**
     * Create default export options
     */
    public static ExportOptions defaultOptions() {
        return ExportOptions.builder().build();
    }
    
    /**
     * Create CSV export options
     */
    public static ExportOptions csvOptions() {
        return ExportOptions.builder()
                .format(ExportFormat.CSV)
                .includeHeaders(true)
                .build();
    }
    
    /**
     * Create JSON export options
     */
    public static ExportOptions jsonOptions() {
        return ExportOptions.builder()
                .format(ExportFormat.JSON)
                .prettyPrint(true)
                .build();
    }
    
    /**
     * Create compressed export options
     */
    public static ExportOptions compressedOptions(ExportFormat format) {
        return ExportOptions.builder()
                .format(format)
                .compressOutput(true)
                .build();
    }
}