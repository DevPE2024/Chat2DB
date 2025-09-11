package ai.chat2db.spi.ai;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sistema de integração com provedores de IA para Chat2DB
 * Suporte completo ao OpenRouter e múltiplos provedores de IA
 */
public class AIProviderIntegration {
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, AIProvider> providers;
    private final AIMetrics metrics;
    private final ExecutorService executorService;
    private final AtomicBoolean isInitialized;
    
    // Configuração padrão do OpenRouter
    private static final String DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String DEFAULT_OPENROUTER_API_KEY = "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";
    
    public AIProviderIntegration() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        this.providers = new ConcurrentHashMap<>();
        this.metrics = new AIMetrics();
        this.executorService = Executors.newFixedThreadPool(10);
        this.isInitialized = new AtomicBoolean(false);
        
        initializeDefaultProviders();
    }
    
    /**
     * Tipos de provedores de IA suportados
     */
    public enum ProviderType {
        OPENAI_GPT4("OpenAI GPT-4", "gpt-4"),
        OPENAI_GPT35_TURBO("OpenAI GPT-3.5-turbo", "gpt-3.5-turbo"),
        CLAUDE_ANTHROPIC("Claude (Anthropic)", "anthropic/claude-3-sonnet"),
        GEMINI_GOOGLE("Gemini (Google)", "google/gemini-pro"),
        LLAMA2("Llama 2", "meta-llama/llama-2-70b-chat"),
        LLAMA3("Llama 3", "meta-llama/llama-3-70b-instruct"),
        MISTRAL_AI("Mistral AI", "mistralai/mistral-7b-instruct"),
        COHERE("Cohere", "cohere/command-r-plus"),
        PALM2("PaLM 2", "google/palm-2-chat-bison"),
        CHAT2DB_AI("Chat2DB AI", "chat2db/sql-optimizer"),
        ZHIPU_AI("Zhipu AI", "zhipuai/glm-4");
        
        private final String displayName;
        private final String modelId;
        
        ProviderType(String displayName, String modelId) {
            this.displayName = displayName;
            this.modelId = modelId;
        }
        
        public String getDisplayName() { return displayName; }
        public String getModelId() { return modelId; }
    }
    
    /**
     * Configuração do provedor de IA
     */
    public static class AIProviderConfig {
        private String providerId;
        private ProviderType type;
        private String baseUrl;
        private String apiKey;
        private Map<String, String> headers;
        private Map<String, Object> defaultParameters;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;
        private int maxRetries;
        private boolean enabled;
        private int priority;
        private double costPerToken;
        private String description;
        
        public AIProviderConfig() {
            this.headers = new HashMap<>();
            this.defaultParameters = new HashMap<>();
            this.maxTokens = 4000;
            this.temperature = 0.7;
            this.timeoutSeconds = 30;
            this.maxRetries = 3;
            this.enabled = true;
            this.priority = 1;
            this.costPerToken = 0.0;
        }
        
        // Getters e Setters
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        
        public ProviderType getType() { return type; }
        public void setType(ProviderType type) { this.type = type; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public Map<String, Object> getDefaultParameters() { return defaultParameters; }
        public void setDefaultParameters(Map<String, Object> defaultParameters) { this.defaultParameters = defaultParameters; }
        
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public double getCostPerToken() { return costPerToken; }
        public void setCostPerToken(double costPerToken) { this.costPerToken = costPerToken; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * Requisição para IA
     */
    public static class AIRequest {
        private String requestId;
        private String prompt;
        private String systemPrompt;
        private List<ChatMessage> messages;
        private Map<String, Object> parameters;
        private String preferredProvider;
        private boolean streamResponse;
        private Consumer<String> streamCallback;
        private LocalDateTime timestamp;
        private String userId;
        private String sessionId;
        
        public AIRequest() {
            this.requestId = UUID.randomUUID().toString();
            this.messages = new ArrayList<>();
            this.parameters = new HashMap<>();
            this.streamResponse = false;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
        
        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
        
        public List<ChatMessage> getMessages() { return messages; }
        public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public String getPreferredProvider() { return preferredProvider; }
        public void setPreferredProvider(String preferredProvider) { this.preferredProvider = preferredProvider; }
        
        public boolean isStreamResponse() { return streamResponse; }
        public void setStreamResponse(boolean streamResponse) { this.streamResponse = streamResponse; }
        
        public Consumer<String> getStreamCallback() { return streamCallback; }
        public void setStreamCallback(Consumer<String> streamCallback) { this.streamCallback = streamCallback; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
    
    /**
     * Resposta da IA
     */
    public static class AIResponse {
        private String requestId;
        private String providerId;
        private String content;
        private Map<String, Object> metadata;
        private LocalDateTime timestamp;
        private Duration processingTime;
        private int tokensUsed;
        private double cost;
        private boolean success;
        private String errorMessage;
        private List<String> warnings;
        
        public AIResponse() {
            this.metadata = new HashMap<>();
            this.timestamp = LocalDateTime.now();
            this.tokensUsed = 0;
            this.cost = 0.0;
            this.success = false;
            this.warnings = new ArrayList<>();
        }
        
        // Getters e Setters
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public String getProviderId() { return providerId; }
        public void setProviderId(String providerId) { this.providerId = providerId; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Duration getProcessingTime() { return processingTime; }
        public void setProcessingTime(Duration processingTime) { this.processingTime = processingTime; }
        
        public int getTokensUsed() { return tokensUsed; }
        public void setTokensUsed(int tokensUsed) { this.tokensUsed = tokensUsed; }
        
        public double getCost() { return cost; }
        public void setCost(double cost) { this.cost = cost; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }
    
    /**
     * Mensagem de chat
     */
    public static class ChatMessage {
        private String role; // system, user, assistant
        private String content;
        private Map<String, Object> metadata;
        
        public ChatMessage() {
            this.metadata = new HashMap<>();
        }
        
        public ChatMessage(String role, String content) {
            this();
            this.role = role;
            this.content = content;
        }
        
        // Getters e Setters
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * Provedor de IA
     */
    private class AIProvider {
        private final AIProviderConfig config;
        private final AtomicLong requestCount;
        private final AtomicBoolean healthy;
        private LocalDateTime lastHealthCheck;
        
        public AIProvider(AIProviderConfig config) {
            this.config = config;
            this.requestCount = new AtomicLong(0);
            this.healthy = new AtomicBoolean(true);
            this.lastHealthCheck = LocalDateTime.now();
        }
        
        public CompletableFuture<AIResponse> processRequest(AIRequest request) {
            return CompletableFuture.supplyAsync(() -> {
                LocalDateTime startTime = LocalDateTime.now();
                AIResponse response = new AIResponse();
                response.setRequestId(request.getRequestId());
                response.setProviderId(config.getProviderId());
                
                try {
                    // Preparar requisição HTTP
                    Map<String, Object> requestBody = buildRequestBody(request);
                    String jsonBody = objectMapper.writeValueAsString(requestBody);
                    
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                        .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.getApiKey())
                        .headers(config.getHeaders().entrySet().stream()
                            .flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue()))
                            .toArray(String[]::new))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();
                    
                    // Executar requisição
                    HttpResponse<String> httpResponse = httpClient.send(httpRequest, 
                        HttpResponse.BodyHandlers.ofString());
                    
                    if (httpResponse.statusCode() == 200) {
                        // Processar resposta
                        JsonNode responseJson = objectMapper.readTree(httpResponse.body());
                        processSuccessResponse(response, responseJson, startTime);
                        requestCount.incrementAndGet();
                        metrics.recordSuccess(config.getProviderId());
                    } else {
                        // Processar erro
                        processErrorResponse(response, httpResponse, startTime);
                        metrics.recordError(config.getProviderId());
                    }
                    
                } catch (Exception e) {
                    response.setSuccess(false);
                    response.setErrorMessage("Erro na requisição: " + e.getMessage());
                    response.setProcessingTime(Duration.between(startTime, LocalDateTime.now()));
                    metrics.recordError(config.getProviderId());
                }
                
                return response;
            }, executorService);
        }
        
        private Map<String, Object> buildRequestBody(AIRequest request) {
            Map<String, Object> body = new HashMap<>();
            body.put("model", config.getType().getModelId());
            body.put("max_tokens", config.getMaxTokens());
            body.put("temperature", config.getTemperature());
            body.put("stream", request.isStreamResponse());
            
            // Construir mensagens
            List<Map<String, String>> messages = new ArrayList<>();
            
            if (request.getSystemPrompt() != null) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", request.getSystemPrompt());
                messages.add(systemMsg);
            }
            
            if (request.getPrompt() != null) {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", request.getPrompt());
                messages.add(userMsg);
            }
            
            for (ChatMessage msg : request.getMessages()) {
                Map<String, String> chatMsg = new HashMap<>();
                chatMsg.put("role", msg.getRole());
                chatMsg.put("content", msg.getContent());
                messages.add(chatMsg);
            }
            
            body.put("messages", messages);
            
            // Adicionar parâmetros customizados
            body.putAll(config.getDefaultParameters());
            body.putAll(request.getParameters());
            
            return body;
        }
        
        private void processSuccessResponse(AIResponse response, JsonNode responseJson, LocalDateTime startTime) {
            response.setSuccess(true);
            response.setProcessingTime(Duration.between(startTime, LocalDateTime.now()));
            
            // Extrair conteúdo
            JsonNode choices = responseJson.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        response.setContent(content.asText());
                    }
                }
            }
            
            // Extrair informações de uso
            JsonNode usage = responseJson.get("usage");
            if (usage != null) {
                JsonNode totalTokens = usage.get("total_tokens");
                if (totalTokens != null) {
                    response.setTokensUsed(totalTokens.asInt());
                    response.setCost(totalTokens.asInt() * config.getCostPerToken());
                }
            }
            
            // Adicionar metadados
            response.getMetadata().put("model", config.getType().getModelId());
            response.getMetadata().put("provider", config.getType().getDisplayName());
            response.getMetadata().put("raw_response", responseJson.toString());
        }
        
        private void processErrorResponse(AIResponse response, HttpResponse<String> httpResponse, LocalDateTime startTime) {
            response.setSuccess(false);
            response.setProcessingTime(Duration.between(startTime, LocalDateTime.now()));
            response.setErrorMessage("HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            
            try {
                JsonNode errorJson = objectMapper.readTree(httpResponse.body());
                JsonNode error = errorJson.get("error");
                if (error != null) {
                    JsonNode message = error.get("message");
                    if (message != null) {
                        response.setErrorMessage(message.asText());
                    }
                }
            } catch (Exception e) {
                // Ignorar erro de parsing
            }
        }
        
        public boolean isHealthy() {
            return healthy.get() && config.isEnabled();
        }
        
        public AIProviderConfig getConfig() {
            return config;
        }
        
        public long getRequestCount() {
            return requestCount.get();
        }
    }
    
    /**
     * Métricas de IA
     */
    private static class AIMetrics {
        private final Map<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> totalTokens = new ConcurrentHashMap<>();
        private final Map<String, Double> totalCosts = new ConcurrentHashMap<>();
        
        public void recordSuccess(String providerId) {
            successCounts.computeIfAbsent(providerId, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordError(String providerId) {
            errorCounts.computeIfAbsent(providerId, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordTokenUsage(String providerId, int tokens, double cost) {
            totalTokens.computeIfAbsent(providerId, k -> new AtomicLong(0)).addAndGet(tokens);
            totalCosts.merge(providerId, cost, Double::sum);
        }
        
        public Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            Map<String, Long> success = successCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("success_counts", success);
            
            Map<String, Long> errors = errorCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("error_counts", errors);
            
            Map<String, Long> tokens = totalTokens.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("total_tokens", tokens);
            
            metrics.put("total_costs", new HashMap<>(totalCosts));
            
            return metrics;
        }
    }
    
    /**
     * Inicializa provedores padrão
     */
    private void initializeDefaultProviders() {
        // OpenAI GPT-4 via OpenRouter
        AIProviderConfig gpt4Config = new AIProviderConfig();
        gpt4Config.setProviderId("openai-gpt4");
        gpt4Config.setType(ProviderType.OPENAI_GPT4);
        gpt4Config.setBaseUrl(DEFAULT_OPENROUTER_BASE_URL);
        gpt4Config.setApiKey(DEFAULT_OPENROUTER_API_KEY);
        gpt4Config.setDescription("OpenAI GPT-4 via OpenRouter - Modelo principal para geração SQL");
        gpt4Config.setPriority(1);
        gpt4Config.setCostPerToken(0.00003);
        addProvider(gpt4Config);
        
        // OpenAI GPT-3.5-turbo via OpenRouter
        AIProviderConfig gpt35Config = new AIProviderConfig();
        gpt35Config.setProviderId("openai-gpt35-turbo");
        gpt35Config.setType(ProviderType.OPENAI_GPT35_TURBO);
        gpt35Config.setBaseUrl(DEFAULT_OPENROUTER_BASE_URL);
        gpt35Config.setApiKey(DEFAULT_OPENROUTER_API_KEY);
        gpt35Config.setDescription("OpenAI GPT-3.5-turbo via OpenRouter - Modelo alternativo rápido");
        gpt35Config.setPriority(2);
        gpt35Config.setCostPerToken(0.000002);
        addProvider(gpt35Config);
        
        // Claude via OpenRouter
        AIProviderConfig claudeConfig = new AIProviderConfig();
        claudeConfig.setProviderId("claude-anthropic");
        claudeConfig.setType(ProviderType.CLAUDE_ANTHROPIC);
        claudeConfig.setBaseUrl(DEFAULT_OPENROUTER_BASE_URL);
        claudeConfig.setApiKey(DEFAULT_OPENROUTER_API_KEY);
        claudeConfig.setDescription("Claude (Anthropic) via OpenRouter - Para análise complexa de dados");
        claudeConfig.setPriority(3);
        claudeConfig.setCostPerToken(0.000015);
        addProvider(claudeConfig);
        
        // Gemini via OpenRouter
        AIProviderConfig geminiConfig = new AIProviderConfig();
        geminiConfig.setProviderId("gemini-google");
        geminiConfig.setType(ProviderType.GEMINI_GOOGLE);
        geminiConfig.setBaseUrl(DEFAULT_OPENROUTER_BASE_URL);
        geminiConfig.setApiKey(DEFAULT_OPENROUTER_API_KEY);
        geminiConfig.setDescription("Gemini (Google) via OpenRouter - Processamento de linguagem natural");
        geminiConfig.setPriority(4);
        geminiConfig.setCostPerToken(0.000001);
        addProvider(geminiConfig);
        
        // Llama 3 via OpenRouter
        AIProviderConfig llama3Config = new AIProviderConfig();
        llama3Config.setProviderId("llama3");
        llama3Config.setType(ProviderType.LLAMA3);
        llama3Config.setBaseUrl(DEFAULT_OPENROUTER_BASE_URL);
        llama3Config.setApiKey(DEFAULT_OPENROUTER_API_KEY);
        llama3Config.setDescription("Llama 3 via OpenRouter - Modelo open-source");
        llama3Config.setPriority(5);
        llama3Config.setCostPerToken(0.0000005);
        addProvider(llama3Config);
        
        isInitialized.set(true);
    }
    
    /**
     * Adiciona um provedor de IA
     */
    public void addProvider(AIProviderConfig config) {
        validateProviderConfig(config);
        AIProvider provider = new AIProvider(config);
        providers.put(config.getProviderId(), provider);
    }
    
    /**
     * Remove um provedor de IA
     */
    public boolean removeProvider(String providerId) {
        return providers.remove(providerId) != null;
    }
    
    /**
     * Processa requisição de IA
     */
    public CompletableFuture<AIResponse> processRequest(AIRequest request) {
        if (!isInitialized.get()) {
            throw new IllegalStateException("AI Provider Integration não foi inicializado");
        }
        
        AIProvider provider = selectProvider(request);
        if (provider == null) {
            AIResponse errorResponse = new AIResponse();
            errorResponse.setRequestId(request.getRequestId());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Nenhum provedor de IA disponível");
            return CompletableFuture.completedFuture(errorResponse);
        }
        
        return provider.processRequest(request)
            .whenComplete((response, throwable) -> {
                if (response != null && response.isSuccess()) {
                    metrics.recordTokenUsage(response.getProviderId(), 
                        response.getTokensUsed(), response.getCost());
                }
            });
    }
    
    /**
     * Processa requisição simples
     */
    public CompletableFuture<AIResponse> processSimpleRequest(String prompt) {
        AIRequest request = new AIRequest();
        request.setPrompt(prompt);
        return processRequest(request);
    }
    
    /**
     * Processa requisição com sistema
     */
    public CompletableFuture<AIResponse> processRequestWithSystem(String systemPrompt, String userPrompt) {
        AIRequest request = new AIRequest();
        request.setSystemPrompt(systemPrompt);
        request.setPrompt(userPrompt);
        return processRequest(request);
    }
    
    /**
     * Seleciona o melhor provedor para a requisição
     */
    private AIProvider selectProvider(AIRequest request) {
        // Se um provedor específico foi solicitado
        if (request.getPreferredProvider() != null) {
            AIProvider preferred = providers.get(request.getPreferredProvider());
            if (preferred != null && preferred.isHealthy()) {
                return preferred;
            }
        }
        
        // Selecionar por prioridade e saúde
        return providers.values().stream()
            .filter(AIProvider::isHealthy)
            .min(Comparator.comparingInt(p -> p.getConfig().getPriority()))
            .orElse(null);
    }
    
    /**
     * Lista provedores disponíveis
     */
    public List<AIProviderConfig> listProviders() {
        return providers.values().stream()
            .map(AIProvider::getConfig)
            .collect(Collectors.toList());
    }
    
    /**
     * Lista provedores ativos
     */
    public List<AIProviderConfig> listActiveProviders() {
        return providers.values().stream()
            .filter(AIProvider::isHealthy)
            .map(AIProvider::getConfig)
            .collect(Collectors.toList());
    }
    
    /**
     * Obtém informações de um provedor
     */
    public Map<String, Object> getProviderInfo(String providerId) {
        AIProvider provider = providers.get(providerId);
        if (provider == null) {
            return null;
        }
        
        Map<String, Object> info = new HashMap<>();
        AIProviderConfig config = provider.getConfig();
        
        info.put("provider_id", config.getProviderId());
        info.put("type", config.getType().getDisplayName());
        info.put("model_id", config.getType().getModelId());
        info.put("enabled", config.isEnabled());
        info.put("healthy", provider.isHealthy());
        info.put("priority", config.getPriority());
        info.put("request_count", provider.getRequestCount());
        info.put("cost_per_token", config.getCostPerToken());
        info.put("max_tokens", config.getMaxTokens());
        info.put("temperature", config.getTemperature());
        info.put("description", config.getDescription());
        
        return info;
    }
    
    /**
     * Obtém métricas gerais
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> generalMetrics = new HashMap<>();
        generalMetrics.put("total_providers", providers.size());
        generalMetrics.put("active_providers", providers.values().stream()
            .mapToInt(p -> p.isHealthy() ? 1 : 0)
            .sum());
        generalMetrics.put("total_requests", providers.values().stream()
            .mapToLong(AIProvider::getRequestCount)
            .sum());
        
        Map<String, Object> providerMetrics = metrics.getMetrics();
        generalMetrics.putAll(providerMetrics);
        
        return generalMetrics;
    }
    
    /**
     * Testa conectividade de um provedor
     */
    public CompletableFuture<Boolean> testProvider(String providerId) {
        AIProvider provider = providers.get(providerId);
        if (provider == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        AIRequest testRequest = new AIRequest();
        testRequest.setPrompt("Test connection");
        testRequest.setPreferredProvider(providerId);
        
        return provider.processRequest(testRequest)
            .thenApply(AIResponse::isSuccess)
            .exceptionally(throwable -> false);
    }
    
    /**
     * Testa conectividade de todos os provedores
     */
    public CompletableFuture<Map<String, Boolean>> testAllProviders() {
        Map<String, CompletableFuture<Boolean>> tests = new HashMap<>();
        
        for (String providerId : providers.keySet()) {
            tests.put(providerId, testProvider(providerId));
        }
        
        return CompletableFuture.allOf(tests.values().toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, Boolean> results = new HashMap<>();
                tests.forEach((id, future) -> {
                    try {
                        results.put(id, future.get());
                    } catch (Exception e) {
                        results.put(id, false);
                    }
                });
                return results;
            });
    }
    
    /**
     * Valida configuração do provedor
     */
    private void validateProviderConfig(AIProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuração do provedor não pode ser nula");
        }
        
        if (config.getProviderId() == null || config.getProviderId().trim().isEmpty()) {
            throw new IllegalArgumentException("ID do provedor deve ser especificado");
        }
        
        if (config.getType() == null) {
            throw new IllegalArgumentException("Tipo do provedor deve ser especificado");
        }
        
        if (config.getBaseUrl() == null || config.getBaseUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("URL base deve ser especificada");
        }
        
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Chave da API deve ser especificada");
        }
    }
    
    /**
     * Finaliza o sistema
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
        
        providers.clear();
    }
    
    /**
     * Verifica se o sistema está inicializado
     */
    public boolean isInitialized() {
        return isInitialized.get();
    }
}