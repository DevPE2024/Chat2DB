package ai.chat2db.spi.nlp;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.text.Normalizer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sistema de Processamento de Linguagem Natural para Chat2DB
 * Converte linguagem natural em consultas SQL estruturadas
 */
public class NaturalLanguageProcessor {
    
    private final Map<String, IntentClassifier> intentClassifiers;
    private final EntityExtractor entityExtractor;
    private final QueryTemplateEngine templateEngine;
    private final ContextManager contextManager;
    private final NLPMetrics metrics;
    private final ExecutorService executorService;
    private final AtomicBoolean isInitialized;
    
    public NaturalLanguageProcessor() {
        this.intentClassifiers = new ConcurrentHashMap<>();
        this.entityExtractor = new EntityExtractor();
        this.templateEngine = new QueryTemplateEngine();
        this.contextManager = new ContextManager();
        this.metrics = new NLPMetrics();
        this.executorService = Executors.newFixedThreadPool(5);
        this.isInitialized = new AtomicBoolean(false);
        
        initializeComponents();
    }
    
    /**
     * Tipos de intenção SQL
     */
    public enum SQLIntent {
        SELECT("Consulta de dados", "SELECT"),
        INSERT("Inserção de dados", "INSERT"),
        UPDATE("Atualização de dados", "UPDATE"),
        DELETE("Exclusão de dados", "DELETE"),
        CREATE_TABLE("Criação de tabela", "CREATE TABLE"),
        ALTER_TABLE("Alteração de tabela", "ALTER TABLE"),
        DROP_TABLE("Exclusão de tabela", "DROP TABLE"),
        CREATE_INDEX("Criação de índice", "CREATE INDEX"),
        JOIN("Junção de tabelas", "JOIN"),
        AGGREGATE("Agregação de dados", "AGGREGATE"),
        FILTER("Filtro de dados", "WHERE"),
        SORT("Ordenação de dados", "ORDER BY"),
        GROUP("Agrupamento de dados", "GROUP BY"),
        SUBQUERY("Subconsulta", "SUBQUERY"),
        UNION("União de consultas", "UNION"),
        EXPLAIN("Explicação de consulta", "EXPLAIN"),
        ANALYZE("Análise de dados", "ANALYZE"),
        UNKNOWN("Intenção desconhecida", "UNKNOWN");
        
        private final String description;
        private final String sqlKeyword;
        
        SQLIntent(String description, String sqlKeyword) {
            this.description = description;
            this.sqlKeyword = sqlKeyword;
        }
        
        public String getDescription() { return description; }
        public String getSqlKeyword() { return sqlKeyword; }
    }
    
    /**
     * Tipos de entidade
     */
    public enum EntityType {
        TABLE_NAME("Nome da tabela"),
        COLUMN_NAME("Nome da coluna"),
        VALUE("Valor"),
        CONDITION("Condição"),
        FUNCTION("Função"),
        OPERATOR("Operador"),
        DATE_TIME("Data/Hora"),
        NUMBER("Número"),
        STRING("Texto"),
        BOOLEAN("Booleano"),
        COMPARISON("Comparação"),
        LOGICAL("Lógico"),
        AGGREGATE_FUNCTION("Função de agregação"),
        SORT_ORDER("Ordem de classificação"),
        LIMIT("Limite"),
        OFFSET("Deslocamento");
        
        private final String description;
        
