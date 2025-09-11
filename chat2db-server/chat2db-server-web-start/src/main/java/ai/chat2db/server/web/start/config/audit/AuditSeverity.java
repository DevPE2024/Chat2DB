package ai.chat2db.server.web.start.config.audit;

/**
 * Níveis de Severidade para Eventos de Auditoria
 * Define a criticidade e urgência dos eventos auditados
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
public enum AuditSeverity {
    
    /**
     * Informativo - Operações normais do sistema
     */
    INFO(0, "INFO", "Evento informativo - operação normal"),
    
    /**
     * Debug - Informações detalhadas para depuração
     */
    DEBUG(1, "DEBUG", "Informações de debug e diagnóstico"),
    
    /**
     * Aviso - Situações que merecem atenção
     */
    WARNING(2, "WARN", "Situação que merece atenção"),
    
    /**
     * Erro - Problemas que afetam funcionalidade
     */
    ERROR(3, "ERROR", "Erro que afeta o funcionamento"),
    
    /**
     * Crítico - Problemas graves que requerem ação imediata
     */
    CRITICAL(4, "CRITICAL", "Situação crítica que requer ação imediata"),
    
    /**
     * Fatal - Falhas catastróficas do sistema
     */
    FATAL(5, "FATAL", "Falha catastrófica do sistema");
    
    private final int level;
    private final String code;
    private final String description;
    
    AuditSeverity(int level, String code, String description) {
        this.level = level;
        this.code = code;
        this.description = description;
    }
    
    public int getLevel() {
        return level;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Verifica se a severidade requer notificação imediata
     */
    public boolean requiresImmediateNotification() {
        return level >= ERROR.level;
    }
    
    /**
     * Verifica se a severidade requer escalação
     */
    public boolean requiresEscalation() {
        return level >= CRITICAL.level;
    }
    
    /**
     * Verifica se a severidade indica problema de segurança
     */
    public boolean isSecurityIssue() {
        return level >= WARNING.level;
    }
    
    /**
     * Obtém a cor associada à severidade (para interfaces)
     */
    public String getColor() {
        return switch (this) {
            case DEBUG -> "#6c757d"; // Cinza
            case INFO -> "#17a2b8";  // Azul
            case WARNING -> "#ffc107"; // Amarelo
            case ERROR -> "#dc3545";   // Vermelho
            case CRITICAL -> "#e83e8c"; // Rosa
            case FATAL -> "#6f42c1";    // Roxo
        };
    }
    
    /**
     * Obtém o ícone associado à severidade
     */
    public String getIcon() {
        return switch (this) {
            case DEBUG -> "🔍";
            case INFO -> "ℹ️";
            case WARNING -> "⚠️";
            case ERROR -> "❌";
            case CRITICAL -> "🚨";
            case FATAL -> "💀";
        };
    }
    
    /**
     * Obtém o tempo de resposta esperado em minutos
     */
    public int getResponseTimeMinutes() {
        return switch (this) {
            case DEBUG, INFO -> 0; // Sem tempo de resposta
            case WARNING -> 60;    // 1 hora
            case ERROR -> 30;      // 30 minutos
            case CRITICAL -> 15;   // 15 minutos
            case FATAL -> 5;       // 5 minutos
        };
    }
    
    /**
     * Verifica se deve ser incluído em relatórios executivos
     */
    public boolean includeInExecutiveReports() {
        return level >= WARNING.level;
    }
    
    /**
     * Obtém a prioridade de processamento
     */
    public int getProcessingPriority() {
        return switch (this) {
            case FATAL -> 1;
            case CRITICAL -> 2;
            case ERROR -> 3;
            case WARNING -> 4;
            case INFO -> 5;
            case DEBUG -> 6;
        };
    }
    
    /**
     * Determina se deve acionar alertas automáticos
     */
    public boolean shouldTriggerAlerts() {
        return level >= ERROR.level;
    }
    
    /**
     * Obtém o canal de notificação recomendado
     */
    public String getNotificationChannel() {
        return switch (this) {
            case DEBUG, INFO -> "log";
            case WARNING -> "email";
            case ERROR -> "email,slack";
            case CRITICAL -> "email,slack,sms";
            case FATAL -> "email,slack,sms,phone";
        };
    }
    
    /**
     * Compara severidades
     */
    public boolean isMoreSevereThan(AuditSeverity other) {
        return this.level > other.level;
    }
    
    /**
     * Compara severidades
     */
    public boolean isLessSevereThan(AuditSeverity other) {
        return this.level < other.level;
    }
    
    /**
     * Busca severidade por código
     */
    public static AuditSeverity fromCode(String code) {
        for (AuditSeverity severity : values()) {
            if (severity.code.equalsIgnoreCase(code)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Código de severidade inválido: " + code);
    }
    
    /**
     * Busca severidade por nível
     */
    public static AuditSeverity fromLevel(int level) {
        for (AuditSeverity severity : values()) {
            if (severity.level == level) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Nível de severidade inválido: " + level);
    }
    
    /**
     * Obtém a severidade máxima entre duas
     */
    public static AuditSeverity max(AuditSeverity s1, AuditSeverity s2) {
        return s1.level > s2.level ? s1 : s2;
    }
    
    /**
     * Obtém a severidade mínima entre duas
     */
    public static AuditSeverity min(AuditSeverity s1, AuditSeverity s2) {
        return s1.level < s2.level ? s1 : s2;
    }
    
    @Override
    public String toString() {
        return String.format("%s [%d]: %s", code, level, description);
    }
}