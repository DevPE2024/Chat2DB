package ai.chat2db.server.domain.core.impl;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Processador de Linguagem Natural
 * Responsável por processar consultas em linguagem natural e convertê-las em SQL
 */
@Slf4j
@Service
public class NaturalLanguageProcessor {

    @Autowired
    private AIProviderIntegration aiProviderIntegration;

    private final Map<String, Object> metrics = new ConcurrentHashMap<>();
    private final Map<String, Object> contextCache = new ConcurrentHashMap<>();
    
    // Padrões para identificação de intenções
    private static final Pattern SELECT_PATTERN = Pattern.compile("(?i).*(mostrar?|exibir?|listar?|selecionar?|buscar?|encontrar?|obter?|recuperar?).*");
    private static final Pattern INSERT_PATTERN = Pattern.compile("(?i).*(inserir?|adicionar?|criar?|incluir?|cadastrar?).*");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("(?i).*(atualizar?|modificar?|alterar?|mudar?).*");
    private static final Pattern DELETE_PATTERN = Pattern.compile("(?i).*(deletar?|remover?|excluir?|apagar?).*");
    private static final Pattern JOIN_PATTERN = Pattern.compile("(?i).*(juntar?|relacionar?|combinar?).*");
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile("(?i).*(contar?|somar?|média|máximo|mínimo|agrupar?).*");

