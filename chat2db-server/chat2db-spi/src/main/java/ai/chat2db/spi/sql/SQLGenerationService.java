package ai.chat2db.spi.sql;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Serviço de Geração de SQL para Chat2DB
 * Converte linguagem natural em consultas SQL otimizadas usando IA
 */
public class SQLGenerationService {
    
    private final SQLTemplateRepository templateRepository;
    private final SchemaAnalyzer schemaAnalyzer;
    private final QueryValidator queryValidator;
    private final SQLOptimizer sqlOptimizer;
    private final CacheManager cacheManager;
    private final GenerationMetrics metrics;
    private final ExecutorService executorService;
    private final AtomicBoolean isInitialized;
    
    public SQLGenerationService() {
        this.templateRepository = new SQLTemplateRepository();
        this.schemaAnalyzer = new SchemaAnalyzer();
        this.queryValidator = new QueryValidator();
        this.sqlOptimizer = new SQLOptimizer();
        this.cacheManager = new CacheManager();
        this.metrics = new GenerationMetrics();
        this.executorService = Executors.newFixedThreadPool(8);
        this.isInitialized = new AtomicBoolean(false);
        
        initializeService();
    }
    
    /**
     * Tipos de consulta SQL
     */
    public enum QueryType {
        SELECT("Consulta de seleção", "SELECT"),
        INSERT("Inserção de dados", "INSERT"),
        UPDATE("Atualização de dados", "UPDATE"),
        DELETE("Exclusão de dados", "DELETE"),
        CREATE_TABLE("Criação de tabela", "CREATE TABLE"),
        ALTER_TABLE("Alteração de tabela", "ALTER TABLE"),
        DROP_TABLE("Exclusão de tabela", "DROP TABLE"),
        CREATE_INDEX("Criação de índice", "CREATE INDEX"),
        CREATE_VIEW("Criação de view", "CREATE VIEW"),
        STORED_PROCEDURE("Procedimento armazenado", "CREATE PROCEDURE"),
        FUNCTION("Função", "CREATE FUNCTION"),
        TRIGGER("Gatilho", "CREATE TRIGGER"),
        TRANSACTION("Transação", "BEGIN TRANSACTION"),
        CTE("Common Table Expression", "WITH"),
        WINDOW_FUNCTION("Função de janela", "WINDOW"),
        PIVOT("Tabela dinâmica", "PIVOT"),
        RECURSIVE("Consulta recursiva", "RECURSIVE"),
        ANALYTICAL("Consulta analítica", "ANALYTICAL");
        
        private final String description;
        private final String keyword;
        
        QueryType(String description, String keyword) {
            this.description = description;
            this.keyword = keyword;
        }
        
        public String getDescription() { return description; }
        public String getKeyword() { return keyword; }
    }
    
    /**
     * Complexidade da consulta
     */
    public enum QueryComplexity {
        SIMPLE("Simples", 1),
        MODERATE("Moderada", 2),
        COMPLEX("Complexa", 3),
        ADVANCED("Avançada", 4),
        EXPERT("Especialista", 5);
        
        private final String description;
        private final int level;
        
        QueryComplexity(String description, int level) {
            this.description = description;
            this.level = level;
        }
        
        public String getDescription() { return description; }
        public int getLevel() { return level; }
    }
    
    /**
     * Requisição de geração SQL
     */
    public static class SQLGenerationRequest {
        private String requestId;
        private String naturalLanguageQuery;
        private String databaseType;
        private String schemaName;
        private List<TableSchema> availableTables;
        private Map<String, Object> context;
        private GenerationOptions options;
        private String userId;
        private String sessionId;
        private LocalDateTime timestamp;
        private boolean useCache;
        private boolean optimizeQuery;
        private boolean validateSyntax;
        private int maxSuggestions;
        
