package ai.chat2db.server.web.start.config.validation;

/**
 * Tipos de Entrada para Validação
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
public enum InputType {
    
    /**
     * Consulta SQL
     */
    SQL_QUERY("SQL Query", "Consulta SQL que pode conter comandos perigosos"),
    
    /**
     * Conteúdo HTML
     */
    HTML_CONTENT("HTML Content", "Conteúdo HTML que pode conter scripts maliciosos"),
    
    /**
     * Caminho de arquivo
     */
    FILE_PATH("File Path", "Caminho de arquivo que pode conter path traversal"),
    
    /**
     * Comando do sistema
     */
    COMMAND("System Command", "Comando do sistema que pode ser perigoso"),
    
    /**
     * Consulta LDAP
     */
    LDAP_QUERY("LDAP Query", "Consulta LDAP que pode conter injeção"),
    
    /**
     * Endereço de email
     */
    EMAIL("Email Address", "Endereço de email que deve seguir formato válido"),
    
    /**
     * URL
     */
    URL("URL", "URL que deve seguir formato válido"),
    
    /**
     * Entrada geral
     */
    GENERAL("General Input", "Entrada geral com validações básicas");
    
    private final String displayName;
    private final String description;
    
    InputType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Verifica se é tipo de entrada perigosa
     */
    public boolean isDangerous() {
        return this == SQL_QUERY || this == COMMAND || this == HTML_CONTENT;
    }
    
    /**
     * Verifica se requer validação rigorosa
     */
    public boolean requiresStrictValidation() {
        return this == SQL_QUERY || this == COMMAND || this == LDAP_QUERY;
    }
}