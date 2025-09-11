package ai.chat2db.server.web.start.config.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultado de Validação
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Data
public class ValidationResult {
    
    private boolean valid = true;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    
    /**
     * Cria resultado válido
     */
    public static ValidationResult valid() {
        return new ValidationResult();
    }
    
    /**
     * Cria resultado inválido com erro
     */
    public static ValidationResult invalid(String error) {
        ValidationResult result = new ValidationResult();
        result.addError(error);
        return result;
    }
    
    /**
     * Adiciona erro
     */
    public void addError(String error) {
        this.errors.add(error);
        this.valid = false;
    }
    
    /**
     * Adiciona aviso
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    /**
     * Mescla com outro resultado
     */
    public void merge(ValidationResult other) {
        if (other != null) {
            this.errors.addAll(other.getErrors());
            this.warnings.addAll(other.getWarnings());
            if (!other.isValid()) {
                this.valid = false;
            }
        }
    }
    
    /**
     * Verifica se tem erros
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    /**
     * Verifica se tem avisos
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    /**
     * Obtém primeiro erro
     */
    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
    
    /**
     * Obtém todos os erros como string
     */
    public String getAllErrors() {
        return String.join("; ", errors);
    }
    
    /**
     * Obtém todos os avisos como string
     */
    public String getAllWarnings() {
        return String.join("; ", warnings);
    }
}