        EntityType(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Requisição de processamento NLP
     */
    public static class NLPRequest {
        private String requestId;
        private String naturalLanguageQuery;
        private String databaseSchema;
        private List<String> availableTables;
        private List<String> availableColumns;
        private Map<String, Object> context;
        private String userId;
        private String sessionId;
        private LocalDateTime timestamp;
        private String language;
        private boolean includeExplanation;
        private int maxSuggestions;
        
        public NLPRequest() {
            this.requestId = UUID.randomUUID().toString();
            this.availableTables = new ArrayList<>();
            this.availableColumns = new ArrayList<>();
            this.context = new HashMap<>();
            this.timestamp = LocalDateTime.now();
            this.language = "pt";
            this.includeExplanation = true;
            this.maxSuggestions = 3;
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public String getNaturalLanguageQuery() { return naturalLanguageQuery; }
        public void setNaturalLanguageQuery(String naturalLanguageQuery) { this.naturalLanguageQuery = naturalLanguageQuery; }
        
        public String getDatabaseSchema() { return databaseSchema; }
        public void setDatabaseSchema(String databaseSchema) { this.databaseSchema = databaseSchema; }
        
        public List<String> getAvailableTables() { return availableTables; }
        public void setAvailableTables(List<String> availableTables) { this.availableTables = availableTables; }
        
        public List<String> getAvailableColumns() { return availableColumns; }
        public void setAvailableColumns(List<String> availableColumns) { this.availableColumns = availableColumns; }
        
        public Map<String, Object> getContext() { return context; }
        public void setContext(Map<String, Object> context) { this.context = context; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public boolean isIncludeExplanation() { return includeExplanation; }
        public void setIncludeExplanation(boolean includeExplanation) { this.includeExplanation = includeExplanation; }
        
        public int getMaxSuggestions() { return maxSuggestions; }
        public void setMaxSuggestions(int maxSuggestions) { this.maxSuggestions = maxSuggestions; }
    }
    
    /**
     * Resposta do processamento NLP
     */
    public static class NLPResponse {
        private String requestId;
        private List<SQLSuggestion> sqlSuggestions;
        private SQLIntent detectedIntent;
        private List<ExtractedEntity> extractedEntities;
        private String explanation;
        private double confidence;
        private Duration processingTime;
        private boolean success;
        private String errorMessage;
        private List<String> warnings;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        
        public NLPResponse() {
            this.sqlSuggestions = new ArrayList<>();
            this.extractedEntities = new ArrayList<>();
            this.confidence = 0.0;
            this.success = false;
            this.warnings = new ArrayList<>();
            this.metadata = new HashMap<>();
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public List<SQLSuggestion> getSqlSuggestions() { return sqlSuggestions; }
        public void setSqlSuggestions(List<SQLSuggestion> sqlSuggestions) { this.sqlSuggestions = sqlSuggestions; }
        
        public SQLIntent getDetectedIntent() { return detectedIntent; }
        public void setDetectedIntent(SQLIntent detectedIntent) { this.detectedIntent = detectedIntent; }
        
        public List<ExtractedEntity> getExtractedEntities() { return extractedEntities; }
        public void setExtractedEntities(List<ExtractedEntity> extractedEntities) { this.extractedEntities = extractedEntities; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public Duration getProcessingTime() { return processingTime; }
        public void setProcessingTime(Duration processingTime) { this.processingTime = processingTime; }
        
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
    }
    
    /**
     * Sugestão de SQL
     */
    public static class SQLSuggestion {
        private String sql;
        private double confidence;
        private String explanation;
        private Map<String, Object> parameters;
        private List<String> requiredTables;
        private List<String> requiredColumns;
        private String complexity;
        private Duration estimatedExecutionTime;
        
        public SQLSuggestion() {
            this.parameters = new HashMap<>();
            this.requiredTables = new ArrayList<>();
            this.requiredColumns = new ArrayList<>();
            this.complexity = "MEDIUM";
        }
        
        // Getters e Setters
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public List<String> getRequiredTables() { return requiredTables; }
        public void setRequiredTables(List<String> requiredTables) { this.requiredTables = requiredTables; }
        
        public List<String> getRequiredColumns() { return requiredColumns; }
        public void setRequiredColumns(List<String> requiredColumns) { this.requiredColumns = requiredColumns; }
        
        public String getComplexity() { return complexity; }
        public void setComplexity(String complexity) { this.complexity = complexity; }
        
        public Duration getEstimatedExecutionTime() { return estimatedExecutionTime; }
        public void setEstimatedExecutionTime(Duration estimatedExecutionTime) { this.estimatedExecutionTime = estimatedExecutionTime; }
    }
    
    /**
     * Entidade extraída
     */
    public static class ExtractedEntity {
        private EntityType type;
        private String value;
        private String normalizedValue;
        private int startPosition;
        private int endPosition;
        private double confidence;
        private Map<String, Object> attributes;
        
        public ExtractedEntity() {
            this.attributes = new HashMap<>();
        }
        
        // Getters e Setters
        public EntityType getType() { return type; }
        public void setType(EntityType type) { this.type = type; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getNormalizedValue() { return normalizedValue; }
        public void setNormalizedValue(String normalizedValue) { this.normalizedValue = normalizedValue; }
        
        public int getStartPosition() { return startPosition; }
        public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
        
        public int getEndPosition() { return endPosition; }
        public void setEndPosition(int endPosition) { this.endPosition = endPosition; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    }
    
    /**
     * Classificador de intenções
     */
    private static class IntentClassifier {
        private final Map<SQLIntent, List<Pattern>> intentPatterns;
        private final Map<SQLIntent, Double> intentWeights;
        
        public IntentClassifier() {
            this.intentPatterns = new HashMap<>();
            this.intentWeights = new HashMap<>();
            initializePatterns();
        }
        
        private void initializePatterns() {
            // Padrões para SELECT
            List<Pattern> selectPatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(mostrar?|exibir|listar|buscar|encontrar|ver|consultar|selecionar)\b.*"),
                Pattern.compile("(?i).*\b(quais?|quantos?|onde|quando|como)\b.*"),
                Pattern.compile("(?i).*\b(dados|informações|registros|resultados)\b.*")
            );
            intentPatterns.put(SQLIntent.SELECT, selectPatterns);
            intentWeights.put(SQLIntent.SELECT, 1.0);
            
            // Padrões para INSERT
            List<Pattern> insertPatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(inserir|adicionar|criar|incluir|cadastrar)\b.*"),
                Pattern.compile("(?i).*\b(novo|nova|novos|novas)\b.*")
            );
            intentPatterns.put(SQLIntent.INSERT, insertPatterns);
            intentWeights.put(SQLIntent.INSERT, 1.0);
            
            // Padrões para UPDATE
            List<Pattern> updatePatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(atualizar|modificar|alterar|mudar|editar)\b.*"),
                Pattern.compile("(?i).*\b(definir|configurar|estabelecer)\b.*")
            );
            intentPatterns.put(SQLIntent.UPDATE, updatePatterns);
            intentWeights.put(SQLIntent.UPDATE, 1.0);
            
            // Padrões para DELETE
            List<Pattern> deletePatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(deletar|excluir|remover|apagar|eliminar)\b.*")
            );
            intentPatterns.put(SQLIntent.DELETE, deletePatterns);
            intentWeights.put(SQLIntent.DELETE, 1.0);
            
            // Padrões para JOIN
            List<Pattern> joinPatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(juntar|unir|combinar|relacionar)\b.*"),
                Pattern.compile("(?i).*\b(com|junto|relacionado)\b.*")
            );
            intentPatterns.put(SQLIntent.JOIN, joinPatterns);
            intentWeights.put(SQLIntent.JOIN, 0.8);
            
            // Padrões para AGGREGATE
            List<Pattern> aggregatePatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(contar|somar|média|máximo|mínimo|total)\b.*"),
                Pattern.compile("(?i).*\b(count|sum|avg|max|min|average)\b.*")
            );
            intentPatterns.put(SQLIntent.AGGREGATE, aggregatePatterns);
            intentWeights.put(SQLIntent.AGGREGATE, 0.9);
            
            // Padrões para GROUP
            List<Pattern> groupPatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(agrupar|grupo|por categoria|por tipo)\b.*"),
                Pattern.compile("(?i).*\b(group by|agrupado)\b.*")
            );
            intentPatterns.put(SQLIntent.GROUP, groupPatterns);
            intentWeights.put(SQLIntent.GROUP, 0.8);
            
            // Padrões para SORT
            List<Pattern> sortPatterns = Arrays.asList(
                Pattern.compile("(?i).*\b(ordenar|classificar|organizar|ordem)\b.*"),
                Pattern.compile("(?i).*\b(crescente|decrescente|alfabética)\b.*")
            );
            intentPatterns.put(SQLIntent.SORT, sortPatterns);
            intentWeights.put(SQLIntent.SORT, 0.7);
        }
        
        public Map<SQLIntent, Double> classifyIntent(String query) {
            Map<SQLIntent, Double> scores = new HashMap<>();
            String normalizedQuery = normalizeText(query);
            
            for (Map.Entry<SQLIntent, List<Pattern>> entry : intentPatterns.entrySet()) {
                SQLIntent intent = entry.getKey();
                List<Pattern> patterns = entry.getValue();
                double score = 0.0;
                
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(normalizedQuery);
                    if (matcher.find()) {
                        score += intentWeights.get(intent) / patterns.size();
                    }
                }
                
                if (score > 0) {
                    scores.put(intent, score);
                }
            }
            
            return scores;
        }
        