    /**
     * Processa uma consulta em linguagem natural
     * @param query Consulta em linguagem natural
     * @param databaseType Tipo de banco de dados (mysql, postgresql, etc.)
     * @param context Contexto adicional (schema, tabelas, etc.)
     * @return Resultado do processamento
     */
    public Map<String, Object> processQuery(String query, String databaseType, Map<String, Object> context) {
        log.info("Processando consulta em linguagem natural: {}", query);
        
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. Classificação de intenção
            String intent = classifyIntent(query);
            result.put("intent", intent);
            
            // 2. Extração de entidades
            Map<String, Object> entities = extractEntities(query, databaseType, context);
            result.put("entities", entities);
            
            // 3. Geração de template de consulta
            String queryTemplate = generateQueryTemplate(intent, entities, databaseType);
            result.put("queryTemplate", queryTemplate);
            
            // 4. Atualização de métricas
            updateMetrics("success", System.currentTimeMillis() - startTime);
            
            // 5. Atualização de contexto
            updateContext(query, intent, entities, context);
            
            return result;
        } catch (Exception e) {
            log.error("Erro ao processar consulta em linguagem natural: {}", e.getMessage());
            updateMetrics("error", System.currentTimeMillis() - startTime);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Classifica a intenção da consulta
     * @param query Consulta em linguagem natural
     * @return Intenção identificada (SELECT, INSERT, UPDATE, DELETE, etc.)
     */
    public String classifyIntent(String query) {
        if (SELECT_PATTERN.matcher(query).matches()) {
            if (AGGREGATE_PATTERN.matcher(query).matches()) {
                return "SELECT_AGGREGATE";
            } else if (JOIN_PATTERN.matcher(query).matches()) {
                return "SELECT_JOIN";
            } else {
                return "SELECT";
            }
        } else if (INSERT_PATTERN.matcher(query).matches()) {
            return "INSERT";
        } else if (UPDATE_PATTERN.matcher(query).matches()) {
            return "UPDATE";
        } else if (DELETE_PATTERN.matcher(query).matches()) {
            return "DELETE";
        } else {
            // Usar IA para classificar intenções mais complexas
            return classifyIntentWithAI(query);
        }
    }

    /**
     * Classifica a intenção da consulta usando IA
     * @param query Consulta em linguagem natural
     * @return Intenção identificada
     */
    private String classifyIntentWithAI(String query) {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.1); // Baixa temperatura para respostas mais determinísticas
            
            String prompt = "Classifique a seguinte consulta em uma das categorias: SELECT, SELECT_JOIN, SELECT_AGGREGATE, INSERT, UPDATE, DELETE, DDL, ou OTHER.\n\nConsulta: " + query;
            
            String providerId = aiProviderIntegration.selectBestProvider();
            String response = aiProviderIntegration.sendRequest(providerId, prompt, options);
            
            // Extrair a classificação da resposta
            String classification = response.trim().toUpperCase();
            if (classification.contains("SELECT") || classification.contains("INSERT") || 
                classification.contains("UPDATE") || classification.contains("DELETE") || 
                classification.contains("DDL") || classification.contains("OTHER")) {
                return classification;
            } else {
                return "OTHER";
            }
        } catch (Exception e) {
            log.error("Erro ao classificar intenção com IA: {}", e.getMessage());
            return "OTHER";
        }
    }

    /**
     * Extrai entidades da consulta
     * @param query Consulta em linguagem natural
     * @param databaseType Tipo de banco de dados
     * @param context Contexto adicional
     * @return Entidades extraídas
     */
    public Map<String, Object> extractEntities(String query, String databaseType, Map<String, Object> context) {
        Map<String, Object> entities = new HashMap<>();
        
        // Extração básica de entidades usando expressões regulares
        extractTablesAndColumns(query, entities);
        extractConditions(query, entities);
        extractLimits(query, entities);
        
        // Complementar com extração baseada em IA para casos mais complexos
        if (entities.isEmpty() || entities.size() < 2) {
            Map<String, Object> aiEntities = extractEntitiesWithAI(query, databaseType, context);
            entities.putAll(aiEntities);
        }
        
        return entities;
    }

    /**
     * Extrai tabelas e colunas da consulta
     * @param query Consulta em linguagem natural
     * @param entities Mapa de entidades a ser preenchido
     */
    private void extractTablesAndColumns(String query, Map<String, Object> entities) {
        // Implementação simplificada - em um sistema real, seria mais complexo
        List<String> tables = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        
        // Exemplo de extração de tabelas usando padrões comuns
        Pattern tablePattern = Pattern.compile("(?i)\\b(tabela|table|da|na|no|dos|das|de|em)\\s+([a-zA-Z0-9_]+)\\b");
        Matcher tableMatcher = tablePattern.matcher(query);
        while (tableMatcher.find()) {
            tables.add(tableMatcher.group(2).toLowerCase());
        }
        
        // Exemplo de extração de colunas
        Pattern columnPattern = Pattern.compile("(?i)\\b(coluna|column|campo|field|atributo|attribute)\\s+([a-zA-Z0-9_]+)\\b");
        Matcher columnMatcher = columnPattern.matcher(query);
        while (columnMatcher.find()) {
            columns.add(columnMatcher.group(2).toLowerCase());
        }
        
        if (!tables.isEmpty()) {
            entities.put("tables", tables);
        }
        
        if (!columns.isEmpty()) {
            entities.put("columns", columns);
        }
    }

    /**
     * Extrai condições da consulta
     * @param query Consulta em linguagem natural
     * @param entities Mapa de entidades a ser preenchido
     */
    private void extractConditions(String query, Map<String, Object> entities) {
        List<String> conditions = new ArrayList<>();
        
        // Exemplo de extração de condições usando padrões comuns
        Pattern wherePattern = Pattern.compile("(?i)\\b(onde|where|quando|when|que|that|cujo|whose)\\s+([^.]+?)\\s+(é|is|seja|seja igual a|igual a|=|>|<|>=|<=|diferente de|não é|not)\\s+([^.,]+)");
        Matcher whereMatcher = wherePattern.matcher(query);
        while (whereMatcher.find()) {
            String field = whereMatcher.group(2).trim();
            String operator = whereMatcher.group(3).trim();
            String value = whereMatcher.group(4).trim();
            
            // Converter operadores em linguagem natural para SQL
            if (operator.equalsIgnoreCase("é") || operator.equalsIgnoreCase("is") || 
                operator.equalsIgnoreCase("seja") || operator.equalsIgnoreCase("seja igual a") || 
                operator.equalsIgnoreCase("igual a")) {
                operator = "=";
            } else if (operator.equalsIgnoreCase("diferente de") || operator.equalsIgnoreCase("não é") || 
                       operator.equalsIgnoreCase("not")) {
                operator = "!=";
            }
            
            conditions.add(field + " " + operator + " " + value);
        }
        
        if (!conditions.isEmpty()) {
            entities.put("conditions", conditions);
        }
    }

    /**
     * Extrai limites da consulta
     * @param query Consulta em linguagem natural
     * @param entities Mapa de entidades a ser preenchido
     */
    private void extractLimits(String query, Map<String, Object> entities) {
        // Exemplo de extração de limites
        Pattern limitPattern = Pattern.compile("(?i)\\b(limite|limit|limitar a|limitado a|apenas|only|top|primeiros|primeiras)\\s+([0-9]+)\\b");
        Matcher limitMatcher = limitPattern.matcher(query);
        if (limitMatcher.find()) {
            entities.put("limit", Integer.parseInt(limitMatcher.group(2)));
        }
        
        // Exemplo de extração de ordenação
        Pattern orderPattern = Pattern.compile("(?i)\\b(ordenar por|order by|ordenado por|sorted by|classificar por)\\s+([a-zA-Z0-9_]+)\\s+(asc|desc|ascendente|descendente|crescente|decrescente)?\\b");
        Matcher orderMatcher = orderPattern.matcher(query);
        if (orderMatcher.find()) {
            Map<String, String> orderBy = new HashMap<>();
            orderBy.put("column", orderMatcher.group(2).toLowerCase());
            
            String direction = orderMatcher.group(3);
            if (direction != null) {
                if (direction.equalsIgnoreCase("desc") || direction.equalsIgnoreCase("descendente") || 
                    direction.equalsIgnoreCase("decrescente")) {
                    orderBy.put("direction", "DESC");
                } else {
                    orderBy.put("direction", "ASC");
                }
            } else {
                orderBy.put("direction", "ASC"); // Padrão
            }
            
            entities.put("orderBy", orderBy);
        }
    }

    /**
     * Extrai entidades da consulta usando IA
     * @param query Consulta em linguagem natural
     * @param databaseType Tipo de banco de dados
     * @param context Contexto adicional
     * @return Entidades extraídas
     */
    private Map<String, Object> extractEntitiesWithAI(String query, String databaseType, Map<String, Object> context) {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.2);
            
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Extraia as entidades da seguinte consulta em linguagem natural e retorne em formato JSON:\n\n");
            promptBuilder.append("Consulta: ").append(query).append("\n\n");
            promptBuilder.append("Tipo de banco de dados: ").append(databaseType).append("\n\n");
            
            // Adicionar informações de contexto, se disponíveis
            if (context != null && !context.isEmpty()) {
                promptBuilder.append("Contexto disponível:\n");
                if (context.containsKey("schema")) {
                    promptBuilder.append("Schema: ").append(context.get("schema")).append("\n");
                }
                if (context.containsKey("tables")) {
                    promptBuilder.append("Tabelas: ").append(context.get("tables")).append("\n");
                }
            }
            
            promptBuilder.append("\nRetorne apenas o JSON com as entidades extraídas no formato: ");
            promptBuilder.append("{\"tables\": [...], \"columns\": [...], \"conditions\": [...], \"limit\": X, \"orderBy\": {\"column\": Y, \"direction\": Z}}");
            
            String providerId = aiProviderIntegration.selectBestProvider();
            String response = aiProviderIntegration.sendRequest(providerId, promptBuilder.toString(), options);
            
            // Aqui seria necessário um parser JSON para extrair as entidades da resposta
            // Implementação simplificada para demonstração
            Map<String, Object> entities = new HashMap<>();
            entities.put("aiExtracted", response.trim());
            return entities;
        } catch (Exception e) {
            log.error("Erro ao extrair entidades com IA: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Gera um template de consulta SQL
     * @param intent Intenção da consulta
     * @param entities Entidades extraídas
     * @param databaseType Tipo de banco de dados
     * @return Template de consulta SQL
     */
    public String generateQueryTemplate(String intent, Map<String, Object> entities, String databaseType) {
        StringBuilder template = new StringBuilder();
        
        switch (intent) {
            case "SELECT":
                template.append("SELECT ");
                if (entities.containsKey("columns") && !((List<?>)entities.get("columns")).isEmpty()) {
                    template.append(String.join(", ", (List<String>)entities.get("columns")));
                } else {
                    template.append("*");
                }
                
                if (entities.containsKey("tables") && !((List<?>)entities.get("tables")).isEmpty()) {
                    template.append(" FROM ").append(String.join(", ", (List<String>)entities.get("tables")));
                }
                
                if (entities.containsKey("conditions") && !((List<?>)entities.get("conditions")).isEmpty()) {
                    template.append(" WHERE ").append(String.join(" AND ", (List<String>)entities.get("conditions")));
                }
                
                if (entities.containsKey("orderBy")) {
                    Map<?, ?> orderBy = (Map<?, ?>)entities.get("orderBy");
                    template.append(" ORDER BY ").append(orderBy.get("column")).append(" ").append(orderBy.get("direction"));
                }
                
                if (entities.containsKey("limit")) {
                    template.append(" LIMIT ").append(entities.get("limit"));
                }
                break;
                
            case "SELECT_JOIN":
                // Implementação para consultas com JOIN
                template.append("SELECT * FROM ... JOIN ... ON ...");
                break;
                
            case "SELECT_AGGREGATE":
                // Implementação para consultas com agregação
                template.append("SELECT COUNT(*)/SUM(...)/AVG(...) FROM ...");
                break;
                
            case "INSERT":
                // Implementação para consultas de inserção
                template.append("INSERT INTO ... VALUES ...");
                break;
                
            case "UPDATE":
                // Implementação para consultas de atualização
                template.append("UPDATE ... SET ... WHERE ...");
                break;
                
            case "DELETE":
                // Implementação para consultas de exclusão
                template.append("DELETE FROM ... WHERE ...");
                break;
                
            default:
                // Usar IA para gerar template para casos mais complexos
                return generateQueryTemplateWithAI(intent, entities, databaseType);
        }
        
        return template.toString();
    }

    /**
     * Gera um template de consulta SQL usando IA
     * @param intent Intenção da consulta
     * @param entities Entidades extraídas
     * @param databaseType Tipo de banco de dados
     * @return Template de consulta SQL
     */
    private String generateQueryTemplateWithAI(String intent, Map<String, Object> entities, String databaseType) {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.3);
            
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Gere um template de consulta SQL para o seguinte cenário:\n\n");
            promptBuilder.append("Intenção: ").append(intent).append("\n");
            promptBuilder.append("Tipo de banco de dados: ").append(databaseType).append("\n\n");
            promptBuilder.append("Entidades extraídas:\n");
            
            for (Map.Entry<String, Object> entry : entities.entrySet()) {
                promptBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            
            promptBuilder.append("\nRetorne apenas o template SQL, sem explicações adicionais.");
            
            String providerId = aiProviderIntegration.selectBestProvider();
            String response = aiProviderIntegration.sendRequest(providerId, promptBuilder.toString(), options);
            
            return response.trim();
        } catch (Exception e) {
            log.error("Erro ao gerar template de consulta com IA: {}", e.getMessage());
            return "-- Erro ao gerar template de consulta";
        }
    }

    /**
     * Atualiza o contexto da conversa
     * @param query Consulta original
     * @param intent Intenção identificada
     * @param entities Entidades extraídas
     * @param context Contexto atual
     */
    private void updateContext(String query, String intent, Map<String, Object> entities, Map<String, Object> context) {
        String sessionId = (String) context.getOrDefault("sessionId", "default");
        
        Map<String, Object> sessionContext = (Map<String, Object>) contextCache.computeIfAbsent(sessionId, k -> new HashMap<>());
        
        // Armazenar histórico de consultas
        List<String> queryHistory = (List<String>) sessionContext.computeIfAbsent("queryHistory", k -> new ArrayList<>());
        queryHistory.add(query);
        if (queryHistory.size() > 10) { // Limitar o tamanho do histórico
            queryHistory.remove(0);
        }
        
        // Armazenar entidades identificadas para uso futuro
        if (entities.containsKey("tables")) {
            sessionContext.put("lastTables", entities.get("tables"));
        }
        
        if (entities.containsKey("columns")) {
            sessionContext.put("lastColumns", entities.get("columns"));
        }
        
        // Armazenar última intenção
        sessionContext.put("lastIntent", intent);
    }

    /**
     * Atualiza métricas de desempenho
     * @param type Tipo de métrica (success, error)
     * @param processingTime Tempo de processamento em ms
     */
    private void updateMetrics(String type, long processingTime) {
        metrics.compute("totalRequests", (k, v) -> v == null ? 1 : (Integer) v + 1);
        metrics.compute("totalProcessingTime", (k, v) -> v == null ? processingTime : (Long) v + processingTime);
        
        if ("success".equals(type)) {
            metrics.compute("successfulRequests", (k, v) -> v == null ? 1 : (Integer) v + 1);
        } else if ("error".equals(type)) {
            metrics.compute("failedRequests", (k, v) -> v == null ? 1 : (Integer) v + 1);
        }
        
        // Calcular tempo médio de processamento
        Integer totalRequests = (Integer) metrics.getOrDefault("totalRequests", 0);
        Long totalTime = (Long) metrics.getOrDefault("totalProcessingTime", 0L);
        if (totalRequests > 0) {
            metrics.put("averageProcessingTime", totalTime / totalRequests);
        }
    }

    /**
     * Obtém métricas de desempenho
     * @return Métricas de desempenho
     */
    public Map<String, Object> getMetrics() {
        return new HashMap<>(metrics);
    }

    /**
     * Limpa o cache de contexto
     * @param sessionId ID da sessão (opcional)
     */
    public void clearContext(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            contextCache.remove(sessionId);
        } else {
            contextCache.clear();
        }
    }
}