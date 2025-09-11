package ai.chat2db.server.web.start.config.audit;

import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sistema de Log de Auditoria
 * Registra todas as ações importantes do sistema para compliance e segurança
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Slf4j
@Component
public class AuditLogger {

    // Fila de eventos para processamento assíncrono
    private final Queue<AuditEvent> eventQueue = new ConcurrentLinkedQueue<>();
    
    // Cache de estatísticas
    private final Map<String, Long> actionCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> userActionCounts = new ConcurrentHashMap<>();
    
    // Executor para processamento assíncrono
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    
    // Configurações
    private static final int MAX_QUEUE_SIZE = 10000;
    private static final int BATCH_SIZE = 100;
    
    public AuditLogger() {
        // Processa eventos a cada 5 segundos
        executor.scheduleAtFixedRate(this::processEvents, 5, 5, TimeUnit.SECONDS);
        
        // Limpa estatísticas antigas a cada hora
        executor.scheduleAtFixedRate(this::cleanupStats, 1, 1, TimeUnit.HOURS);
    }

    /**
     * Registra evento de autenticação
     */
    public void logAuthentication(String action, String username, String ip, boolean success, String details) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.AUTHENTICATION)
            .action(action)
            .username(username)
            .ipAddress(ip)
            .success(success)
            .details(details)
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra evento de autorização
     */
    public void logAuthorization(String resource, String permission, String username, String ip, boolean success) {
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.AUTHORIZATION)
            .action("ACCESS_" + resource)
            .username(username)
            .ipAddress(ip)
            .success(success)
            .details("Permission: " + permission)
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra operação de banco de dados
     */
    public void logDatabaseOperation(String operation, String database, String table, String sql, HttpServletRequest request) {
        LoginUser user = getCurrentUser();
        String sanitizedSql = sanitizeSql(sql);
        
        Map<String, Object> details = new HashMap<>();
        details.put("database", database);
        details.put("table", table);
        details.put("sql", sanitizedSql);
        details.put("userAgent", request.getHeader("User-Agent"));
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.DATABASE_OPERATION)
            .action(operation)
            .username(user != null ? user.getNickName() : "anonymous")
            .userId(user != null ? user.getId() : null)
            .ipAddress(getClientIp(request))
            .success(true)
            .details(JSONUtil.toJsonStr(details))
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra operação de dados sensíveis
     */
    public void logSensitiveDataAccess(String dataType, String operation, String resourceId, HttpServletRequest request) {
        LoginUser user = getCurrentUser();
        
        Map<String, Object> details = new HashMap<>();
        details.put("dataType", dataType);
        details.put("resourceId", resourceId);
        details.put("userAgent", request.getHeader("User-Agent"));
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.SENSITIVE_DATA_ACCESS)
            .action(operation)
            .username(user != null ? user.getNickName() : "anonymous")
            .userId(user != null ? user.getId() : null)
            .ipAddress(getClientIp(request))
            .success(true)
            .details(JSONUtil.toJsonStr(details))
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra evento de segurança
     */
    public void logSecurityEvent(String eventType, String description, String ip, String userAgent) {
        LoginUser user = getCurrentUser();
        
        Map<String, Object> details = new HashMap<>();
        details.put("description", description);
        details.put("userAgent", userAgent);
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.SECURITY_EVENT)
            .action(eventType)
            .username(user != null ? user.getNickName() : "anonymous")
            .userId(user != null ? user.getId() : null)
            .ipAddress(ip)
            .success(false) // Eventos de segurança geralmente são falhas
            .details(JSONUtil.toJsonStr(details))
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra operação administrativa
     */
    public void logAdminOperation(String operation, String target, Map<String, Object> parameters, HttpServletRequest request) {
        LoginUser user = getCurrentUser();
        
        Map<String, Object> details = new HashMap<>();
        details.put("target", target);
        details.put("parameters", parameters);
        details.put("userAgent", request.getHeader("User-Agent"));
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.ADMIN_OPERATION)
            .action(operation)
            .username(user != null ? user.getNickName() : "anonymous")
            .userId(user != null ? user.getId() : null)
            .ipAddress(getClientIp(request))
            .success(true)
            .details(JSONUtil.toJsonStr(details))
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Registra erro do sistema
     */
    public void logSystemError(String component, String error, String stackTrace, HttpServletRequest request) {
        LoginUser user = getCurrentUser();
        
        Map<String, Object> details = new HashMap<>();
        details.put("component", component);
        details.put("error", error);
        details.put("stackTrace", truncateStackTrace(stackTrace));
        if (request != null) {
            details.put("uri", request.getRequestURI());
            details.put("method", request.getMethod());
            details.put("userAgent", request.getHeader("User-Agent"));
        }
        
        AuditEvent event = AuditEvent.builder()
            .eventType(AuditEventType.SYSTEM_ERROR)
            .action("ERROR")
            .username(user != null ? user.getNickName() : "system")
            .userId(user != null ? user.getId() : null)
            .ipAddress(request != null ? getClientIp(request) : "localhost")
            .success(false)
            .details(JSONUtil.toJsonStr(details))
            .timestamp(LocalDateTime.now())
            .build();
            
        addEvent(event);
    }
    
    /**
     * Adiciona evento à fila
     */
    private void addEvent(AuditEvent event) {
        if (eventQueue.size() >= MAX_QUEUE_SIZE) {
            // Remove eventos antigos se a fila estiver cheia
            eventQueue.poll();
        }
        
        eventQueue.offer(event);
        
        // Atualiza estatísticas
        updateStats(event);
    }
    
    /**
     * Processa eventos da fila
     */
    private void processEvents() {
        List<AuditEvent> batch = new ArrayList<>();
        
        // Coleta um lote de eventos
        for (int i = 0; i < BATCH_SIZE && !eventQueue.isEmpty(); i++) {
            AuditEvent event = eventQueue.poll();
            if (event != null) {
                batch.add(event);
            }
        }
        
        if (!batch.isEmpty()) {
            processBatch(batch);
        }
    }
    
    /**
     * Processa um lote de eventos
     */
    private void processBatch(List<AuditEvent> events) {
        for (AuditEvent event : events) {
            try {
                // Log estruturado para análise
                log.info("AUDIT: {} | Type: {} | User: {} | IP: {} | Success: {} | Details: {}",
                    event.getAction(),
                    event.getEventType(),
                    event.getUsername(),
                    event.getIpAddress(),
                    event.isSuccess(),
                    event.getDetails());
                
                // Aqui poderia ser integrado com sistemas externos:
                // - SIEM (Security Information and Event Management)
                // - Elasticsearch para análise
                // - Banco de dados de auditoria
                // - Sistemas de compliance
                
            } catch (Exception e) {
                log.error("Erro ao processar evento de auditoria: {}", event, e);
            }
        }
    }
    
    /**
     * Atualiza estatísticas
     */
    private void updateStats(AuditEvent event) {
        // Conta por ação
        actionCounts.merge(event.getAction(), 1L, Long::sum);
        
        // Conta por usuário
        if (event.getUsername() != null) {
            userActionCounts.merge(event.getUsername(), 1L, Long::sum);
        }
    }
    
    /**
     * Limpa estatísticas antigas
     */
    private void cleanupStats() {
        // Mantém apenas as top 1000 ações mais frequentes
        if (actionCounts.size() > 1000) {
            actionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .skip(1000)
                .map(Map.Entry::getKey)
                .forEach(actionCounts::remove);
        }
        
        // Mantém apenas os top 500 usuários mais ativos
        if (userActionCounts.size() > 500) {
            userActionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .skip(500)
                .map(Map.Entry::getKey)
                .forEach(userActionCounts::remove);
        }
    }
    
    /**
     * Obtém usuário atual
     */
    private LoginUser getCurrentUser() {
        try {
            return ContextUtils.getLoginUser();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Obtém IP do cliente
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StrUtil.isNotBlank(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StrUtil.isNotBlank(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Sanitiza SQL para log
     */
    private String sanitizeSql(String sql) {
        if (StrUtil.isBlank(sql)) {
            return "";
        }
        
        // Remove dados sensíveis e limita tamanho
        String sanitized = sql.replaceAll("(?i)(password|token|secret|key)\\s*=\\s*'[^']*'", "$1='***'")
                             .replaceAll("(?i)(password|token|secret|key)\\s*=\\s*\"[^\"]*\"", "$1=\"***\"")
                             .replaceAll("(?i)(password|token|secret|key)\\s*=\\s*[^\\s,)]+", "$1=***");
        
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }
    
    /**
     * Trunca stack trace
     */
    private String truncateStackTrace(String stackTrace) {
        if (StrUtil.isBlank(stackTrace)) {
            return "";
        }
        
        return stackTrace.length() > 2000 ? stackTrace.substring(0, 2000) + "..." : stackTrace;
    }
    
    /**
     * Obtém estatísticas de auditoria
     */
    public Map<String, Object> getAuditStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("queueSize", eventQueue.size());
        stats.put("totalActions", actionCounts.values().stream().mapToLong(Long::longValue).sum());
        stats.put("uniqueActions", actionCounts.size());
        stats.put("activeUsers", userActionCounts.size());
        stats.put("topActions", getTopActions(10));
        stats.put("topUsers", getTopUsers(10));
        return stats;
    }
    
    /**
     * Obtém top ações
     */
    private List<Map<String, Object>> getTopActions(int limit) {
        return actionCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> {
                Map<String, Object> item = new HashMap<>();
                item.put("action", entry.getKey());
                item.put("count", entry.getValue());
                return item;
            })
            .toList();
    }
    
    /**
     * Obtém top usuários
     */
    private List<Map<String, Object>> getTopUsers(int limit) {
        return userActionCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> {
                Map<String, Object> item = new HashMap<>();
                item.put("user", entry.getKey());
                item.put("count", entry.getValue());
                return item;
            })
            .toList();
    }
    
    /**
     * Força o processamento de todos os eventos pendentes
     */
    public void flush() {
        while (!eventQueue.isEmpty()) {
            processEvents();
        }
    }
    
    /**
     * Finaliza o logger
     */
    public void shutdown() {
        flush();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}