        private String normalizeText(String text) {
            if (text == null) return "";
            return Normalizer.normalize(text.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        }
    }
    
    /**
     * Extrator de entidades
     */
    private static class EntityExtractor {
        private final Map<EntityType, List<Pattern>> entityPatterns;
        
        public EntityExtractor() {
            this.entityPatterns = new HashMap<>();
            initializeEntityPatterns();
        }
        
        private void initializeEntityPatterns() {
            // Padrões para nomes de tabela
            List<Pattern> tablePatterns = Arrays.asList(
                Pattern.compile("(?i)\\b(tabela|table)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"),
                Pattern.compile("(?i)\\b(da|do|na|no)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"),
                Pattern.compile("(?i)\\b(usuarios?|clientes?|produtos?|pedidos?|vendas?)\\b")
            );
            entityPatterns.put(EntityType.TABLE_NAME, tablePatterns);
            
            // Padrões para nomes de coluna
            List<Pattern> columnPatterns = Arrays.asList(
                Pattern.compile("(?i)\\b(coluna|campo|field)\\s+([a-zA-Z_][a-zA-Z0-9_]*)"),
                Pattern.compile("(?i)\\b(nome|email|idade|data|valor|preco|quantidade)\\b")
            );
            entityPatterns.put(EntityType.COLUMN_NAME, columnPatterns);
            
            // Padrões para números
            List<Pattern> numberPatterns = Arrays.asList(
                Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"),
                Pattern.compile("(?i)\\b(zero|um|dois|três|quatro|cinco|seis|sete|oito|nove|dez)\\b")
            );
            entityPatterns.put(EntityType.NUMBER, numberPatterns);
            
            // Padrões para datas
            List<Pattern> datePatterns = Arrays.asList(
                Pattern.compile("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b"),
                Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
                Pattern.compile("(?i)\\b(hoje|ontem|amanhã|semana|mês|ano)\\b")
            );
            entityPatterns.put(EntityType.DATE_TIME, datePatterns);
            
            // Padrões para operadores de comparação
            List<Pattern> comparisonPatterns = Arrays.asList(
                Pattern.compile("(?i)\\b(maior|menor|igual|diferente|superior|inferior)\\b"),
                Pattern.compile("(?i)\\b(>=|<=|!=|<>|>|<|=)\\b")
            );
            entityPatterns.put(EntityType.COMPARISON, comparisonPatterns);
            
            // Padrões para funções de agregação
            List<Pattern> aggregatePatterns = Arrays.asList(
                Pattern.compile("(?i)\\b(count|sum|avg|max|min|average|total|contar|somar|média)\\b")
            );
            entityPatterns.put(EntityType.AGGREGATE_FUNCTION, aggregatePatterns);
        }
        