        public SQLGenerationRequest() {
            this.requestId = UUID.randomUUID().toString();
            this.availableTables = new ArrayList<>();
            this.context = new HashMap<>();
            this.options = new GenerationOptions();
            this.timestamp = LocalDateTime.now();
            this.useCache = true;
            this.optimizeQuery = true;
            this.validateSyntax = true;
            this.maxSuggestions = 5;
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public String getNaturalLanguageQuery() { return naturalLanguageQuery; }
        public void setNaturalLanguageQuery(String naturalLanguageQuery) { this.naturalLanguageQuery = naturalLanguageQuery; }
        
        public String getDatabaseType() { return databaseType; }
        public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
        
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        
        public List<TableSchema> getAvailableTables() { return availableTables; }
        public void setAvailableTables(List<TableSchema> availableTables) { this.availableTables = availableTables; }
        
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        
        public GenerationOptions getOptions() { return options; }
        public void setOptions(GenerationOptions options) { this.options = options; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public boolean isUseCache() { return useCache; }
        public void setUseCache(boolean useCache) { this.useCache = useCache; }
        
        public boolean isOptimizeQuery() { return optimizeQuery; }
        public void setOptimizeQuery(boolean optimizeQuery) { this.optimizeQuery = optimizeQuery; }
        
        public boolean isValidateSyntax() { return validateSyntax; }
        public void setValidateSyntax(boolean validateSyntax) { this.validateSyntax = validateSyntax; }
        
        public int getMaxSuggestions() { return maxSuggestions; }
        public void setMaxSuggestions(int maxSuggestions) { this.maxSuggestions = maxSuggestions; }
    }
    
    /**
     * Resposta da geração SQL
     */
    public static class SQLGenerationResponse {
        private String requestId;
        private List<GeneratedSQL> sqlSuggestions;
        private QueryType detectedQueryType;
        private QueryComplexity complexity;
        private String explanation;
        private double confidence;
        private Duration generationTime;
        private boolean success;
        private String errorMessage;
        private List<String> warnings;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private boolean fromCache;
        
        public SQLGenerationResponse() {
            this.sqlSuggestions = new ArrayList<>();
            this.confidence = 0.0;
            this.success = false;
            this.warnings = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.timestamp = LocalDateTime.now();
            this.fromCache = false;
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public List<GeneratedSQL> getSqlSuggestions() { return sqlSuggestions; }
        public void setSqlSuggestions(List<GeneratedSQL> sqlSuggestions) { this.sqlSuggestions = sqlSuggestions; }
        
        public QueryType getDetectedQueryType() { return detectedQueryType; }
        public void setDetectedQueryType(QueryType detectedQueryType) { this.detectedQueryType = detectedQueryType; }
        
        public QueryComplexity getComplexity() { return complexity; }
        public void setComplexity(QueryComplexity complexity) { this.complexity = complexity; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public Duration getGenerationTime() { return generationTime; }
        public void setGenerationTime(Duration generationTime) { this.generationTime = generationTime; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public boolean isFromCache() { return fromCache; }
        public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }
    }
    
    /**
     * SQL gerado
     */
    public static class GeneratedSQL {
        private String sql;
        private QueryType queryType;
        private QueryComplexity complexity;
        private double confidence;
        private String explanation;
        private List<String> requiredTables;
        private List<String> requiredColumns;
        private Map<String, Object> parameters;
        private Duration estimatedExecutionTime;
        private String optimizationNotes;
        private List<String> indexSuggestions;
        private boolean isOptimized;
        private boolean isValid;
        private List<String> validationErrors;
        
        public GeneratedSQL() {
            this.requiredTables = new ArrayList<>();
            this.requiredColumns = new ArrayList<>();
            this.parameters = new HashMap<>();
            this.indexSuggestions = new ArrayList<>();
            this.isOptimized = false;
            this.isValid = true;
            this.validationErrors = new ArrayList<>();
        }
        
        // Getters e Setters
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        
        public QueryType getQueryType() { return queryType; }
        public void setQueryType(QueryType queryType) { this.queryType = queryType; }
        
        public QueryComplexity getComplexity() { return complexity; }
        public void setComplexity(QueryComplexity complexity) { this.complexity = complexity; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public List<String> getRequiredTables() { return requiredTables; }
        public void setRequiredTables(List<String> requiredTables) { this.requiredTables = requiredTables; }
        
        public List<String> getRequiredColumns() { return requiredColumns; }
        public void setRequiredColumns(List<String> requiredColumns) { this.requiredColumns = requiredColumns; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public Duration getEstimatedExecutionTime() { return estimatedExecutionTime; }
        public void setEstimatedExecutionTime(Duration estimatedExecutionTime) { this.estimatedExecutionTime = estimatedExecutionTime; }
        
        public String getOptimizationNotes() { return optimizationNotes; }
        public void setOptimizationNotes(String optimizationNotes) { this.optimizationNotes = optimizationNotes; }
        
        public List<String> getIndexSuggestions() { return indexSuggestions; }
        public void setIndexSuggestions(List<String> indexSuggestions) { this.indexSuggestions = indexSuggestions; }
        
        public boolean isOptimized() { return isOptimized; }
        public void setOptimized(boolean optimized) { isOptimized = optimized; }
        
        public boolean isValid() { return isValid; }
        public void setValid(boolean valid) { isValid = valid; }
        
        public List<String> getValidationErrors() { return validationErrors; }
        public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
    }
    
    /**
     * Opções de geração
     */
    public static class GenerationOptions {
        private boolean includeComments;
        private boolean formatSQL;
        private boolean generateAlternatives;
        private boolean includePerformanceHints;
        private boolean useIndexHints;
        private String sqlDialect;
        private int maxComplexity;
        private boolean allowSubqueries;
        private boolean allowJoins;
        private boolean allowAggregations;
        private boolean allowWindowFunctions;
        private boolean allowCTE;
        
        public GenerationOptions() {
            this.includeComments = true;
            this.formatSQL = true;
            this.generateAlternatives = true;
            this.includePerformanceHints = true;
            this.useIndexHints = false;
            this.sqlDialect = "ANSI";
            this.maxComplexity = 3;
            this.allowSubqueries = true;
            this.allowJoins = true;
            this.allowAggregations = true;
            this.allowWindowFunctions = true;
            this.allowCTE = true;
        }
        
        // Getters e Setters
        public boolean isIncludeComments() { return includeComments; }
        public void setIncludeComments(boolean includeComments) { this.includeComments = includeComments; }
        
        public boolean isFormatSQL() { return formatSQL; }
        public void setFormatSQL(boolean formatSQL) { this.formatSQL = formatSQL; }
        
        public boolean isGenerateAlternatives() { return generateAlternatives; }
        public void setGenerateAlternatives(boolean generateAlternatives) { this.generateAlternatives = generateAlternatives; }
        
        public boolean isIncludePerformanceHints() { return includePerformanceHints; }
        public void setIncludePerformanceHints(boolean includePerformanceHints) { this.includePerformanceHints = includePerformanceHints; }
        
        public boolean isUseIndexHints() { return useIndexHints; }
        public void setUseIndexHints(boolean useIndexHints) { this.useIndexHints = useIndexHints; }
        
        public String getSqlDialect() { return sqlDialect; }
        public void setSqlDialect(String sqlDialect) { this.sqlDialect = sqlDialect; }
        
        public int getMaxComplexity() { return maxComplexity; }
        public void setMaxComplexity(int maxComplexity) { this.maxComplexity = maxComplexity; }
        
        public boolean isAllowSubqueries() { return allowSubqueries; }
        public void setAllowSubqueries(boolean allowSubqueries) { this.allowSubqueries = allowSubqueries; }
        
        public boolean isAllowJoins() { return allowJoins; }
        public void setAllowJoins(boolean allowJoins) { this.allowJoins = allowJoins; }
        
        public boolean isAllowAggregations() { return allowAggregations; }
        public void setAllowAggregations(boolean allowAggregations) { this.allowAggregations = allowAggregations; }
        
        public boolean isAllowWindowFunctions() { return allowWindowFunctions; }
        public void setAllowWindowFunctions(boolean allowWindowFunctions) { this.allowWindowFunctions = allowWindowFunctions; }
        
        public boolean isAllowCTE() { return allowCTE; }
        public void setAllowCTE(boolean allowCTE) { this.allowCTE = allowCTE; }
    }
    
    /**
     * Schema de tabela
     */
    public static class TableSchema {
        private String tableName;
        private String schemaName;
        private List<ColumnSchema> columns;
        private List<IndexSchema> indexes;
        private List<ForeignKeySchema> foreignKeys;
        private Map<String, Object> metadata;
        
        public TableSchema() {
            this.columns = new ArrayList<>();
            this.indexes = new ArrayList<>();
            this.foreignKeys = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
        
        // Getters e Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
        
        public List<ColumnSchema> getColumns() { return columns; }
        public void setColumns(List<ColumnSchema> columns) { this.columns = columns; }
        
        public List<IndexSchema> getIndexes() { return indexes; }
        public void setIndexes(List<IndexSchema> indexes) { this.indexes = indexes; }
        
        public List<ForeignKeySchema> getForeignKeys() { return foreignKeys; }
        public void setForeignKeys(List<ForeignKeySchema> foreignKeys) { this.foreignKeys = foreignKeys; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * Schema de coluna
     */
    public static class ColumnSchema {
        private String columnName;
        private String dataType;
        private boolean nullable;
        private boolean primaryKey;
        private boolean unique;
        private String defaultValue;
        private String comment;
        
        // Getters e Setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        
        public boolean isNullable() { return nullable; }
        public void setNullable(boolean nullable) { this.nullable = nullable; }
        
        public boolean isPrimaryKey() { return primaryKey; }
        public void setPrimaryKey(boolean primaryKey) { this.primaryKey = primaryKey; }
        
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
        
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
    
    /**
     * Schema de índice
     */
    public static class IndexSchema {
        private String indexName;
        private List<String> columns;
        private boolean unique;
        private String type;
        
        public IndexSchema() {
            this.columns = new ArrayList<>();
        }
        
        // Getters e Setters
        public String getIndexName() { return indexName; }
        public void setIndexName(String indexName) { this.indexName = indexName; }
        
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        
        public boolean isUnique() { return unique; }
        public void setUnique(boolean unique) { this.unique = unique; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
    
    /**
     * Schema de chave estrangeira
     */
    public static class ForeignKeySchema {
        private String constraintName;
        private List<String> columns;
        private String referencedTable;
        private List<String> referencedColumns;
        
        public ForeignKeySchema() {
            this.columns = new ArrayList<>();
            this.referencedColumns = new ArrayList<>();
        }
        
        // Getters e Setters
        public String getConstraintName() { return constraintName; }
        public void setConstraintName(String constraintName) { this.constraintName = constraintName; }
        
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        
        public String getReferencedTable() { return referencedTable; }
        public void setReferencedTable(String referencedTable) { this.referencedTable = referencedTable; }
        
        public List<String> getReferencedColumns() { return referencedColumns; }
        public void setReferencedColumns(List<String> referencedColumns) { this.referencedColumns = referencedColumns; }
    }
    
    /**
     * Repositório de templates SQL
     */
    private static class SQLTemplateRepository {
        private final Map<QueryType, List<String>> templates;
        
        public SQLTemplateRepository() {
            this.templates = new HashMap<>();
            initializeTemplates();
        }
        
        private void initializeTemplates() {
            // Templates SELECT
            templates.put(QueryType.SELECT, Arrays.asList(
                "SELECT {columns} FROM {table}",
                "SELECT {columns} FROM {table} WHERE {condition}",
                "SELECT {columns} FROM {table} WHERE {condition} ORDER BY {orderBy}",
                "SELECT {columns} FROM {table} WHERE {condition} GROUP BY {groupBy}",
                "SELECT {columns} FROM {table} WHERE {condition} GROUP BY {groupBy} HAVING {having}",
                "SELECT {columns} FROM {table} WHERE {condition} ORDER BY {orderBy} LIMIT {limit}",
                "SELECT DISTINCT {columns} FROM {table} WHERE {condition}",
                "SELECT {aggregateFunction}({column}) AS {alias} FROM {table} WHERE {condition} GROUP BY {groupBy}"
            ));
            
            // Templates INSERT
            templates.put(QueryType.INSERT, Arrays.asList(
                "INSERT INTO {table} ({columns}) VALUES ({values})",
                "INSERT INTO {table} ({columns}) SELECT {selectColumns} FROM {sourceTable} WHERE {condition}",
                "INSERT INTO {table} SET {assignments}"
            ));
            
            // Templates UPDATE
            templates.put(QueryType.UPDATE, Arrays.asList(
                "UPDATE {table} SET {assignments} WHERE {condition}",
                "UPDATE {table} SET {assignments}",
                "UPDATE {table} t1 JOIN {table2} t2 ON {joinCondition} SET {assignments} WHERE {condition}"
            ));
            
            // Templates DELETE
            templates.put(QueryType.DELETE, Arrays.asList(
                "DELETE FROM {table} WHERE {condition}",
                "DELETE FROM {table}",
                "DELETE t1 FROM {table} t1 JOIN {table2} t2 ON {joinCondition} WHERE {condition}"
            ));
            
            // Templates JOIN
            templates.put(QueryType.SELECT, Arrays.asList(
                "SELECT {columns} FROM {table1} t1 INNER JOIN {table2} t2 ON {joinCondition}",
                "SELECT {columns} FROM {table1} t1 LEFT JOIN {table2} t2 ON {joinCondition}",
                "SELECT {columns} FROM {table1} t1 RIGHT JOIN {table2} t2 ON {joinCondition}",
                "SELECT {columns} FROM {table1} t1 FULL OUTER JOIN {table2} t2 ON {joinCondition}"
            ));
            
            // Templates CTE
            templates.put(QueryType.CTE, Arrays.asList(
                "WITH {cteName} AS ({cteQuery}) SELECT {columns} FROM {cteName}",
                "WITH RECURSIVE {cteName} AS ({baseQuery} UNION ALL {recursiveQuery}) SELECT {columns} FROM {cteName}"
            ));
            
            // Templates Window Functions
            templates.put(QueryType.WINDOW_FUNCTION, Arrays.asList(
                "SELECT {columns}, {windowFunction} OVER (PARTITION BY {partitionBy} ORDER BY {orderBy}) AS {alias} FROM {table}",
                "SELECT {columns}, ROW_NUMBER() OVER (PARTITION BY {partitionBy} ORDER BY {orderBy}) AS row_num FROM {table}",
                "SELECT {columns}, RANK() OVER (ORDER BY {orderBy}) AS rank FROM {table}"
            ));
        }
        
        public List<String> getTemplates(QueryType queryType) {
            return templates.getOrDefault(queryType, new ArrayList<>());
        }
    }
    
    /**
     * Analisador de schema
     */
    private static class SchemaAnalyzer {
        
        public Map<String, Object> analyzeSchema(List<TableSchema> tables) {
            Map<String, Object> analysis = new HashMap<>();
            
            analysis.put("total_tables", tables.size());
            analysis.put("total_columns", tables.stream()
                .mapToInt(t -> t.getColumns().size())
                .sum());
            
            Map<String, List<String>> relationships = findRelationships(tables);
            analysis.put("relationships", relationships);
            
            List<String> suggestedIndexes = suggestIndexes(tables);
            analysis.put("suggested_indexes", suggestedIndexes);
            
            return analysis;
        }
        
        private Map<String, List<String>> findRelationships(List<TableSchema> tables) {
            Map<String, List<String>> relationships = new HashMap<>();
            
            for (TableSchema table : tables) {
                List<String> relatedTables = new ArrayList<>();
                
                for (ForeignKeySchema fk : table.getForeignKeys()) {
                    relatedTables.add(fk.getReferencedTable());
                }
                
                if (!relatedTables.isEmpty()) {
                    relationships.put(table.getTableName(), relatedTables);
                }
            }
            
            return relationships;
        }
        
        private List<String> suggestIndexes(List<TableSchema> tables) {
            List<String> suggestions = new ArrayList<>();
            
            for (TableSchema table : tables) {
                for (ColumnSchema column : table.getColumns()) {
                    // Sugerir índices para colunas frequentemente usadas em WHERE
                    if (isFrequentlyQueried(column)) {
                        suggestions.add(String.format("CREATE INDEX idx_%s_%s ON %s (%s)",
                            table.getTableName(), column.getColumnName(),
                            table.getTableName(), column.getColumnName()));
                    }
                }
            }
            
            return suggestions;
        }
        
        private boolean isFrequentlyQueried(ColumnSchema column) {
            // Heurística simples para identificar colunas frequentemente consultadas
            String columnName = column.getColumnName().toLowerCase();
            return columnName.contains("id") || 
                   columnName.contains("name") || 
                   columnName.contains("email") || 
                   columnName.contains("date") ||
                   columnName.contains("status");
        }
    }
    
    /**
     * Validador de consultas
     */
    private static class QueryValidator {
        private final List<Pattern> sqlInjectionPatterns;
        
        public QueryValidator() {
            this.sqlInjectionPatterns = Arrays.asList(
                Pattern.compile("(?i).*\\b(DROP|DELETE|TRUNCATE|ALTER)\\s+.*"),
                Pattern.compile("(?i).*\\b(EXEC|EXECUTE|SP_)\\s+.*"),
                Pattern.compile("(?i).*\\b(UNION|UNION\\s+ALL)\\s+SELECT\\s+.*"),
                Pattern.compile("(?i).*'\\s*;\\s*--.*"),
                Pattern.compile("(?i).*'\\s*OR\\s+'1'\\s*=\\s*'1.*")
            );
        }
        
        public ValidationResult validateSQL(String sql) {
            ValidationResult result = new ValidationResult();
            
            if (sql == null || sql.trim().isEmpty()) {
                result.addError("SQL não pode estar vazio");
                return result;
            }
            
            // Verificar injeção SQL
            for (Pattern pattern : sqlInjectionPatterns) {
                if (pattern.matcher(sql).matches()) {
                    result.addError("Possível tentativa de injeção SQL detectada");
                    break;
                }
            }
            
            // Verificar sintaxe básica
            if (!hasValidSyntax(sql)) {
                result.addError("Sintaxe SQL inválida");
            }
            
            // Verificar balanceamento de parênteses
            if (!hasBalancedParentheses(sql)) {
                result.addError("Parênteses não balanceados");
            }
            
            return result;
        }
        
        private boolean hasValidSyntax(String sql) {
            // Verificação básica de sintaxe SQL
            String upperSql = sql.toUpperCase().trim();
            
            return upperSql.startsWith("SELECT") ||
                   upperSql.startsWith("INSERT") ||
                   upperSql.startsWith("UPDATE") ||
                   upperSql.startsWith("DELETE") ||
                   upperSql.startsWith("WITH") ||
                   upperSql.startsWith("CREATE") ||
                   upperSql.startsWith("ALTER") ||
                   upperSql.startsWith("DROP");
        }
        
        private boolean hasBalancedParentheses(String sql) {
            int count = 0;
            for (char c : sql.toCharArray()) {
                if (c == '(') count++;
                else if (c == ')') count--;
                if (count < 0) return false;
            }
            return count == 0;
        }
    }
    
    /**
     * Resultado de validação
     */
    private static class ValidationResult {
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
        }
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return errors;
        }
        
        public List<String> getWarnings() {
            return warnings;
        }
    }
    
    /**
     * Otimizador SQL
     */
    private static class SQLOptimizer {
        
        public OptimizationResult optimizeSQL(String sql, List<TableSchema> schema) {
            OptimizationResult result = new OptimizationResult();
            result.setOriginalSQL(sql);
            
            String optimizedSQL = sql;
            List<String> optimizations = new ArrayList<>();
            
            // Otimização 1: Remover SELECT *
            if (sql.toUpperCase().contains("SELECT *")) {
                optimizedSQL = suggestSpecificColumns(optimizedSQL, schema);
                optimizations.add("Substituído SELECT * por colunas específicas");
            }
            
            // Otimização 2: Adicionar LIMIT se não existir
            if (!sql.toUpperCase().contains("LIMIT") && 
                sql.toUpperCase().startsWith("SELECT")) {
                optimizedSQL += " LIMIT 1000";
                optimizations.add("Adicionado LIMIT para evitar resultados excessivos");
            }
            
            // Otimização 3: Sugerir índices
            List<String> indexSuggestions = suggestIndexes(sql, schema);
            
            result.setOptimizedSQL(optimizedSQL);
            result.setOptimizations(optimizations);
            result.setIndexSuggestions(indexSuggestions);
            result.setImprovement(calculateImprovement(sql, optimizedSQL));
            
            return result;
        }
        
        private String suggestSpecificColumns(String sql, List<TableSchema> schema) {
            if (schema.isEmpty()) {
                return sql.replaceAll("(?i)SELECT\\s+\\*", "SELECT column1, column2, column3");
            }
            
            TableSchema firstTable = schema.get(0);
            String columns = firstTable.getColumns().stream()
                .limit(5) // Limitar a 5 colunas
                .map(ColumnSchema::getColumnName)
                .collect(Collectors.joining(", "));
            
            return sql.replaceAll("(?i)SELECT\\s+\\*", "SELECT " + columns);
        }
        
        private List<String> suggestIndexes(String sql, List<TableSchema> schema) {
            List<String> suggestions = new ArrayList<>();
            
            // Extrair colunas usadas em WHERE
            Pattern wherePattern = Pattern.compile("(?i)WHERE\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
            Matcher matcher = wherePattern.matcher(sql);
            
            while (matcher.find()) {
                String column = matcher.group(1);
                suggestions.add("CREATE INDEX idx_" + column + " ON table_name (" + column + ")");
            }
            
            return suggestions;
        }
        
        private double calculateImprovement(String original, String optimized) {
            // Cálculo simples baseado na redução de complexidade
            int originalComplexity = calculateComplexity(original);
            int optimizedComplexity = calculateComplexity(optimized);
            
            if (originalComplexity == 0) return 0.0;
            
            return ((double) (originalComplexity - optimizedComplexity) / originalComplexity) * 100;
        }
        
        private int calculateComplexity(String sql) {
            int complexity = 0;
            String upperSql = sql.toUpperCase();
            
            if (upperSql.contains("SELECT *")) complexity += 2;
            if (upperSql.contains("JOIN")) complexity += 1;
            if (upperSql.contains("SUBQUERY")) complexity += 3;
            if (upperSql.contains("GROUP BY")) complexity += 1;
            if (upperSql.contains("ORDER BY")) complexity += 1;
            if (!upperSql.contains("LIMIT")) complexity += 1;
            
            return complexity;
        }
    }
    
    /**
     * Resultado de otimização
     */
    private static class OptimizationResult {
        private String originalSQL;
        private String optimizedSQL;
        private List<String> optimizations;
        private List<String> indexSuggestions;
        private double improvement;
        
        public OptimizationResult() {
            this.optimizations = new ArrayList<>();
            this.indexSuggestions = new ArrayList<>();
        }
        
        // Getters e Setters
        public String getOriginalSQL() { return originalSQL; }
        public void setOriginalSQL(String originalSQL) { this.originalSQL = originalSQL; }
        
        public String getOptimizedSQL() { return optimizedSQL; }
        public void setOptimizedSQL(String optimizedSQL) { this.optimizedSQL = optimizedSQL; }
        
        public List<String> getOptimizations() { return optimizations; }
        public void setOptimizations(List<String> optimizations) { this.optimizations = optimizations; }
        
        public List<String> getIndexSuggestions() { return indexSuggestions; }
        public void setIndexSuggestions(List<String> indexSuggestions) { this.indexSuggestions = indexSuggestions; }
        
        public double getImprovement() { return improvement; }
        public void setImprovement(double improvement) { this.improvement = improvement; }
    }
    
    /**
     * Gerenciador de cache
     */
    private static class CacheManager {
        private final Map<String, CacheEntry> cache;
        private final int maxSize;
        
        public CacheManager() {
            this.cache = new ConcurrentHashMap<>();
            this.maxSize = 1000;
        }
        
        public SQLGenerationResponse get(String key) {
            CacheEntry entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                entry.updateLastAccess();
                SQLGenerationResponse response = entry.getResponse();
                response.setFromCache(true);
                return response;
            }
            return null;
        }
        
        public void put(String key, SQLGenerationResponse response) {
            if (cache.size() >= maxSize) {
                evictOldest();
            }
            cache.put(key, new CacheEntry(response));
        }
        
        private void evictOldest() {
            String oldestKey = cache.entrySet().stream()
                .min(Map.Entry.comparingByValue(
                    (e1, e2) -> e1.getLastAccess().compareTo(e2.getLastAccess())))
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (oldestKey != null) {
                cache.remove(oldestKey);
            }
        }
        
        public String generateCacheKey(SQLGenerationRequest request) {
            try {
                String input = request.getNaturalLanguageQuery() + 
                              request.getDatabaseType() + 
                              request.getSchemaName();
                
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                
                StringBuilder hexString = new StringBuilder();
                for (byte b : hash) {
                    String hex = Integer.toHexString(0xff & b);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }
                
                return hexString.toString();
            } catch (Exception e) {
                return UUID.randomUUID().toString();
            }
        }
    }
    
    /**
     * Entrada do cache
     */
    private static class CacheEntry {
        private final SQLGenerationResponse response;
        private final LocalDateTime createdAt;
        private LocalDateTime lastAccess;
        private final Duration ttl;
        
        public CacheEntry(SQLGenerationResponse response) {
            this.response = response;
            this.createdAt = LocalDateTime.now();
            this.lastAccess = LocalDateTime.now();
            this.ttl = Duration.ofHours(1); // TTL de 1 hora
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(createdAt.plus(ttl));
        }
        
        public void updateLastAccess() {
            this.lastAccess = LocalDateTime.now();
        }
        
        public SQLGenerationResponse getResponse() {
            return response;
        }
        
        public LocalDateTime getLastAccess() {
            return lastAccess;
        }
    }
    
    /**
     * Métricas de geração
     */
    private static class GenerationMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong cacheHits = new AtomicLong(0);
        private final Map<QueryType, AtomicLong> queryTypeCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> processingTimes = new ConcurrentHashMap<>();
        
        public void recordRequest(QueryType queryType, Duration processingTime, 
                boolean success, boolean fromCache) {
            totalRequests.incrementAndGet();
            
            if (success) {
                successfulRequests.incrementAndGet();
            }
            
            if (fromCache) {
                cacheHits.incrementAndGet();
            }
            
            if (queryType != null) {
                queryTypeCounts.computeIfAbsent(queryType, k -> new AtomicLong(0))
                    .incrementAndGet();
            }
            
            String timeRange = getTimeRange(processingTime);
            processingTimes.computeIfAbsent(timeRange, k -> new AtomicLong(0))
                .incrementAndGet();
        }
        
        private String getTimeRange(Duration duration) {
            long millis = duration.toMillis();
            if (millis < 100) return "<100ms";
            if (millis < 500) return "100-500ms";
            if (millis < 1000) return "500ms-1s";
            if (millis < 5000) return "1-5s";
            return ">5s";
        }
        
        public Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            metrics.put("total_requests", totalRequests.get());
            metrics.put("successful_requests", successfulRequests.get());
            metrics.put("cache_hits", cacheHits.get());
            metrics.put("success_rate", 
                totalRequests.get() > 0 ? 
                    (double) successfulRequests.get() / totalRequests.get() : 0.0);
            metrics.put("cache_hit_rate", 
                totalRequests.get() > 0 ? 
                    (double) cacheHits.get() / totalRequests.get() : 0.0);
            
            Map<String, Long> queryStats = queryTypeCounts.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().name(),
                    e -> e.getValue().get()
                ));
            metrics.put("query_type_distribution", queryStats);
            
            Map<String, Long> timeStats = processingTimes.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().get()
                ));
            metrics.put("processing_time_distribution", timeStats);
            
            return metrics;
        }
    }
    
    /**
     * Inicializa o serviço
     */
    private void initializeService() {
        isInitialized.set(true);
    }
    
    /**
     * Gera SQL a partir de linguagem natural
     */
    public CompletableFuture<SQLGenerationResponse> generateSQL(SQLGenerationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            SQLGenerationResponse response = new SQLGenerationResponse();
            response.setRequestId(request.getRequestId());
            
            try {
                // Verificar cache
                if (request.isUseCache()) {
                    String cacheKey = cacheManager.generateCacheKey(request);
                    SQLGenerationResponse cachedResponse = cacheManager.get(cacheKey);
                    if (cachedResponse != null) {
                        return cachedResponse;
                    }
                }
                
                // Detectar tipo de consulta
                QueryType queryType = detectQueryType(request.getNaturalLanguageQuery());
                response.setDetectedQueryType(queryType);
                
                // Gerar sugestões SQL
                List<GeneratedSQL> suggestions = generateSQLSuggestions(request, queryType);
                response.setSqlSuggestions(suggestions);
                
                // Calcular complexidade
                QueryComplexity complexity = calculateComplexity(suggestions);
                response.setComplexity(complexity);
                
                // Calcular confiança
                double confidence = calculateConfidence(suggestions);
                response.setConfidence(confidence);
                
                // Gerar explicação
                String explanation = generateExplanation(queryType, suggestions);
                response.setExplanation(explanation);
                
                response.setSuccess(true);
                
                // Salvar no cache
                if (request.isUseCache()) {
                    String cacheKey = cacheManager.generateCacheKey(request);
                    cacheManager.put(cacheKey, response);
                }
                
            } catch (Exception e) {
                response.setSuccess(false);
                response.setErrorMessage("Erro na geração SQL: " + e.getMessage());
            } finally {
                Duration generationTime = Duration.between(startTime, LocalDateTime.now());
                response.setGenerationTime(generationTime);
                
                metrics.recordRequest(response.getDetectedQueryType(), 
                    generationTime, response.isSuccess(), response.isFromCache());
            }
            
            return response;
        }, executorService);
    }
    
    /**
     * Detecta tipo de consulta
     */
    private QueryType detectQueryType(String query) {
        String normalizedQuery = query.toLowerCase().trim();
        
        if (normalizedQuery.contains("select") || normalizedQuery.contains("mostrar") || 
            normalizedQuery.contains("listar") || normalizedQuery.contains("buscar")) {
            return QueryType.SELECT;
        }
        
        if (normalizedQuery.contains("insert") || normalizedQuery.contains("adicionar") || 
            normalizedQuery.contains("criar") || normalizedQuery.contains("incluir")) {
            return QueryType.INSERT;
        }
        
        if (normalizedQuery.contains("update") || normalizedQuery.contains("atualizar") || 
            normalizedQuery.contains("modificar") || normalizedQuery.contains("alterar")) {
            return QueryType.UPDATE;
        }
        
        if (normalizedQuery.contains("delete") || normalizedQuery.contains("excluir") || 
            normalizedQuery.contains("remover") || normalizedQuery.contains("apagar")) {
            return QueryType.DELETE;
        }
        
        if (normalizedQuery.contains("join") || normalizedQuery.contains("juntar") || 
            normalizedQuery.contains("relacionar")) {
            return QueryType.SELECT; // JOIN é um tipo de SELECT
        }
        
        return QueryType.SELECT; // Padrão
    }
    
    /**
     * Gera sugestões SQL
     */
    private List<GeneratedSQL> generateSQLSuggestions(SQLGenerationRequest request, QueryType queryType) {
        List<String> templates = templateRepository.getTemplates(queryType);
        List<GeneratedSQL> suggestions = new ArrayList<>();
        
        for (int i = 0; i < Math.min(templates.size(), request.getMaxSuggestions()); i++) {
            String template = templates.get(i);
            
            GeneratedSQL sql = new GeneratedSQL();
            sql.setSql(populateTemplate(template, request));
            sql.setQueryType(queryType);
            sql.setConfidence(0.9 - (i * 0.1));
            sql.setComplexity(determineComplexity(template));
            sql.setExplanation(generateSQLExplanation(sql.getSql(), queryType));
            
            // Validar SQL
            if (request.isValidateSyntax()) {
                ValidationResult validation = queryValidator.validateSQL(sql.getSql());
                sql.setValid(validation.isValid());
                sql.setValidationErrors(validation.getErrors());
            }
            
            // Otimizar SQL
            if (request.isOptimizeQuery()) {
                OptimizationResult optimization = sqlOptimizer.optimizeSQL(
                    sql.getSql(), request.getAvailableTables());
                sql.setSql(optimization.getOptimizedSQL());
                sql.setOptimized(true);
                sql.setOptimizationNotes(String.join("; ", optimization.getOptimizations()));
                sql.setIndexSuggestions(optimization.getIndexSuggestions());
            }
            
            suggestions.add(sql);
        }
        
        return suggestions;
    }
    
    /**
     * Popula template com dados da requisição
     */
    private String populateTemplate(String template, SQLGenerationRequest request) {
        String sql = template;
        
        // Substituições básicas
        sql = sql.replace("{columns}", "*");
        sql = sql.replace("{table}", getTableName(request));
        sql = sql.replace("{condition}", "1=1");
        sql = sql.replace("{orderBy}", "id");
        sql = sql.replace("{limit}", "100");
        sql = sql.replace("{groupBy}", "id");
        sql = sql.replace("{having}", "COUNT(*) > 1");
        sql = sql.replace("{values}", "('value1', 'value2')");
        sql = sql.replace("{assignments}", "column1 = 'value1'");
        
        return sql;
    }
    
    /**
     * Obtém nome da tabela
     */
    private String getTableName(SQLGenerationRequest request) {
        if (!request.getAvailableTables().isEmpty()) {
            return request.getAvailableTables().get(0).getTableName();
        }
        return "table_name";
    }
    
    /**
     * Determina complexidade do template
     */
    private QueryComplexity determineComplexity(String template) {
        int complexity = 0;
        
        if (template.contains("JOIN")) complexity += 2;
        if (template.contains("GROUP BY")) complexity += 1;
        if (template.contains("HAVING")) complexity += 1;
        if (template.contains("SUBQUERY")) complexity += 3;
        if (template.contains("WINDOW")) complexity += 2;
        if (template.contains("CTE")) complexity += 2;
        
        if (complexity <= 1) return QueryComplexity.SIMPLE;
        if (complexity <= 3) return QueryComplexity.MODERATE;
        if (complexity <= 5) return QueryComplexity.COMPLEX;
        if (complexity <= 7) return QueryComplexity.ADVANCED;
        return QueryComplexity.EXPERT;
    }
    
    /**
     * Calcula complexidade geral
     */
    private QueryComplexity calculateComplexity(List<GeneratedSQL> suggestions) {
        if (suggestions.isEmpty()) return QueryComplexity.SIMPLE;
        
        return suggestions.stream()
            .map(GeneratedSQL::getComplexity)
            .max(Comparator.comparingInt(QueryComplexity::getLevel))
            .orElse(QueryComplexity.SIMPLE);
    }
    
    /**
     * Calcula confiança geral
     */
    private double calculateConfidence(List<GeneratedSQL> suggestions) {
        if (suggestions.isEmpty()) return 0.0;
        
        return suggestions.stream()
            .mapToDouble(GeneratedSQL::getConfidence)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Gera explicação
     */
    private String generateExplanation(QueryType queryType, List<GeneratedSQL> suggestions) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Análise da geração SQL:\n");
        explanation.append("- Tipo de consulta: ").append(queryType.getDescription()).append("\n");
        explanation.append("- Sugestões geradas: ").append(suggestions.size()).append("\n");
        
        if (!suggestions.isEmpty()) {
            GeneratedSQL bestSuggestion = suggestions.get(0);
            explanation.append("- Melhor sugestão: ").append(bestSuggestion.getSql()).append("\n");
            explanation.append("- Confiança: ").append(String.format("%.1f%%", bestSuggestion.getConfidence() * 100));
        }
        
        return explanation.toString();
    }
    
    /**
     * Gera explicação SQL
     */
    private String generateSQLExplanation(String sql, QueryType queryType) {
        return String.format("Consulta %s: %s", 
            queryType.getDescription().toLowerCase(), sql);
    }
    
    /**
     * Obtém métricas
     */
    public Map<String, Object> getMetrics() {
        return metrics.getMetrics();
    }
    
    /**
     * Limpa cache
     */
    public void clearCache() {
        // Implementar limpeza de cache se necessário
    }
    
    /**
     * Finaliza o serviço
     */
    public void shutdown() {
        isInitialized.set(false);
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Verifica se está inicializado
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }
}