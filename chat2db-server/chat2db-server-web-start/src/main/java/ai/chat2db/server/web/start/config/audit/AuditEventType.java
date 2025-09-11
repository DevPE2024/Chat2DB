package ai.chat2db.server.web.start.config.audit;

/**
 * Tipos de Eventos de Auditoria
 * Define as categorias principais de eventos que devem ser auditados
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
public enum AuditEventType {
    
    /**
     * Eventos de autenticação
     * Login, logout, falhas de autenticação, etc.
     */
    AUTHENTICATION("AUTH", "Eventos de autenticação e sessão"),
    
    /**
     * Eventos de autorização
     * Verificação de permissões, acesso negado, etc.
     */
    AUTHORIZATION("AUTHZ", "Eventos de autorização e controle de acesso"),
    
    /**
     * Operações de banco de dados
     * Consultas, modificações, conexões, etc.
     */
    DATABASE_OPERATION("DB", "Operações realizadas em bancos de dados"),
    
    /**
     * Acesso a dados sensíveis
     * Visualização de senhas, dados pessoais, etc.
     */
    SENSITIVE_DATA_ACCESS("SENSITIVE", "Acesso a informações sensíveis"),
    
    /**
     * Eventos de segurança
     * Tentativas de ataque, violações, etc.
     */
    SECURITY_EVENT("SECURITY", "Eventos relacionados à segurança"),
    
    /**
     * Operações administrativas
     * Configurações, gerenciamento de usuários, etc.
     */
    ADMIN_OPERATION("ADMIN", "Operações administrativas do sistema"),
    
    /**
     * Erros do sistema
     * Exceções, falhas, problemas técnicos
     */
    SYSTEM_ERROR("ERROR", "Erros e exceções do sistema"),
    
    /**
     * Operações de configuração
     * Mudanças em configurações, parâmetros, etc.
     */
    CONFIGURATION_CHANGE("CONFIG", "Alterações de configuração"),
    
    /**
     * Operações de dados
     * CRUD em entidades de negócio
     */
    DATA_OPERATION("DATA", "Operações de manipulação de dados"),
    
    /**
     * Eventos de API
     * Chamadas de API, integrações externas
     */
    API_ACCESS("API", "Acesso e uso de APIs"),
    
    /**
     * Eventos de exportação/importação
     * Backup, restore, export de dados
     */
    DATA_TRANSFER("TRANSFER", "Transferência e backup de dados"),
    
    /**
     * Eventos de monitoramento
     * Health checks, métricas, status
     */
    MONITORING("MONITOR", "Eventos de monitoramento e saúde do sistema"),
    
    /**
     * Eventos de integração
     * Conectores, plugins, extensões
     */
    INTEGRATION("INTEGRATION", "Eventos de integração com sistemas externos"),
    
    /**
     * Eventos de compliance
     * Auditoria, relatórios regulatórios
     */
    COMPLIANCE("COMPLIANCE", "Eventos relacionados a compliance e regulamentações");
    
    private final String code;
    private final String description;
    
    AuditEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Verifica se o tipo de evento é crítico para segurança
     */
    public boolean isSecurityCritical() {
        return this == SECURITY_EVENT || 
               this == AUTHENTICATION || 
               this == AUTHORIZATION ||
               this == SENSITIVE_DATA_ACCESS;
    }
    
    /**
     * Verifica se o tipo de evento requer retenção de longo prazo
     */
    public boolean requiresLongTermRetention() {
        return this == ADMIN_OPERATION ||
               this == SENSITIVE_DATA_ACCESS ||
               this == SECURITY_EVENT ||
               this == COMPLIANCE ||
               this == CONFIGURATION_CHANGE;
    }
    
    /**
     * Verifica se o tipo de evento deve ser reportado em tempo real
     */
    public boolean requiresRealTimeReporting() {
        return this == SECURITY_EVENT ||
               this == SYSTEM_ERROR ||
               this == AUTHENTICATION;
    }
    
    /**
     * Obtém o nível de prioridade do evento
     */
    public int getPriority() {
        return switch (this) {
            case SECURITY_EVENT -> 1; // Máxima prioridade
            case SYSTEM_ERROR -> 2;
            case AUTHENTICATION, AUTHORIZATION -> 3;
            case ADMIN_OPERATION, SENSITIVE_DATA_ACCESS -> 4;
            case CONFIGURATION_CHANGE, COMPLIANCE -> 5;
            case DATABASE_OPERATION, DATA_OPERATION -> 6;
            case API_ACCESS, DATA_TRANSFER -> 7;
            case INTEGRATION, MONITORING -> 8;
        };
    }
    
    /**
     * Obtém o período de retenção recomendado em dias
     */
    public int getRetentionDays() {
        return switch (this) {
            case COMPLIANCE, ADMIN_OPERATION -> 2555; // 7 anos
            case SECURITY_EVENT, SENSITIVE_DATA_ACCESS -> 1825; // 5 anos
            case AUTHENTICATION, AUTHORIZATION -> 1095; // 3 anos
            case CONFIGURATION_CHANGE, SYSTEM_ERROR -> 730; // 2 anos
            case DATABASE_OPERATION, DATA_OPERATION -> 365; // 1 ano
            case API_ACCESS, DATA_TRANSFER -> 180; // 6 meses
            case INTEGRATION, MONITORING -> 90; // 3 meses
        };
    }
    
    /**
     * Busca tipo por código
     */
    public static AuditEventType fromCode(String code) {
        for (AuditEventType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Código de evento inválido: " + code);
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s): %s", name(), code, description);
    }
}