        public List<ExtractedEntity> extractEntities(String query) {
            List<ExtractedEntity> entities = new ArrayList<>();
            
            for (Map.Entry<EntityType, List<Pattern>> entry : entityPatterns.entrySet()) {
                EntityType type = entry.getKey();
                List<Pattern> patterns = entry.getValue();
                
                for (Pattern pattern : patterns) {
                    Matcher matcher = pattern.matcher(query);
                    while (matcher.find()) {
                        ExtractedEntity entity = new ExtractedEntity();
                        entity.setType(type);
                        entity.setValue(matcher.group());
                        entity.setNormalizedValue(normalizeEntityValue(matcher.group(), type));
                        entity.setStartPosition(matcher.start());
                        entity.setEndPosition(matcher.end());
                        entity.setConfidence(0.8); // Confiança padrão
                        
                        entities.add(entity);
                    }
                }
            }
            
            return entities;
        }
        
        private String normalizeEntityValue(String value, EntityType type) {
            if (value == null) return null;
            
            switch (type) {
                case TABLE_NAME:
                case COLUMN_NAME:
                    return value.toLowerCase().replaceAll("[^a-zA-Z0-9_]", "");
                case NUMBER:
                    return value.replaceAll("[^0-9.]", "");
                case DATE_TIME:
                    return value.trim();
                default:
                    return value.toLowerCase().trim();
            }
        }
    }
    
