package ai.chat2db.server.web.start.config.validation;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validador de Entrada de Dados
 * Implementa validações de segurança para prevenir ataques de injeção
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Slf4j
@Component
public class InputValidator {

    // Padrões de SQL Injection
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute)\\s+.*"),
        Pattern.compile("(?i).*('|(\\-\\-)|;|\\||\\*|%).*"),
        Pattern.compile("(?i).*(script|javascript|vbscript|onload|onerror|onclick).*"),
        Pattern.compile("(?i).*(char|nchar|varchar|nvarchar|alter|begin|cast|create|cursor|declare|delete|drop|end|exec|execute|fetch|insert|kill|open|select|sys|sysobjects|syscolumns|table|update)\\s*\\(.*\\).*")
    );

    // Padrões de XSS
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*<\\s*script[^>]*>.*</\\s*script\\s*>.*"),
        Pattern.compile("(?i).*<\\s*iframe[^>]*>.*</\\s*iframe\\s*>.*"),
        Pattern.compile("(?i).*<\\s*object[^>]*>.*</\\s*object\\s*>.*"),
        Pattern.compile("(?i).*<\\s*embed[^>]*>.*"),
        Pattern.compile("(?i).*<\\s*link[^>]*>.*"),
        Pattern.compile("(?i).*javascript\\s*:.*"),
        Pattern.compile("(?i).*on\\w+\\s*=.*"),
        Pattern.compile("(?i).*<\\s*img[^>]*src\\s*=\\s*['\"]?\\s*javascript:.*"),
        Pattern.compile("(?i).*<\\s*svg[^>]*>.*</\\s*svg\\s*>.*")
    );

    // Padrões de Path Traversal
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\.[\\\\/].*"),
        Pattern.compile(".*[\\\\/]\\.\\.[\\\\/].*"),
        Pattern.compile(".*%2e%2e[\\\\/].*"),
        Pattern.compile(".*%2e%2e%2f.*"),
        Pattern.compile(".*%2e%2e%5c.*"),
        Pattern.compile(".*\\.\\.\\\\.*")
    );

    // Padrões de Command Injection
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i).*(;|\\||&|`|\\$\\(|\\${).*"),
        Pattern.compile("(?i).*(cmd|powershell|bash|sh|exec|system|eval)\\s*\\(.*\\).*"),
        Pattern.compile("(?i).*(rm|del|format|shutdown|reboot|kill|killall)\\s+.*")
    );

    // Padrões de LDAP Injection
    private static final List<Pattern> LDAP_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*[\\(\\)\\*\\\\\\|&].*"),
        Pattern.compile(".*\\x00.*")
    );

    // Caracteres perigosos
    private static final Set<Character> DANGEROUS_CHARS = Set.of(
        '<', '>', '"', '\'', '&', '\0', '\r', '\n', '\t'
    );

    // Tamanhos máximos por tipo de campo
    private static final Map<String, Integer> MAX_LENGTHS = Map.of(
        "username", 50,
        "email", 100,
        "password", 128,
        "name", 100,
        "description", 1000,
        "query", 5000,
        "url", 2048,
        "default", 255
    );

    /**
     * Valida entrada geral
     */
    public ValidationResult validateInput(String input, String fieldName, InputType type) {
        if (input == null) {
            return ValidationResult.valid();
        }

        ValidationResult result = new ValidationResult();

        // Validação de tamanho
        if (!validateLength(input, fieldName)) {
            result.addError("Campo '" + fieldName + "' excede o tamanho máximo permitido");
        }

        // Validações específicas por tipo
        switch (type) {
            case SQL_QUERY:
                validateSqlInjection(input, result);
                break;
            case HTML_CONTENT:
                validateXss(input, result);
                break;
            case FILE_PATH:
                validatePathTraversal(input, result);
                break;
            case COMMAND:
                validateCommandInjection(input, result);
                break;
            case LDAP_QUERY:
                validateLdapInjection(input, result);
                break;
            case EMAIL:
                validateEmail(input, result);
                break;
            case URL:
                validateUrl(input, result);
                break;
            case GENERAL:
            default:
                validateGeneral(input, result);
                break;
        }

        return result;
    }

    /**
     * Sanitiza entrada removendo caracteres perigosos
     */
    public String sanitizeInput(String input, InputType type) {
        if (StrUtil.isBlank(input)) {
            return input;
        }

        String sanitized = input;

        switch (type) {
            case HTML_CONTENT:
                sanitized = sanitizeHtml(sanitized);
                break;
            case SQL_QUERY:
                sanitized = sanitizeSql(sanitized);
                break;
            case FILE_PATH:
                sanitized = sanitizePath(sanitized);
                break;
            case COMMAND:
                sanitized = sanitizeCommand(sanitized);
                break;
            case GENERAL:
            default:
                sanitized = sanitizeGeneral(sanitized);
                break;
        }

        return sanitized;
    }

    /**
     * Valida múltiplos campos
     */
    public ValidationResult validateFields(Map<String, Object> fields, Map<String, InputType> fieldTypes) {
        ValidationResult result = new ValidationResult();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            InputType type = fieldTypes.getOrDefault(fieldName, InputType.GENERAL);

            if (value instanceof String) {
                ValidationResult fieldResult = validateInput((String) value, fieldName, type);
                result.merge(fieldResult);
            } else if (value instanceof Collection) {
                for (Object item : (Collection<?>) value) {
                    if (item instanceof String) {
                        ValidationResult itemResult = validateInput((String) item, fieldName, type);
                        result.merge(itemResult);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Valida SQL Injection
     */
    private void validateSqlInjection(String input, ValidationResult result) {
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                result.addError("Possível tentativa de SQL Injection detectada");
                log.warn("SQL Injection detectado: {}", maskSensitiveData(input));
                break;
            }
        }
    }

    /**
     * Valida XSS
     */
    private void validateXss(String input, ValidationResult result) {
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                result.addError("Possível tentativa de XSS detectada");
                log.warn("XSS detectado: {}", maskSensitiveData(input));
                break;
            }
        }
    }

    /**
     * Valida Path Traversal
     */
    private void validatePathTraversal(String input, ValidationResult result) {
        for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                result.addError("Possível tentativa de Path Traversal detectada");
                log.warn("Path Traversal detectado: {}", maskSensitiveData(input));
                break;
            }
        }
    }

    /**
     * Valida Command Injection
     */
    private void validateCommandInjection(String input, ValidationResult result) {
        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                result.addError("Possível tentativa de Command Injection detectada");
                log.warn("Command Injection detectado: {}", maskSensitiveData(input));
                break;
            }
        }
    }

    /**
     * Valida LDAP Injection
     */
    private void validateLdapInjection(String input, ValidationResult result) {
        for (Pattern pattern : LDAP_INJECTION_PATTERNS) {
            if (pattern.matcher(input).matches()) {
                result.addError("Possível tentativa de LDAP Injection detectada");
                log.warn("LDAP Injection detectado: {}", maskSensitiveData(input));
                break;
            }
        }
    }

    /**
     * Valida email
     */
    private void validateEmail(String input, ValidationResult result) {
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$");
        if (!emailPattern.matcher(input).matches()) {
            result.addError("Formato de email inválido");
        }
    }

    /**
     * Valida URL
     */
    private void validateUrl(String input, ValidationResult result) {
        Pattern urlPattern = Pattern.compile("^https?://[A-Za-z0-9.-]+(/.*)?$");
        if (!urlPattern.matcher(input).matches()) {
            result.addError("Formato de URL inválido");
        }
    }

    /**
     * Validação geral
     */
    private void validateGeneral(String input, ValidationResult result) {
        // Verifica caracteres perigosos
        for (char c : input.toCharArray()) {
            if (DANGEROUS_CHARS.contains(c)) {
                result.addError("Caractere perigoso detectado: " + c);
                break;
            }
        }
    }

    /**
     * Valida tamanho do campo
     */
    private boolean validateLength(String input, String fieldName) {
        int maxLength = MAX_LENGTHS.getOrDefault(fieldName.toLowerCase(), MAX_LENGTHS.get("default"));
        return input.length() <= maxLength;
    }

    /**
     * Sanitiza HTML
     */
    private String sanitizeHtml(String input) {
        return input
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("&", "&amp;");
    }

    /**
     * Sanitiza SQL
     */
    private String sanitizeSql(String input) {
        return input
            .replace("'", "''")
            .replace("\"", "\"\"")
            .replace(";", "")
            .replace("--", "");
    }

    /**
     * Sanitiza Path
     */
    private String sanitizePath(String input) {
        return input
            .replace("..", "")
            .replace("//", "/")
            .replace("\\\\", "\\");
    }

    /**
     * Sanitiza Command
     */
    private String sanitizeCommand(String input) {
        return input
            .replace(";", "")
            .replace("|", "")
            .replace("&", "")
            .replace("`", "")
            .replace("$(", "")
            .replace("${", "");
    }

    /**
     * Sanitização geral
     */
    private String sanitizeGeneral(String input) {
        StringBuilder sanitized = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (!DANGEROUS_CHARS.contains(c)) {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }

    /**
     * Mascara dados sensíveis para logs
     */
    private String maskSensitiveData(String data) {
        if (StrUtil.isBlank(data)) {
            return data;
        }
        
        int length = data.length();
        if (length <= 10) {
            return "***";
        }
        
        return data.substring(0, 3) + "***" + data.substring(length - 3);
    }

    /**
     * Verifica se string contém apenas caracteres seguros
     */
    public boolean isSafeString(String input) {
        if (StrUtil.isBlank(input)) {
            return true;
        }
        
        return input.chars()
            .noneMatch(c -> DANGEROUS_CHARS.contains((char) c));
    }

    /**
     * Obtém estatísticas de validação
     */
    public Map<String, Object> getValidationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("sqlPatterns", SQL_INJECTION_PATTERNS.size());
        stats.put("xssPatterns", XSS_PATTERNS.size());
        stats.put("pathTraversalPatterns", PATH_TRAVERSAL_PATTERNS.size());
        stats.put("commandInjectionPatterns", COMMAND_INJECTION_PATTERNS.size());
        stats.put("ldapPatterns", LDAP_INJECTION_PATTERNS.size());
        stats.put("dangerousChars", DANGEROUS_CHARS.size());
        stats.put("maxLengths", MAX_LENGTHS);
        return stats;
    }
}