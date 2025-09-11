package ai.chat2db.spi.sql;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de agregação de dados para Chat2DB
 * Responsável por realizar operações de agregação, agrupamento e cálculos estatísticos
 */
public class DataAggregationService {
    
    private final ExecutorService executorService;
    private final Map<String, AggregationCache> cache;
    private final AggregationMetrics metrics;
    
    public DataAggregationService() {
        this.executorService = Executors.newFixedThreadPool(8);
        this.cache = new ConcurrentHashMap<>();
        this.metrics = new AggregationMetrics();
    }
    
    /**
     * Tipos de função de agregação suportados
     */
    public enum AggregationFunction {
        COUNT,          // Contagem
        SUM,            // Soma
        AVG,            // Média
        MIN,            // Mínimo
        MAX,            // Máximo
        MEDIAN,         // Mediana
        MODE,           // Moda
        STDDEV,         // Desvio padrão
        VARIANCE,       // Variância
        PERCENTILE,     // Percentil
        DISTINCT_COUNT, // Contagem de valores únicos
        FIRST,          // Primeiro valor
        LAST,           // Último valor
        CONCAT,         // Concatenação
        GROUP_CONCAT    // Concatenação agrupada
    }
    
    /**
     * Tipos de agrupamento
     */
    public enum GroupingType {
        SIMPLE,         // Agrupamento simples
        HIERARCHICAL,   // Agrupamento hierárquico
        TIME_BASED,     // Agrupamento baseado em tempo
        RANGE_BASED,    // Agrupamento baseado em faixas
        CUSTOM          // Agrupamento customizado
    }
    
    /**
     * Configurações de agregação
     */
    public static class AggregationConfig {
        private List<String> groupByColumns;
        private Map<String, AggregationFunction> aggregations;
        private Map<String, Object> functionParameters;
        private GroupingType groupingType;
        private boolean includeNulls;
        private boolean enableCache;
        private int cacheExpirationMinutes;
        private String sortBy;
        private boolean sortDescending;
        private Integer limit;
        private Map<String, Object> filters;
        private boolean parallelProcessing;
        
        public AggregationConfig() {
            this.groupByColumns = new ArrayList<>();
            this.aggregations = new HashMap<>();
            this.functionParameters = new HashMap<>();
            this.groupingType = GroupingType.SIMPLE;
            this.includeNulls = false;
            this.enableCache = true;
            this.cacheExpirationMinutes = 15;
            this.sortDescending = false;
            this.filters = new HashMap<>();
            this.parallelProcessing = true;
        }
        
        // Getters e Setters
        public List<String> getGroupByColumns() { return groupByColumns; }
        public void setGroupByColumns(List<String> groupByColumns) { this.groupByColumns = groupByColumns; }
        
        public Map<String, AggregationFunction> getAggregations() { return aggregations; }
        public void setAggregations(Map<String, AggregationFunction> aggregations) { this.aggregations = aggregations; }
        
        public Map<String, Object> getFunctionParameters() { return functionParameters; }
        public void setFunctionParameters(Map<String, Object> functionParameters) { this.functionParameters = functionParameters; }
        
        public GroupingType getGroupingType() { return groupingType; }
        public void setGroupingType(GroupingType groupingType) { this.groupingType = groupingType; }
        
        public boolean isIncludeNulls() { return includeNulls; }
        public void setIncludeNulls(boolean includeNulls) { this.includeNulls = includeNulls; }
        
        public boolean isEnableCache() { return enableCache; }
        public void setEnableCache(boolean enableCache) { this.enableCache = enableCache; }
        
        public int getCacheExpirationMinutes() { return cacheExpirationMinutes; }
        public void setCacheExpirationMinutes(int cacheExpirationMinutes) { this.cacheExpirationMinutes = cacheExpirationMinutes; }
        
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        
        public boolean isSortDescending() { return sortDescending; }
        public void setSortDescending(boolean sortDescending) { this.sortDescending = sortDescending; }
        
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        
        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
        
        public boolean isParallelProcessing() { return parallelProcessing; }
        public void setParallelProcessing(boolean parallelProcessing) { this.parallelProcessing = parallelProcessing; }
    }
    