    /**
     * Motor de templates de consulta
     */
    private static class QueryTemplateEngine {
        private final Map<SQLIntent, List<String>> templates;
        
        public QueryTemplateEngine() {
            this.templates = new HashMap<>();
            initializeTemplates();
        }
        
        private void initializeTemplates() {
            // Templates para SELECT
            templates.put(SQLIntent.SELECT, Arrays.asList(
                "SELECT {columns} FROM {table}",
                "SELECT {columns} FROM {table} WHERE {condition}",
                "SELECT {columns} FROM {table} ORDER BY {orderBy}",
                "SELECT {columns} FROM {table} WHERE {condition} ORDER BY {orderBy}",
                "SELECT {columns} FROM {table} LIMIT {limit}"
            ));
            
            // Templates para INSERT
            templates.put(SQLIntent.INSERT, Arrays.asList(
                "INSERT INTO {table} ({columns}) VALUES ({values})",
                "INSERT INTO {table} SET {assignments}"
            ));
            
            // Templates para UPDATE
            templates.put(SQLIntent.UPDATE, Arrays.asList(
                "UPDATE {table} SET {assignments}",
                "UPDATE {table} SET {assignments} WHERE {condition}"
            ));
            
            // Templates para DELETE
            templates.put(SQLIntent.DELETE, Arrays.asList(
                "DELETE FROM {table}",
                "DELETE FROM {table} WHERE {condition}"
            ));
            
            // Templates para JOIN
            templates.put(SQLIntent.JOIN, Arrays.asList(
                "SELECT {columns} FROM {table1} JOIN {table2} ON {joinCondition}",
                "SELECT {columns} FROM {table1} LEFT JOIN {table2} ON {joinCondition}",
                "SELECT {columns} FROM {table1} INNER JOIN {table2} ON {joinCondition}"
            ));
            
            // Templates para AGGREGATE
            templates.put(SQLIntent.AGGREGATE, Arrays.asList(
                "SELECT {aggregateFunction}({column}) FROM {table}",
                "SELECT {aggregateFunction}({column}) FROM {table} WHERE {condition}",
                "SELECT {groupBy}, {aggregateFunction}({column}) FROM {table} GROUP BY {groupBy}"
            ));
        }
        
        public List<String> generateSQL(SQLIntent intent, Map<String, String> parameters) {
            List<String> intentTemplates = templates.get(intent);
            if (intentTemplates == null) {
                return Arrays.asList("-- Template não encontrado para: " + intent);
            }
            
            List<String> generatedQueries = new ArrayList<>();
            
            for (String template : intentTemplates) {
                String query = template;
                
                // Substituir parâmetros no template
                for (Map.Entry<String, String> param : parameters.entrySet()) {
                    String placeholder = "{" + param.getKey() + "}";
                    if (query.contains(placeholder)) {
                        query = query.replace(placeholder, param.getValue());
                    }
                }
                
                // Verificar se ainda há placeholders não substituídos
                if (!query.matches(".*\\{[^}]+\\}.*")) {
                    generatedQueries.add(query);
                }
            }
            
            return generatedQueries;
        }
    }
    
