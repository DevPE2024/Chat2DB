package ai.chat2db.server.domain.core.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gerenciador de modelos de IA para o Chat2DB
 * Responsável por gerenciar os modelos de IA disponíveis, suas configurações e métricas
 */
@Slf4j
public class AIModelManagement {

    private static final AIModelManagement INSTANCE = new AIModelManagement();
    
    private final Map<String, AIModel> registeredModels = new ConcurrentHashMap<>();
    private final Map<String, ModelMetrics> modelMetrics = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean initialized = false;
    
    private AIModelManagement() {
        // Singleton
    }
    
    public static AIModelManagement getInstance() {
        return INSTANCE;
    }
    
    /**
     * Inicializa o gerenciador de modelos de IA
     */
    public synchronized void initialize() {
        if (initialized) {
            log.warn("AIModelManagement já está inicializado");
            return;
        }
        
        // Registra modelos padrão
        registerDefaultModels();
        
        // Inicializa métricas
        initializeMetrics();
        
        initialized = true;
        log.info("AIModelManagement inicializado com sucesso");
    }
    
    /**
     * Registra um novo modelo de IA
     * 
     * @param model Modelo a ser registrado
     * @return true se o registro foi bem-sucedido, false caso contrário
     */
    public boolean registerModel(AIModel model) {
        if (model == null || model.getModelId() == null) {
            log.error("Tentativa de registrar modelo nulo ou sem ID");
            return false;
        }
        
        registeredModels.put(model.getModelId(), model);
        modelMetrics.putIfAbsent(model.getModelId(), new ModelMetrics(model.getModelId()));
        log.info("Modelo registrado com sucesso: {}", model.getModelId());
        return true;
    }
    
    /**
     * Remove um modelo de IA registrado
     * 
     * @param modelId ID do modelo a ser removido
     * @return true se a remoção foi bem-sucedida, false caso contrário
     */
    public boolean unregisterModel(String modelId) {
        if (modelId == null) {
            return false;
        }
        
        registeredModels.remove(modelId);
        log.info("Modelo removido: {}", modelId);
        return true;
    }
    
    /**
     * Atualiza as configurações de um modelo existente
     * 
     * @param modelId ID do modelo a ser atualizado
     * @param config Novas configurações
     * @return true se a atualização foi bem-sucedida, false caso contrário
     */
    public boolean updateModelConfig(String modelId, Map<String, Object> config) {
        AIModel model = registeredModels.get(modelId);
        if (model == null) {
            log.error("Modelo não encontrado para atualização: {}", modelId);
            return false;
        }
        
        model.setConfiguration(config);
        log.info("Configuração do modelo atualizada: {}", modelId);
        return true;
    }
    
    /**
     * Obtém um modelo pelo ID
     * 
     * @param modelId ID do modelo
     * @return O modelo encontrado ou null se não existir
     */
    public AIModel getModel(String modelId) {
        return registeredModels.get(modelId);
    }
    
    /**
     * Lista todos os modelos registrados
     * 
     * @return Lista de modelos
     */
    public List<AIModel> listModels() {
        return registeredModels.values().stream().collect(Collectors.toList());
    }
    
    /**
     * Lista modelos filtrados por provedor
     * 
     * @param provider Nome do provedor
     * @return Lista de modelos do provedor especificado
     */
    public List<AIModel> listModelsByProvider(String provider) {
        return registeredModels.values().stream()
            .filter(model -> provider.equals(model.getProvider()))
            .collect(Collectors.toList());
    }
    