    /**
     * Resultado da agregação
     */
    public static class AggregationResult {
        private List<Map<String, Object>> aggregatedData;
        private Map<String, Object> summary;
        private long totalRecords;
        private long processedRecords;
        private long processingTimeMs;
        private LocalDateTime generatedAt;
        private boolean fromCache;
        private Map<String, Object> metadata;
        
        public AggregationResult() {
            this.aggregatedData = new ArrayList<>();
            this.summary = new HashMap<>();
            this.metadata = new HashMap<>();
            this.generatedAt = LocalDateTime.now();
        }
        
        // Getters e Setters
        public List<Map<String, Object>> getAggregatedData() { return aggregatedData; }
        public void setAggregatedData(List<Map<String, Object>> aggregatedData) { this.aggregatedData = aggregatedData; }
        
        public Map<String, Object> getSummary() { return summary; }
        public void setSummary(Map<String, Object> summary) { this.summary = summary; }
        
        public long getTotalRecords() { return totalRecords; }
        public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }
        
        public long getProcessedRecords() { return processedRecords; }
        public void setProcessedRecords(long processedRecords) { this.processedRecords = processedRecords; }
        
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
        
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        
        public boolean isFromCache() { return fromCache; }
        public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * Cache de agregação
     */
    private static class AggregationCache {
        private final AggregationResult result;
        private final LocalDateTime expiresAt;
        
        public AggregationCache(AggregationResult result, int expirationMinutes) {
            this.result = result;
            this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes);
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
        
        public AggregationResult getResult() {
            return result;
        }
    }
    
    /**
     * Métricas de agregação
     */
    private static class AggregationMetrics {
        private long totalAggregations = 0;
        private long cacheHits = 0;
        private long cacheMisses = 0;
        private long totalProcessingTime = 0;
        
        public synchronized void recordAggregation(long processingTime, boolean fromCache) {
            totalAggregations++;
            totalProcessingTime += processingTime;
            if (fromCache) {
                cacheHits++;
            } else {
                cacheMisses++;
            }
        }
        