    /**
     * Gerenciador de contexto
     */
    private static class ContextManager {
        private final Map<String, Map<String, Object>> sessionContexts;
        private final Map<String, LocalDateTime> lastAccess;
        
        public ContextManager() {
            this.sessionContexts = new ConcurrentHashMap<>();
            this.lastAccess = new ConcurrentHashMap<>();
        }
        
        public void updateContext(String sessionId, String key, Object value) {
            sessionContexts.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(key, value);
            lastAccess.put(sessionId, LocalDateTime.now());
        }
        
        public Object getContext(String sessionId, String key) {
            Map<String, Object> context = sessionContexts.get(sessionId);
            return context != null ? context.get(key) : null;
        }
        
        public Map<String, Object> getFullContext(String sessionId) {
            return sessionContexts.getOrDefault(sessionId, new HashMap<>());
        }
        
        public void clearContext(String sessionId) {
            sessionContexts.remove(sessionId);
            lastAccess.remove(sessionId);
        }
        
        public void cleanupOldSessions(Duration maxAge) {
            LocalDateTime cutoff = LocalDateTime.now().minus(maxAge);
            
            List<String> expiredSessions = lastAccess.entrySet().stream()
                .filter(entry -> entry.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            expiredSessions.forEach(this::clearContext);
        }
    }
    
    /**
     * Métricas de NLP
     */
    private static class NLPMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong failedRequests = new AtomicLong(0);
        private final Map<SQLIntent, AtomicLong> intentCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> processingTimes = new ConcurrentHashMap<>();
        
        public void recordRequest(SQLIntent intent, Duration processingTime, boolean success) {
            totalRequests.incrementAndGet();
            
            if (success) {
                successfulRequests.incrementAndGet();
            } else {
                failedRequests.incrementAndGet();
            }
            
            if (intent != null) {
                intentCounts.computeIfAbsent(intent, k -> new AtomicLong(0)).incrementAndGet();
            }
            
            String timeRange = getTimeRange(processingTime);
            processingTimes.computeIfAbsent(timeRange, k -> new AtomicLong(0)).incrementAndGet();
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
            metrics.put("failed_requests", failedRequests.get());
            metrics.put("success_rate", 
                totalRequests.get() > 0 ? 
                    (double) successfulRequests.get() / totalRequests.get() : 0.0);
            
            Map<String, Long> intentStats = intentCounts.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> e.getKey().name(),
                    e -> e.getValue().get()
                ));
            metrics.put("intent_distribution", intentStats);
            
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
     * Inicializa componentes
     */
    private void initializeComponents() {
        // Inicializar classificadores por idioma
        intentClassifiers.put("pt", new IntentClassifier());
        intentClassifiers.put("en", new IntentClassifier());
        
        isInitialized.set(true);
    }
    