    /**
     * Seleciona o melhor modelo para uma tarefa específica
     * 
     * @param task Tipo de tarefa (SQL_GENERATION, QUERY_OPTIMIZATION, etc)
     * @param contextSize Tamanho do contexto (tokens)
     * @return O modelo mais adequado para a tarefa
     */
    public AIModel selectBestModelForTask(String task, int contextSize) {
        // Implementação básica - pode ser expandida com lógica mais sofisticada
        return registeredModels.values().stream()
            .filter(model -> model.getSupportedTasks().contains(task))
            .filter(model -> model.getMaxContextSize() >= contextSize)
            .sorted((m1, m2) -> {
                // Prioriza modelos com melhor desempenho
                ModelMetrics metrics1 = modelMetrics.get(m1.getModelId());
                ModelMetrics metrics2 = modelMetrics.get(m2.getModelId());
                
                if (metrics1 == null || metrics2 == null) {
                    return 0;
                }
                
                // Menor latência é melhor
                return Double.compare(metrics1.getAverageLatency(), metrics2.getAverageLatency());
            })
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Registra métricas de uso para um modelo
     * 
     * @param modelId ID do modelo
     * @param latencyMs Latência em milissegundos
     * @param tokenCount Número de tokens processados
     * @param success Se a chamada foi bem-sucedida
     */
    public void recordModelUsage(String modelId, long latencyMs, int tokenCount, boolean success) {
        executorService.submit(() -> {
            ModelMetrics metrics = modelMetrics.get(modelId);
            if (metrics != null) {
                metrics.recordUsage(latencyMs, tokenCount, success);
            }
        });
    }
    
    /**
     * Obtém métricas de um modelo específico
     * 
     * @param modelId ID do modelo
     * @return Métricas do modelo ou null se não existir
     */
    public ModelMetrics getModelMetrics(String modelId) {
        return modelMetrics.get(modelId);
    }
    
    /**
     * Obtém métricas de todos os modelos
     * 
     * @return Mapa de métricas por modelo
     */
    public Map<String, ModelMetrics> getAllModelMetrics() {
        return new ConcurrentHashMap<>(modelMetrics);
    }
    
    /**
     * Registra modelos padrão no sistema
     */
    private void registerDefaultModels() {
        // OpenAI
        registerModel(AIModel.builder()
            .modelId("gpt-4")
            .provider("openai")
            .name("GPT-4")
            .description("Modelo avançado da OpenAI para tarefas complexas")
            .maxContextSize(8192)
            .supportedTasks(List.of("SQL_GENERATION", "QUERY_OPTIMIZATION", "SCHEMA_ANALYSIS"))
            .build());
            
        registerModel(AIModel.builder()
            .modelId("gpt-3.5-turbo")
            .provider("openai")
            .name("GPT-3.5 Turbo")
            .description("Modelo balanceado da OpenAI para tarefas gerais")
            .maxContextSize(4096)
            .supportedTasks(List.of("SQL_GENERATION", "QUERY_OPTIMIZATION"))
            .build());
            
        // Anthropic
        registerModel(AIModel.builder()
            .modelId("claude-3-opus")
            .provider("anthropic")
            .name("Claude 3 Opus")
            .description("Modelo avançado da Anthropic para tarefas complexas")
            .maxContextSize(100000)
            .supportedTasks(List.of("SQL_GENERATION", "QUERY_OPTIMIZATION", "SCHEMA_ANALYSIS"))
            .build());
            
        registerModel(AIModel.builder()
            .modelId("claude-3-sonnet")
            .provider("anthropic")
            .name("Claude 3 Sonnet")
            .description("Modelo balanceado da Anthropic")
            .maxContextSize(100000)
            .supportedTasks(List.of("SQL_GENERATION", "QUERY_OPTIMIZATION"))
            .build());
            
        // Google
        registerModel(AIModel.builder()
            .modelId("gemini-pro")
            .provider("google")
            .name("Gemini Pro")
            .description("Modelo avançado do Google para tarefas gerais")
            .maxContextSize(32768)
            .supportedTasks(List.of("SQL_GENERATION", "QUERY_OPTIMIZATION"))
            .build());
    }
    
    /**
     * Inicializa as métricas para todos os modelos registrados
     */
    private void initializeMetrics() {
        registeredModels.keySet().forEach(modelId -> 
            modelMetrics.putIfAbsent(modelId, new ModelMetrics(modelId)));
    }
    
    /**
     * Finaliza o gerenciador de modelos
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AIModelManagement finalizado");
    }
    
    /**
     * Verifica se o gerenciador está inicializado
     * 
     * @return true se inicializado, false caso contrário
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Classe que representa um modelo de IA
     */
    @Data
    @Builder(builderMethodName = "builder")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIModel {
        private String modelId;
        private String provider;
        private String name;
        private String description;
        private int maxContextSize;
        private List<String> supportedTasks;
        private Map<String, Object> configuration;
        
        public static AIModelBuilder builder() {
            return new AIModelBuilder();
        }
    }
    
    /**
     * Classe que armazena métricas de uso de um modelo
     */
    @Data
    public static class ModelMetrics {
        private final String modelId;
        private long totalCalls = 0;
        private long successfulCalls = 0;
        private long failedCalls = 0;
        private long totalLatency = 0;
        private long totalTokens = 0;
        
        public ModelMetrics(String modelId) {
            this.modelId = modelId;
        }
        
        public synchronized void recordUsage(long latencyMs, int tokenCount, boolean success) {
            totalCalls++;
            if (success) {
                successfulCalls++;
            } else {
                failedCalls++;
            }
            totalLatency += latencyMs;
            totalTokens += tokenCount;
        }
        
        public double getAverageLatency() {
            return totalCalls > 0 ? (double) totalLatency / totalCalls : 0;
        }
        
        public double getSuccessRate() {
            return totalCalls > 0 ? (double) successfulCalls / totalCalls : 0;
        }
        
        public double getAverageTokensPerCall() {
            return totalCalls > 0 ? (double) totalTokens / totalCalls : 0;
        }
    }
}