        public synchronized Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("total_aggregations", totalAggregations);
            metrics.put("cache_hits", cacheHits);
            metrics.put("cache_misses", cacheMisses);
            metrics.put("cache_hit_rate", totalAggregations > 0 ? (double) cacheHits / totalAggregations : 0.0);
            metrics.put("average_processing_time_ms", totalAggregations > 0 ? totalProcessingTime / totalAggregations : 0);
            return metrics;
        }
    }
    
    /**
     * Realiza agregação de dados de forma síncrona
     */
    public AggregationResult aggregate(List<Map<String, Object>> data, AggregationConfig config) {
        long startTime = System.currentTimeMillis();
        
        validateConfig(config);
        
        String cacheKey = generateCacheKey(data, config);
        
        // Verificar cache se habilitado
        if (config.isEnableCache()) {
            AggregationCache cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                AggregationResult result = cached.getResult();
                result.setFromCache(true);
                metrics.recordAggregation(System.currentTimeMillis() - startTime, true);
                return result;
            }
        }
        
        AggregationResult result = new AggregationResult();
        result.setTotalRecords(data.size());
        result.setFromCache(false);
        
        try {
            // Aplicar filtros se especificados
            List<Map<String, Object>> filteredData = applyFilters(data, config.getFilters());
            result.setProcessedRecords(filteredData.size());
            
            // Realizar agregação baseada no tipo de agrupamento
            List<Map<String, Object>> aggregatedData = performAggregation(filteredData, config);
            
            // Aplicar ordenação se especificada
            if (config.getSortBy() != null) {
                aggregatedData = sortResults(aggregatedData, config.getSortBy(), config.isSortDescending());
            }
            
            // Aplicar limite se especificado
            if (config.getLimit() != null && config.getLimit() > 0) {
                aggregatedData = aggregatedData.stream()
                    .limit(config.getLimit())
                    .collect(Collectors.toList());
            }
            
            result.setAggregatedData(aggregatedData);
            
            // Gerar resumo
            generateSummary(result, config);
            
            // Adicionar metadados
            addMetadata(result, config);
            
            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(processingTime);
            
            // Armazenar no cache se habilitado
            if (config.isEnableCache()) {
                cache.put(cacheKey, new AggregationCache(result, config.getCacheExpirationMinutes()));
            }
            
            metrics.recordAggregation(processingTime, false);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao realizar agregação: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Realiza agregação de dados de forma assíncrona
     */
    public CompletableFuture<AggregationResult> aggregateAsync(List<Map<String, Object>> data, AggregationConfig config) {
        return CompletableFuture.supplyAsync(() -> aggregate(data, config), executorService);
    }
    
    /**
     * Aplica filtros aos dados
     */
    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> data, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return data;
        }
        
        return data.stream()
            .filter(row -> {
                for (Map.Entry<String, Object> filter : filters.entrySet()) {
                    String column = filter.getKey();
                    Object filterValue = filter.getValue();
                    Object rowValue = row.get(column);
                    
                    if (!matchesFilter(rowValue, filterValue)) {
                        return false;
                    }
                }
                return true;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Verifica se um valor corresponde ao filtro
     */
    private boolean matchesFilter(Object rowValue, Object filterValue) {
        if (rowValue == null && filterValue == null) {
            return true;
        }
        if (rowValue == null || filterValue == null) {
            return false;
        }
        
        // Implementação simplificada - pode ser expandida para operadores complexos
        return rowValue.toString().equals(filterValue.toString());
    }
    
    /**
     * Realiza a agregação baseada na configuração
     */
    private List<Map<String, Object>> performAggregation(List<Map<String, Object>> data, AggregationConfig config) {
        switch (config.getGroupingType()) {
            case SIMPLE:
                return performSimpleAggregation(data, config);
            case HIERARCHICAL:
                return performHierarchicalAggregation(data, config);
            case TIME_BASED:
                return performTimeBasedAggregation(data, config);
            case RANGE_BASED:
                return performRangeBasedAggregation(data, config);
            default:
                return performSimpleAggregation(data, config);
        }
    }
    
    /**
     * Realiza agregação simples
     */
    private List<Map<String, Object>> performSimpleAggregation(List<Map<String, Object>> data, AggregationConfig config) {
        if (config.getGroupByColumns().isEmpty()) {
            // Agregação sem agrupamento
            Map<String, Object> result = new HashMap<>();
            
            for (Map.Entry<String, AggregationFunction> entry : config.getAggregations().entrySet()) {
                String column = entry.getKey();
                AggregationFunction function = entry.getValue();
                Object aggregatedValue = calculateAggregation(data, column, function, config);
                result.put(column + "_" + function.name().toLowerCase(), aggregatedValue);
            }
            
            return Arrays.asList(result);
        }
        
        // Agregação com agrupamento
        Map<List<Object>, List<Map<String, Object>>> groups = groupData(data, config.getGroupByColumns(), config.isIncludeNulls());
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map.Entry<List<Object>, List<Map<String, Object>>> group : groups.entrySet()) {
            Map<String, Object> result = new HashMap<>();
            
            // Adicionar colunas de agrupamento
            for (int i = 0; i < config.getGroupByColumns().size(); i++) {
                String column = config.getGroupByColumns().get(i);
                Object value = group.getKey().get(i);
                result.put(column, value);
            }
            
            // Calcular agregações
            for (Map.Entry<String, AggregationFunction> entry : config.getAggregations().entrySet()) {
                String column = entry.getKey();
                AggregationFunction function = entry.getValue();
                Object aggregatedValue = calculateAggregation(group.getValue(), column, function, config);
                result.put(column + "_" + function.name().toLowerCase(), aggregatedValue);
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Realiza agregação hierárquica
     */
    private List<Map<String, Object>> performHierarchicalAggregation(List<Map<String, Object>> data, AggregationConfig config) {
        // Implementação simplificada de agregação hierárquica
        List<Map<String, Object>> results = new ArrayList<>();
        
        // Agregar por cada nível da hierarquia
        for (int level = 1; level <= config.getGroupByColumns().size(); level++) {
            List<String> levelColumns = config.getGroupByColumns().subList(0, level);
            
            AggregationConfig levelConfig = new AggregationConfig();
            levelConfig.setGroupByColumns(levelColumns);
            levelConfig.setAggregations(config.getAggregations());
            levelConfig.setIncludeNulls(config.isIncludeNulls());
            
            List<Map<String, Object>> levelResults = performSimpleAggregation(data, levelConfig);
            
            // Adicionar nível à cada resultado
            for (Map<String, Object> result : levelResults) {
                result.put("hierarchy_level", level);
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Realiza agregação baseada em tempo
     */
    private List<Map<String, Object>> performTimeBasedAggregation(List<Map<String, Object>> data, AggregationConfig config) {
        String timeColumn = (String) config.getFunctionParameters().get("timeColumn");
        String timeUnit = (String) config.getFunctionParameters().get("timeUnit"); // day, week, month, year
        
        if (timeColumn == null || timeUnit == null) {
            return performSimpleAggregation(data, config);
        }
        
        // Agrupar por unidade de tempo
        Map<String, List<Map<String, Object>>> timeGroups = new HashMap<>();
        
        for (Map<String, Object> row : data) {
            Object timeValue = row.get(timeColumn);
            if (timeValue != null) {
                String timeKey = formatTimeKey(timeValue.toString(), timeUnit);
                timeGroups.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(row);
            }
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> group : timeGroups.entrySet()) {
            Map<String, Object> result = new HashMap<>();
            result.put("time_period", group.getKey());
            
            // Calcular agregações
            for (Map.Entry<String, AggregationFunction> entry : config.getAggregations().entrySet()) {
                String column = entry.getKey();
                AggregationFunction function = entry.getValue();
                Object aggregatedValue = calculateAggregation(group.getValue(), column, function, config);
                result.put(column + "_" + function.name().toLowerCase(), aggregatedValue);
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Realiza agregação baseada em faixas
     */
    private List<Map<String, Object>> performRangeBasedAggregation(List<Map<String, Object>> data, AggregationConfig config) {
        String rangeColumn = (String) config.getFunctionParameters().get("rangeColumn");
        List<Double> ranges = (List<Double>) config.getFunctionParameters().get("ranges");
        
        if (rangeColumn == null || ranges == null) {
            return performSimpleAggregation(data, config);
        }
        
        // Agrupar por faixas
        Map<String, List<Map<String, Object>>> rangeGroups = new HashMap<>();
        
        for (Map<String, Object> row : data) {
            Object value = row.get(rangeColumn);
            if (value != null) {
                try {
                    double numValue = Double.parseDouble(value.toString());
                    String rangeKey = findRangeKey(numValue, ranges);
                    rangeGroups.computeIfAbsent(rangeKey, k -> new ArrayList<>()).add(row);
                } catch (NumberFormatException e) {
                    // Ignorar valores não numéricos
                }
            }
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> group : rangeGroups.entrySet()) {
            Map<String, Object> result = new HashMap<>();
            result.put("range", group.getKey());
            
            // Calcular agregações
            for (Map.Entry<String, AggregationFunction> entry : config.getAggregations().entrySet()) {
                String column = entry.getKey();
                AggregationFunction function = entry.getValue();
                Object aggregatedValue = calculateAggregation(group.getValue(), column, function, config);
                result.put(column + "_" + function.name().toLowerCase(), aggregatedValue);
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * Agrupa dados pelas colunas especificadas
     */
    private Map<List<Object>, List<Map<String, Object>>> groupData(List<Map<String, Object>> data, List<String> groupByColumns, boolean includeNulls) {
        Map<List<Object>, List<Map<String, Object>>> groups = new HashMap<>();
        
        for (Map<String, Object> row : data) {
            List<Object> key = new ArrayList<>();
            boolean hasNull = false;
            
            for (String column : groupByColumns) {
                Object value = row.get(column);
                if (value == null) {
                    hasNull = true;
                }
                key.add(value);
            }
            
            if (!hasNull || includeNulls) {
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }
        
        return groups;
    }
    
    /**
     * Calcula agregação para uma função específica
     */
    private Object calculateAggregation(List<Map<String, Object>> data, String column, AggregationFunction function, AggregationConfig config) {
        List<Object> values = data.stream()
            .map(row -> row.get(column))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        switch (function) {
            case COUNT:
                return (long) values.size();
            case SUM:
                return calculateSum(values);
            case AVG:
                return calculateAverage(values);
            case MIN:
                return calculateMin(values);
            case MAX:
                return calculateMax(values);
            case MEDIAN:
                return calculateMedian(values);
            case MODE:
                return calculateMode(values);
            case STDDEV:
                return calculateStandardDeviation(values);
            case VARIANCE:
                return calculateVariance(values);
            case PERCENTILE:
                Double percentile = (Double) config.getFunctionParameters().get("percentile");
                return calculatePercentile(values, percentile != null ? percentile : 50.0);
            case DISTINCT_COUNT:
                return (long) values.stream().distinct().count();
            case FIRST:
                return values.isEmpty() ? null : values.get(0);
            case LAST:
                return values.isEmpty() ? null : values.get(values.size() - 1);
            case CONCAT:
                String separator = (String) config.getFunctionParameters().getOrDefault("separator", ",");
                return values.stream().map(Object::toString).collect(Collectors.joining(separator));
            case GROUP_CONCAT:
                String groupSeparator = (String) config.getFunctionParameters().getOrDefault("separator", ",");
                return values.stream().distinct().map(Object::toString).collect(Collectors.joining(groupSeparator));
            default:
                throw new UnsupportedOperationException("Função de agregação não suportada: " + function);
        }
    }
    
    /**
     * Calcula soma
     */
    private BigDecimal calculateSum(List<Object> values) {
        return values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Calcula média
     */
    private BigDecimal calculateAverage(List<Object> values) {
        List<BigDecimal> numbers = values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (numbers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal sum = numbers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(numbers.size()), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcula mínimo
     */
    private Object calculateMin(List<Object> values) {
        return values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .min(BigDecimal::compareTo)
            .orElse(null);
    }
    
    /**
     * Calcula máximo
     */
    private Object calculateMax(List<Object> values) {
        return values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .max(BigDecimal::compareTo)
            .orElse(null);
    }
    
    /**
     * Calcula mediana
     */
    private BigDecimal calculateMedian(List<Object> values) {
        List<BigDecimal> numbers = values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
        
        if (numbers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        int size = numbers.size();
        if (size % 2 == 0) {
            BigDecimal mid1 = numbers.get(size / 2 - 1);
            BigDecimal mid2 = numbers.get(size / 2);
            return mid1.add(mid2).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            return numbers.get(size / 2);
        }
    }
    
    /**
     * Calcula moda
     */
    private Object calculateMode(List<Object> values) {
        Map<Object, Long> frequency = values.stream()
            .collect(Collectors.groupingBy(
                obj -> obj,
                Collectors.counting()
            ));
        
        return frequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    /**
     * Calcula desvio padrão
     */
    private BigDecimal calculateStandardDeviation(List<Object> values) {
        BigDecimal variance = calculateVariance(values);
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
    
    /**
     * Calcula variância
     */
    private BigDecimal calculateVariance(List<Object> values) {
        List<BigDecimal> numbers = values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        
        if (numbers.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal mean = calculateAverage(values);
        
        BigDecimal sumSquaredDiffs = numbers.stream()
            .map(num -> num.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sumSquaredDiffs.divide(BigDecimal.valueOf(numbers.size() - 1), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calcula percentil
     */
    private BigDecimal calculatePercentile(List<Object> values, double percentile) {
        List<BigDecimal> numbers = values.stream()
            .map(this::toBigDecimal)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
        
        if (numbers.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        double index = (percentile / 100.0) * (numbers.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        
        if (lowerIndex == upperIndex) {
            return numbers.get(lowerIndex);
        }
        
        BigDecimal lowerValue = numbers.get(lowerIndex);
        BigDecimal upperValue = numbers.get(upperIndex);
        BigDecimal weight = BigDecimal.valueOf(index - lowerIndex);
        
        return lowerValue.add(upperValue.subtract(lowerValue).multiply(weight));
    }
    
    /**
     * Converte objeto para BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Formata chave de tempo
     */
    private String formatTimeKey(String timeValue, String timeUnit) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timeValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            switch (timeUnit.toLowerCase()) {
                case "day":
                    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                case "week":
                    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-'W'ww"));
                case "month":
                    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                case "year":
                    return dateTime.format(DateTimeFormatter.ofPattern("yyyy"));
                default:
                    return timeValue;
            }
        } catch (Exception e) {
            return timeValue;
        }
    }
    
    /**
     * Encontra chave de faixa
     */
    private String findRangeKey(double value, List<Double> ranges) {
        Collections.sort(ranges);
        
        for (int i = 0; i < ranges.size() - 1; i++) {
            if (value >= ranges.get(i) && value < ranges.get(i + 1)) {
                return ranges.get(i) + " - " + ranges.get(i + 1);
            }
        }
        
        if (value >= ranges.get(ranges.size() - 1)) {
            return ">= " + ranges.get(ranges.size() - 1);
        }
        
        return "< " + ranges.get(0);
    }
    
    /**
     * Ordena resultados
     */
    private List<Map<String, Object>> sortResults(List<Map<String, Object>> data, String sortBy, boolean descending) {
        Comparator<Map<String, Object>> comparator = (a, b) -> {
            Object valueA = a.get(sortBy);
            Object valueB = b.get(sortBy);
            
            if (valueA == null && valueB == null) return 0;
            if (valueA == null) return 1;
            if (valueB == null) return -1;
            
            if (valueA instanceof Comparable && valueB instanceof Comparable) {
                return ((Comparable) valueA).compareTo(valueB);
            }
            
            return valueA.toString().compareTo(valueB.toString());
        };
        
        if (descending) {
            comparator = comparator.reversed();
        }
        
        return data.stream().sorted(comparator).collect(Collectors.toList());
    }
    
    /**
     * Gera resumo do resultado
     */
    private void generateSummary(AggregationResult result, AggregationConfig config) {
        Map<String, Object> summary = result.getSummary();
        summary.put("groups_count", result.getAggregatedData().size());
        summary.put("aggregation_functions", config.getAggregations().size());
        summary.put("grouping_columns", config.getGroupByColumns().size());
        summary.put("grouping_type", config.getGroupingType().toString());
    }
    
    /**
     * Adiciona metadados ao resultado
     */
    private void addMetadata(AggregationResult result, AggregationConfig config) {
        Map<String, Object> metadata = result.getMetadata();
        metadata.put("parallel_processing", config.isParallelProcessing());
        metadata.put("cache_enabled", config.isEnableCache());
        metadata.put("include_nulls", config.isIncludeNulls());
        metadata.put("filters_applied", !config.getFilters().isEmpty());
    }
    
    /**
     * Gera chave de cache
     */
    private String generateCacheKey(List<Map<String, Object>> data, AggregationConfig config) {
        int dataHash = data.hashCode();
        int configHash = Objects.hash(
            config.getGroupByColumns(),
            config.getAggregations(),
            config.getGroupingType(),
            config.getFilters(),
            config.getFunctionParameters()
        );
        
        return "aggregation_" + dataHash + "_" + configHash;
    }
    
    /**
     * Valida configuração
     */
    private void validateConfig(AggregationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuração de agregação não pode ser nula");
        }
        
        if (config.getAggregations().isEmpty()) {
            throw new IllegalArgumentException("Pelo menos uma função de agregação deve ser especificada");
        }
    }
    
    /**
     * Obtém métricas do serviço
     */
    public Map<String, Object> getMetrics() {
        return metrics.getMetrics();
    }
    
    /**
     * Limpa o cache
     */
    public void clearCache() {
        cache.clear();
    }
    
    /**
     * Finaliza o serviço
     */
    public void shutdown() {
        executorService.shutdown();
        clearCache();
    }
}