    /**
     * Processa requisição de linguagem natural
     */
    public CompletableFuture<NLPResponse> processNaturalLanguage(NLPRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            NLPResponse response = new NLPResponse();
            response.setRequestId(request.getRequestId());
            
            try {
                // 1. Classificar intenção
                Map<SQLIntent, Double> intentScores = classifyIntent(request);
                SQLIntent primaryIntent = selectPrimaryIntent(intentScores);
                response.setDetectedIntent(primaryIntent);
                
                // 2. Extrair entidades
                List<ExtractedEntity> entities = entityExtractor.extractEntities(
                    request.getNaturalLanguageQuery());
                response.setExtractedEntities(entities);
                
                // 3. Gerar sugestões SQL
                List<SQLSuggestion> suggestions = generateSQLSuggestions(
                    request, primaryIntent, entities);
                response.setSqlSuggestions(suggestions);
                
                // 4. Calcular confiança geral
                double confidence = calculateOverallConfidence(intentScores, entities, suggestions);
                response.setConfidence(confidence);
                
                // 5. Gerar explicação
                if (request.isIncludeExplanation()) {
                    String explanation = generateExplanation(primaryIntent, entities, suggestions);
                    response.setExplanation(explanation);
                }
                
                // 6. Atualizar contexto
                updateSessionContext(request, response);
                
                response.setSuccess(true);
                
            } catch (Exception e) {
                response.setSuccess(false);
                response.setErrorMessage("Erro no processamento NLP: " + e.getMessage());
            } finally {
                Duration processingTime = Duration.between(startTime, LocalDateTime.now());
                response.setProcessingTime(processingTime);
                
                metrics.recordRequest(response.getDetectedIntent(), 
                    processingTime, response.isSuccess());
            }
            
            return response;
        }, executorService);
    }
    
    /**
     * Classifica intenção da consulta
     */
    private Map<SQLIntent, Double> classifyIntent(NLPRequest request) {
        String language = request.getLanguage();
        IntentClassifier classifier = intentClassifiers.get(language);
        
        if (classifier == null) {
            classifier = intentClassifiers.get("pt"); // Fallback para português
        }
        
        return classifier.classifyIntent(request.getNaturalLanguageQuery());
    }
    
    /**
     * Seleciona intenção primária
     */
    private SQLIntent selectPrimaryIntent(Map<SQLIntent, Double> intentScores) {
        return intentScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(SQLIntent.UNKNOWN);
    }
    
    /**
     * Gera sugestões SQL
     */
    private List<SQLSuggestion> generateSQLSuggestions(NLPRequest request, 
            SQLIntent intent, List<ExtractedEntity> entities) {
        
        Map<String, String> parameters = extractParameters(entities, request);
        List<String> sqlQueries = templateEngine.generateSQL(intent, parameters);
        
        List<SQLSuggestion> suggestions = new ArrayList<>();
        
        for (int i = 0; i < sqlQueries.size() && i < request.getMaxSuggestions(); i++) {
            String sql = sqlQueries.get(i);
            
            SQLSuggestion suggestion = new SQLSuggestion();
            suggestion.setSql(sql);
            suggestion.setConfidence(0.8 - (i * 0.1)); // Decrementa confiança
            suggestion.setExplanation(generateSQLExplanation(sql, intent));
            suggestion.setComplexity(calculateComplexity(sql));
            suggestion.setRequiredTables(extractTablesFromSQL(sql));
            suggestion.setRequiredColumns(extractColumnsFromSQL(sql));
            
            suggestions.add(suggestion);
        }
        
        return suggestions;
    }
    
    /**
     * Extrai parâmetros das entidades
     */
    private Map<String, String> extractParameters(List<ExtractedEntity> entities, NLPRequest request) {
        Map<String, String> parameters = new HashMap<>();
        
        // Valores padrão
        parameters.put("columns", "*");
        parameters.put("table", "table_name");
        parameters.put("condition", "1=1");
        parameters.put("orderBy", "id");
        parameters.put("limit", "10");
        
        // Extrair das entidades
        for (ExtractedEntity entity : entities) {
            switch (entity.getType()) {
                case TABLE_NAME:
                    parameters.put("table", entity.getNormalizedValue());
                    break;
                case COLUMN_NAME:
                    parameters.put("columns", entity.getNormalizedValue());
                    break;
                case NUMBER:
                    if (parameters.get("limit").equals("10")) {
                        parameters.put("limit", entity.getNormalizedValue());
                    }
                    break;
                case AGGREGATE_FUNCTION:
                    parameters.put("aggregateFunction", entity.getNormalizedValue().toUpperCase());
                    break;
            }
        }
        
        // Usar tabelas disponíveis se fornecidas
        if (!request.getAvailableTables().isEmpty() && 
            parameters.get("table").equals("table_name")) {
            parameters.put("table", request.getAvailableTables().get(0));
        }
        
        return parameters;
    }
    
    /**
     * Calcula confiança geral
     */
    private double calculateOverallConfidence(Map<SQLIntent, Double> intentScores, 
            List<ExtractedEntity> entities, List<SQLSuggestion> suggestions) {
        
        double intentConfidence = intentScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .max().orElse(0.0);
        
        double entityConfidence = entities.stream()
            .mapToDouble(ExtractedEntity::getConfidence)
            .average().orElse(0.0);
        
        double suggestionConfidence = suggestions.stream()
            .mapToDouble(SQLSuggestion::getConfidence)
            .average().orElse(0.0);
        
        return (intentConfidence + entityConfidence + suggestionConfidence) / 3.0;
    }
    
    /**
     * Gera explicação
     */
    private String generateExplanation(SQLIntent intent, List<ExtractedEntity> entities, 
            List<SQLSuggestion> suggestions) {
        
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Análise da consulta:\n");
        explanation.append("- Intenção detectada: ").append(intent.getDescription()).append("\n");
        explanation.append("- Entidades encontradas: ").append(entities.size()).append("\n");
        
        if (!entities.isEmpty()) {
            explanation.append("- Tipos de entidades: ");
            entities.stream()
                .map(e -> e.getType().getDescription())
                .distinct()
                .forEach(type -> explanation.append(type).append(", "));
            explanation.setLength(explanation.length() - 2); // Remove última vírgula
            explanation.append("\n");
        }
        
        if (!suggestions.isEmpty()) {
            explanation.append("- Sugestões geradas: ").append(suggestions.size()).append("\n");
            explanation.append("- Melhor sugestão: ").append(suggestions.get(0).getSql());
        }
        
        return explanation.toString();
    }
    
    /**
     * Atualiza contexto da sessão
     */
    private void updateSessionContext(NLPRequest request, NLPResponse response) {
        if (request.getSessionId() != null) {
            contextManager.updateContext(request.getSessionId(), "last_intent", 
                response.getDetectedIntent());
            contextManager.updateContext(request.getSessionId(), "last_entities", 
                response.getExtractedEntities());
            contextManager.updateContext(request.getSessionId(), "last_query", 
                request.getNaturalLanguageQuery());
        }
    }
    
    /**
     * Gera explicação SQL
     */
    private String generateSQLExplanation(String sql, SQLIntent intent) {
        return String.format("Consulta %s gerada: %s", 
            intent.getDescription().toLowerCase(), sql);
    }
    
    /**
     * Calcula complexidade da consulta
     */
    private String calculateComplexity(String sql) {
        if (sql == null) return "LOW";
        
        String upperSql = sql.toUpperCase();
        int complexity = 0;
        
        if (upperSql.contains("JOIN")) complexity += 2;
        if (upperSql.contains("SUBQUERY") || upperSql.contains("(")) complexity += 3;
        if (upperSql.contains("GROUP BY")) complexity += 1;
        if (upperSql.contains("ORDER BY")) complexity += 1;
        if (upperSql.contains("HAVING")) complexity += 2;
        if (upperSql.contains("UNION")) complexity += 2;
        
        if (complexity <= 2) return "LOW";
        if (complexity <= 5) return "MEDIUM";
        return "HIGH";
    }
    
    /**
     * Extrai tabelas do SQL
     */
    private List<String> extractTablesFromSQL(String sql) {
        List<String> tables = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?i)\\b(?:FROM|JOIN|UPDATE|INTO)\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher matcher = pattern.matcher(sql);
        
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        
        return tables.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Extrai colunas do SQL
     */
    private List<String> extractColumnsFromSQL(String sql) {
        List<String> columns = new ArrayList<>();
        
        if (sql.contains("*")) {
            columns.add("*");
        } else {
            Pattern pattern = Pattern.compile("(?i)SELECT\\s+([^\\s]+(?:\\s*,\\s*[^\\s]+)*)");
            Matcher matcher = pattern.matcher(sql);
            
            if (matcher.find()) {
                String columnList = matcher.group(1);
                String[] columnArray = columnList.split(",");
                for (String column : columnArray) {
                    columns.add(column.trim());
                }
            }
        }
        
        return columns;
    }
    
    /**
     * Obtém métricas
     */
    public Map<String, Object> getMetrics() {
        return metrics.getMetrics();
    }
    
    /**
     * Limpa contextos antigos
     */
    public void cleanupOldContexts() {
        contextManager.cleanupOldSessions(Duration.ofHours(24));
    }
    
    /**
     * Finaliza o processador
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