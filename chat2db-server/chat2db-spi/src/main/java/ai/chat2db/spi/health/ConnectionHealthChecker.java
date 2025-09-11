package ai.chat2db.spi.health;

import ai.chat2db.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Verificador de saúde das conexões de banco de dados
 * Implementa monitoramento contínuo, detecção de falhas e recuperação automática
 */
@Slf4j
public class ConnectionHealthChecker {
    
    private static final Map<String, ConnectionHealth> HEALTH_STATUS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService HEALTH_SCHEDULER = 
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "connection-health-checker");
                t.setDaemon(true);
                return t;
            });
    
    private static final long HEALTH_CHECK_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5); // 5 minutos
    private static final int CONNECTION_TIMEOUT_MS = 10000; // 10 segundos
    private static final int MAX_RETRY_ATTEMPTS = 3;
    
    static {
        // Inicia verificação periódica de saúde
        HEALTH_SCHEDULER.scheduleAtFixedRate(
                ConnectionHealthChecker::performPeriodicHealthCheck,
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        
        // Limpa status antigos a cada hora
        HEALTH_SCHEDULER.scheduleAtFixedRate(
                ConnectionHealthChecker::cleanupOldHealthStatus,
                TimeUnit.HOURS.toMillis(1),
                TimeUnit.HOURS.toMillis(1),
                TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Verifica a saúde de uma conexão específica
     */
    public static ConnectionHealthResult checkConnectionHealth(ConnectInfo connectInfo) {
        String connectionKey = generateConnectionKey(connectInfo);
        
        ConnectionHealthResult result = new ConnectionHealthResult();
        result.setConnectionKey(connectionKey);
        result.setTimestamp(System.currentTimeMillis());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Testa conexão básica
            boolean canConnect = testBasicConnection(connectInfo);
            result.setCanConnect(canConnect);
            
            if (canConnect) {
                // Testa operações básicas
                result.setCanExecuteQuery(testQueryExecution(connectInfo));
                result.setCanAccessMetadata(testMetadataAccess(connectInfo));
                
                // Mede latência
                long responseTime = System.currentTimeMillis() - startTime;
                result.setResponseTimeMs(responseTime);
                
                // Determina status geral
                if (result.isCanExecuteQuery() && result.isCanAccessMetadata()) {
                    result.setStatus(HealthStatus.HEALTHY);
                } else {
                    result.setStatus(HealthStatus.DEGRADED);
                }
                
                // Verifica se a latência está alta
                if (responseTime > 5000) { // > 5 segundos
                    result.setStatus(HealthStatus.SLOW);
                    result.addWarning("Alta latência detectada: " + responseTime + "ms");
                }
                
            } else {
                result.setStatus(HealthStatus.UNHEALTHY);
                result.addError("Não foi possível estabelecer conexão");
            }
            
        } catch (Exception e) {
            result.setStatus(HealthStatus.UNHEALTHY);
            result.addError("Erro durante verificação de saúde: " + e.getMessage());
            log.error("Erro ao verificar saúde da conexão {}: {}", connectionKey, e.getMessage(), e);
        }
        
        // Atualiza cache de status
        updateHealthStatus(connectionKey, result);
        
        log.info("Verificação de saúde concluída para {}: {} ({}ms)", 
                connectionKey, result.getStatus(), result.getResponseTimeMs());
        
        return result;
    }
    
    /**
     * Obtém o status de saúde atual de uma conexão
     */
    public static ConnectionHealth getConnectionHealth(String connectionKey) {
        return HEALTH_STATUS.get(connectionKey);
    }
    
    /**
     * Obtém todas as conexões monitoradas
     */
    public static Map<String, ConnectionHealth> getAllConnectionsHealth() {
        return new HashMap<>(HEALTH_STATUS);
    }
    
    /**
     * Verifica se uma conexão está saudável
     */
    public static boolean isConnectionHealthy(String connectionKey) {
        ConnectionHealth health = HEALTH_STATUS.get(connectionKey);
        return health != null && 
               health.getLastResult() != null && 
               health.getLastResult().getStatus() == HealthStatus.HEALTHY;
    }
    
    /**
     * Inicia monitoramento contínuo de uma conexão
     */
    public static void startMonitoring(ConnectInfo connectInfo) {
        String connectionKey = generateConnectionKey(connectInfo);
        
        ConnectionHealth health = HEALTH_STATUS.computeIfAbsent(connectionKey, 
                k -> new ConnectionHealth(connectionKey));
        
        health.setMonitored(true);
        health.setConnectInfo(connectInfo);
        
        log.info("Monitoramento iniciado para conexão: {}", connectionKey);
    }
    
    /**
     * Para o monitoramento de uma conexão
     */
    public static void stopMonitoring(String connectionKey) {
        ConnectionHealth health = HEALTH_STATUS.get(connectionKey);
        if (health != null) {
            health.setMonitored(false);
            log.info("Monitoramento parado para conexão: {}", connectionKey);
        }
    }
    
    /**
     * Executa verificação assíncrona de saúde
     */
    public static CompletableFuture<ConnectionHealthResult> checkHealthAsync(ConnectInfo connectInfo) {
        return CompletableFuture.supplyAsync(() -> checkConnectionHealth(connectInfo));
    }
    
    /**
     * Tenta recuperar uma conexão não saudável
     */
    public static boolean attemptConnectionRecovery(ConnectInfo connectInfo) {
        String connectionKey = generateConnectionKey(connectInfo);
        
        log.info("Tentando recuperar conexão: {}", connectionKey);
        
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(1000 * attempt); // Backoff exponencial
                
                ConnectionHealthResult result = checkConnectionHealth(connectInfo);
                
                if (result.getStatus() == HealthStatus.HEALTHY) {
                    log.info("Conexão recuperada com sucesso na tentativa {}: {}", attempt, connectionKey);
                    return true;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Tentativa {} de recuperação falhou para {}: {}", 
                        attempt, connectionKey, e.getMessage());
            }
        }
        
        log.error("Falha na recuperação da conexão após {} tentativas: {}", 
                MAX_RETRY_ATTEMPTS, connectionKey);
        return false;
    }
    
    // Métodos privados
    
    private static boolean testBasicConnection(ConnectInfo connectInfo) {
        try (Connection connection = createConnection(connectInfo)) {
            return connection != null && !connection.isClosed();
        } catch (Exception e) {
            log.debug("Teste de conexão básica falhou: {}", e.getMessage());
            return false;
        }
    }
    
    private static boolean testQueryExecution(ConnectInfo connectInfo) {
        try (Connection connection = createConnection(connectInfo)) {
            String testQuery = getTestQuery(connectInfo.getDbType());
            
            try (PreparedStatement stmt = connection.prepareStatement(testQuery);
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
            
        } catch (Exception e) {
            log.debug("Teste de execução de query falhou: {}", e.getMessage());
            return false;
        }
    }
    
    private static boolean testMetadataAccess(ConnectInfo connectInfo) {
        try (Connection connection = createConnection(connectInfo)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Tenta acessar informações básicas de metadata
            metaData.getDatabaseProductName();
            metaData.getDatabaseProductVersion();
            
            return true;
            
        } catch (Exception e) {
            log.debug("Teste de acesso a metadata falhou: {}", e.getMessage());
            return false;
        }
    }
    
    private static Connection createConnection(ConnectInfo connectInfo) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", connectInfo.getUser());
        props.setProperty("password", connectInfo.getDecryptedPassword());
        props.setProperty("connectTimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        props.setProperty("socketTimeout", String.valueOf(CONNECTION_TIMEOUT_MS));
        
        return DriverManager.getConnection(connectInfo.getUrl(), props);
    }
    
    private static String getTestQuery(String dbType) {
        switch (dbType.toLowerCase()) {
            case "mysql":
            case "mariadb":
                return "SELECT 1";
            case "postgresql":
                return "SELECT 1";
            case "oracle":
                return "SELECT 1 FROM DUAL";
            case "sqlserver":
                return "SELECT 1";
            case "h2":
                return "SELECT 1";
            case "sqlite":
                return "SELECT 1";
            default:
                return "SELECT 1";
        }
    }
    
    private static String generateConnectionKey(ConnectInfo connectInfo) {
        return String.format("%s_%s_%s_%s", 
                connectInfo.getDbType(),
                connectInfo.getHost(),
                connectInfo.getPort(),
                connectInfo.getDatabaseName());
    }
    
    private static void updateHealthStatus(String connectionKey, ConnectionHealthResult result) {
        ConnectionHealth health = HEALTH_STATUS.computeIfAbsent(connectionKey, 
                k -> new ConnectionHealth(connectionKey));
        
        health.setLastResult(result);
        health.setLastCheckTime(System.currentTimeMillis());
        
        // Atualiza histórico (mantém últimos 10 resultados)
        health.addToHistory(result);
        
        // Atualiza estatísticas
        health.updateStatistics(result);
    }
    
    private static void performPeriodicHealthCheck() {
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            HEALTH_STATUS.values().stream()
                    .filter(ConnectionHealth::isMonitored)
                    .filter(health -> health.getConnectInfo() != null)
                    .forEach(health -> {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            try {
                                checkConnectionHealth(health.getConnectInfo());
                            } catch (Exception e) {
                                log.error("Erro na verificação periódica de {}: {}", 
                                        health.getConnectionKey(), e.getMessage());
                            }
                        });
                        futures.add(future);
                    });
            
            // Aguarda todas as verificações completarem (com timeout)
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
            
            log.debug("Verificação periódica de saúde concluída para {} conexões", futures.size());
            
        } catch (Exception e) {
            log.error("Erro durante verificação periódica de saúde: {}", e.getMessage(), e);
        }
    }
    
    private static void cleanupOldHealthStatus() {
        long cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        
        HEALTH_STATUS.entrySet().removeIf(entry -> {
            ConnectionHealth health = entry.getValue();
            return !health.isMonitored() && health.getLastCheckTime() < cutoffTime;
        });
        
        log.debug("Limpeza de status antigos concluída. Conexões monitoradas: {}", 
                HEALTH_STATUS.size());
    }
    
    /**
     * Enum para status de saúde
     */
    public enum HealthStatus {
        HEALTHY("Saudável"),
        DEGRADED("Degradado"),
        SLOW("Lento"),
        UNHEALTHY("Não saudável"),
        UNKNOWN("Desconhecido");
        
        private final String description;
        
        HealthStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Classe para resultado de verificação de saúde
     */
    public static class ConnectionHealthResult {
        private String connectionKey;
        private long timestamp;
        private HealthStatus status = HealthStatus.UNKNOWN;
        private boolean canConnect = false;
        private boolean canExecuteQuery = false;
        private boolean canAccessMetadata = false;
        private long responseTimeMs = 0;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        
        // Getters e Setters
        public String getConnectionKey() { return connectionKey; }
        public void setConnectionKey(String connectionKey) { this.connectionKey = connectionKey; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public HealthStatus getStatus() { return status; }
        public void setStatus(HealthStatus status) { this.status = status; }
        
        public boolean isCanConnect() { return canConnect; }
        public void setCanConnect(boolean canConnect) { this.canConnect = canConnect; }
        
        public boolean isCanExecuteQuery() { return canExecuteQuery; }
        public void setCanExecuteQuery(boolean canExecuteQuery) { this.canExecuteQuery = canExecuteQuery; }
        
        public boolean isCanAccessMetadata() { return canAccessMetadata; }
        public void setCanAccessMetadata(boolean canAccessMetadata) { this.canAccessMetadata = canAccessMetadata; }
        
        public long getResponseTimeMs() { return responseTimeMs; }
        public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
        
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }
    }
    
    /**
     * Classe para manter status de saúde de uma conexão
     */
    public static class ConnectionHealth {
        private final String connectionKey;
        private ConnectInfo connectInfo;
        private boolean monitored = false;
        private long lastCheckTime = 0;
        private ConnectionHealthResult lastResult;
        private final List<ConnectionHealthResult> history = new ArrayList<>();
        private long totalChecks = 0;
        private long successfulChecks = 0;
        private double averageResponseTime = 0;
        
        public ConnectionHealth(String connectionKey) {
            this.connectionKey = connectionKey;
        }
        
        public void addToHistory(ConnectionHealthResult result) {
            history.add(result);
            // Mantém apenas os últimos 10 resultados
            if (history.size() > 10) {
                history.remove(0);
            }
        }
        
        public void updateStatistics(ConnectionHealthResult result) {
            totalChecks++;
            if (result.getStatus() == HealthStatus.HEALTHY) {
                successfulChecks++;
            }
            
            // Atualiza média de tempo de resposta
            averageResponseTime = ((averageResponseTime * (totalChecks - 1)) + result.getResponseTimeMs()) / totalChecks;
        }
        
        public double getSuccessRate() {
            return totalChecks > 0 ? (double) successfulChecks / totalChecks : 0.0;
        }
        
        // Getters e Setters
        public String getConnectionKey() { return connectionKey; }
        public ConnectInfo getConnectInfo() { return connectInfo; }
        public void setConnectInfo(ConnectInfo connectInfo) { this.connectInfo = connectInfo; }
        public boolean isMonitored() { return monitored; }
        public void setMonitored(boolean monitored) { this.monitored = monitored; }
        public long getLastCheckTime() { return lastCheckTime; }
        public void setLastCheckTime(long lastCheckTime) { this.lastCheckTime = lastCheckTime; }
        public ConnectionHealthResult getLastResult() { return lastResult; }
        public void setLastResult(ConnectionHealthResult lastResult) { this.lastResult = lastResult; }
        public List<ConnectionHealthResult> getHistory() { return new ArrayList<>(history); }
        public long getTotalChecks() { return totalChecks; }
        public long getSuccessfulChecks() { return successfulChecks; }
        public double getAverageResponseTime() { return averageResponseTime; }
    }
}