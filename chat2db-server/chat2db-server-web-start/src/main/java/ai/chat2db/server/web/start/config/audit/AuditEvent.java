package ai.chat2db.server.web.start.config.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Evento de Auditoria
 * Representa um evento que deve ser registrado no sistema de auditoria
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {
    
    /**
     * Tipo do evento
     */
    private AuditEventType eventType;
    
    /**
     * Ação realizada
     */
    private String action;
    
    /**
     * Nome do usuário
     */
    private String username;
    
    /**
     * ID do usuário
     */
    private Long userId;
    
    /**
     * Endereço IP
     */
    private String ipAddress;
    
    /**
     * Se a operação foi bem-sucedida
     */
    private boolean success;
    
    /**
     * Detalhes adicionais em formato JSON
     */
    private String details;
    
    /**
     * Timestamp do evento
     */
    private LocalDateTime timestamp;
    
    /**
     * Sessão do usuário
     */
    private String sessionId;
    
    /**
     * User Agent
     */
    private String userAgent;
    
    /**
     * Recurso acessado
     */
    private String resource;
    
    /**
     * Método HTTP
     */
    private String httpMethod;
    
    /**
     * Código de resposta HTTP
     */
    private Integer responseCode;
    
    /**
     * Duração da operação em milissegundos
     */
    private Long duration;
    
    /**
     * Tamanho da resposta em bytes
     */
    private Long responseSize;
    
    /**
     * Nível de severidade
     */
    private AuditSeverity severity;
    
    /**
     * Categoria do evento
     */
    private String category;
    
    /**
     * Tags para classificação
     */
    private String tags;
    
    /**
     * Contexto adicional
     */
    private String context;
    
    /**
     * Builder customizado com validações
     */
    public static class AuditEventBuilder {
        
        public AuditEventBuilder eventType(AuditEventType eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public AuditEventBuilder action(String action) {
            this.action = action != null ? action.toUpperCase() : null;
            return this;
        }
        
        public AuditEventBuilder username(String username) {
            this.username = username;
            return this;
        }
        
        public AuditEventBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }
        
        public AuditEventBuilder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }
        
        public AuditEventBuilder success(boolean success) {
            this.success = success;
            // Define severidade baseada no sucesso
            if (!success) {
                this.severity = AuditSeverity.WARNING;
            } else {
                this.severity = AuditSeverity.INFO;
            }
            return this;
        }
        
        public AuditEventBuilder details(String details) {
            this.details = details;
            return this;
        }
        
        public AuditEventBuilder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
            return this;
        }
        
        public AuditEventBuilder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }
        
        public AuditEventBuilder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public AuditEventBuilder resource(String resource) {
            this.resource = resource;
            return this;
        }
        
        public AuditEventBuilder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod != null ? httpMethod.toUpperCase() : null;
            return this;
        }
        
        public AuditEventBuilder responseCode(Integer responseCode) {
            this.responseCode = responseCode;
            
            // Define severidade baseada no código de resposta
            if (responseCode != null) {
                if (responseCode >= 500) {
                    this.severity = AuditSeverity.ERROR;
                } else if (responseCode >= 400) {
                    this.severity = AuditSeverity.WARNING;
                } else if (responseCode >= 200 && responseCode < 300) {
                    this.severity = AuditSeverity.INFO;
                }
            }
            
            return this;
        }
        
        public AuditEventBuilder duration(Long duration) {
            this.duration = duration;
            return this;
        }
        
        public AuditEventBuilder responseSize(Long responseSize) {
            this.responseSize = responseSize;
            return this;
        }
        
        public AuditEventBuilder severity(AuditSeverity severity) {
            this.severity = severity;
            return this;
        }
        
        public AuditEventBuilder category(String category) {
            this.category = category;
            return this;
        }
        
        public AuditEventBuilder tags(String tags) {
            this.tags = tags;
            return this;
        }
        
        public AuditEventBuilder context(String context) {
            this.context = context;
            return this;
        }
        
        public AuditEvent build() {
            // Validações
            if (this.eventType == null) {
                throw new IllegalArgumentException("EventType é obrigatório");
            }
            
            if (this.action == null || this.action.trim().isEmpty()) {
                throw new IllegalArgumentException("Action é obrigatório");
            }
            
            if (this.timestamp == null) {
                this.timestamp = LocalDateTime.now();
            }
            
            if (this.severity == null) {
                this.severity = AuditSeverity.INFO;
            }
            
            return new AuditEvent(
                this.eventType,
                this.action,
                this.username,
                this.userId,
                this.ipAddress,
                this.success,
                this.details,
                this.timestamp,
                this.sessionId,
                this.userAgent,
                this.resource,
                this.httpMethod,
                this.responseCode,
                this.duration,
                this.responseSize,
                this.severity,
                this.category,
                this.tags,
                this.context
            );
        }
    }
    
    /**
     * Cria um builder para eventos de segurança
     */
    public static AuditEventBuilder securityEvent() {
        return AuditEvent.builder()
            .eventType(AuditEventType.SECURITY_EVENT)
            .severity(AuditSeverity.WARNING)
            .category("SECURITY");
    }
    
    /**
     * Cria um builder para eventos de autenticação
     */
    public static AuditEventBuilder authenticationEvent() {
        return AuditEvent.builder()
            .eventType(AuditEventType.AUTHENTICATION)
            .category("AUTH");
    }
    
    /**
     * Cria um builder para eventos de banco de dados
     */
    public static AuditEventBuilder databaseEvent() {
        return AuditEvent.builder()
            .eventType(AuditEventType.DATABASE_OPERATION)
            .category("DATABASE");
    }
    
    /**
     * Cria um builder para eventos administrativos
     */
    public static AuditEventBuilder adminEvent() {
        return AuditEvent.builder()
            .eventType(AuditEventType.ADMIN_OPERATION)
            .category("ADMIN")
            .severity(AuditSeverity.WARNING);
    }
    
    /**
     * Cria um builder para eventos de erro
     */
    public static AuditEventBuilder errorEvent() {
        return AuditEvent.builder()
            .eventType(AuditEventType.SYSTEM_ERROR)
            .category("ERROR")
            .severity(AuditSeverity.ERROR)
            .success(false);
    }
    
    /**
     * Verifica se o evento é crítico
     */
    public boolean isCritical() {
        return severity == AuditSeverity.CRITICAL || 
               severity == AuditSeverity.ERROR ||
               eventType == AuditEventType.SECURITY_EVENT;
    }
    
    /**
     * Obtém uma representação resumida do evento
     */
    public String getSummary() {
        return String.format("%s: %s by %s from %s [%s]", 
            eventType, action, username != null ? username : "anonymous", 
            ipAddress != null ? ipAddress : "unknown", 
            success ? "SUCCESS" : "FAILURE");
    }
    
    @Override
    public String toString() {
        return String.format("AuditEvent{type=%s, action='%s', user='%s', ip='%s', success=%s, timestamp=%s}",
            eventType, action, username, ipAddress, success, timestamp);
    }
}