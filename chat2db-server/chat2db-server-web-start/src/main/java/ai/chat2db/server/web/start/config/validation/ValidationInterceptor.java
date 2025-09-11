package ai.chat2db.server.web.start.config.validation;

import ai.chat2db.server.web.start.config.audit.AuditLogger;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interceptor de Validação de Entrada
 * Aplica validações automáticas em todas as requisições
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationInterceptor implements HandlerInterceptor {

    private final InputValidator inputValidator;
    private final AuditLogger auditLogger;

    // Endpoints que requerem validação rigorosa
    private static final Set<String> STRICT_VALIDATION_ENDPOINTS = Set.of(
        "/api/rdb/ddl/export",
        "/api/rdb/dml/execute",
        "/api/connection/test",
        "/api/operation/execute"
    );

    // Endpoints que devem ser ignorados
    private static final Set<String> IGNORED_ENDPOINTS = Set.of(
        "/api/system/get-version-a",
        "/api/oauth/login_success",
        "/api/config/system_config",
        "/static",
        "/favicon.ico"
    );

    // Mapeamento de parâmetros para tipos de validação
    private static final Map<String, InputType> PARAMETER_TYPE_MAPPING = Map.of(
        "sql", InputType.SQL_QUERY,
        "query", InputType.SQL_QUERY,
        "ddl", InputType.SQL_QUERY,
        "dml", InputType.SQL_QUERY,
        "script", InputType.SQL_QUERY,
        "command", InputType.COMMAND,
        "path", InputType.FILE_PATH,
        "filePath", InputType.FILE_PATH,
        "url", InputType.URL,
        "email", InputType.EMAIL,
        "html", InputType.HTML_CONTENT,
        "content", InputType.HTML_CONTENT
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        
        // Ignora endpoints específicos
        if (shouldIgnoreEndpoint(requestURI)) {
            return true;
        }

        try {
            // Valida parâmetros da query string
            ValidationResult queryResult = validateQueryParameters(request);
            
            // Valida headers
            ValidationResult headerResult = validateHeaders(request);
            
            // Valida corpo da requisição se aplicável
            ValidationResult bodyResult = validateRequestBody(request);
            
            // Combina resultados
            ValidationResult finalResult = new ValidationResult();
            finalResult.merge(queryResult);
            finalResult.merge(headerResult);
            finalResult.merge(bodyResult);
            
            // Se há erros de validação
            if (!finalResult.isValid()) {
                handleValidationFailure(request, response, finalResult);
                return false;
            }
            
            // Log de validação bem-sucedida para endpoints críticos
            if (isStrictValidationEndpoint(requestURI)) {
                auditLogger.logSecurityEvent(
                    "INPUT_VALIDATION_SUCCESS",
                    "Validação de entrada bem-sucedida",
                    request.getRemoteAddr(),
                    getCurrentUser(request),
                    Map.of(
                        "endpoint", requestURI,
                        "method", request.getMethod(),
                        "userAgent", request.getHeader("User-Agent")
                    )
                );
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Erro durante validação de entrada", e);
            
            auditLogger.logError(
                "INPUT_VALIDATION_ERROR",
                "Erro durante validação de entrada: " + e.getMessage(),
                request.getRemoteAddr(),
                getCurrentUser(request),
                e
            );
            
            // Em caso de erro, permite continuar mas registra o problema
            return true;
        }
    }

    /**
     * Valida parâmetros da query string
     */
    private ValidationResult validateQueryParameters(HttpServletRequest request) {
        ValidationResult result = new ValidationResult();
        
        Map<String, String[]> parameterMap = request.getParameterMap();
        
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String paramName = entry.getKey();
            String[] paramValues = entry.getValue();
            
            InputType inputType = determineInputType(paramName);
            
            for (String paramValue : paramValues) {
                if (StrUtil.isNotBlank(paramValue)) {
                    ValidationResult paramResult = inputValidator.validateInput(paramValue, paramName, inputType);
                    result.merge(paramResult);
                }
            }
        }
        
        return result;
    }

    /**
     * Valida headers da requisição
     */
    private ValidationResult validateHeaders(HttpServletRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Headers críticos para validar
        String[] criticalHeaders = {"User-Agent", "Referer", "X-Forwarded-For", "Authorization"};
        
        for (String headerName : criticalHeaders) {
            String headerValue = request.getHeader(headerName);
            if (StrUtil.isNotBlank(headerValue)) {
                ValidationResult headerResult = inputValidator.validateInput(
                    headerValue, 
                    headerName, 
                    InputType.GENERAL
                );
                result.merge(headerResult);
            }
        }
        
        return result;
    }

    /**
     * Valida corpo da requisição
     */
    private ValidationResult validateRequestBody(HttpServletRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Só valida POST, PUT, PATCH
        String method = request.getMethod();
        if (!Arrays.asList("POST", "PUT", "PATCH").contains(method)) {
            return result;
        }
        
        try {
            String contentType = request.getContentType();
            if (contentType != null && contentType.contains("application/json")) {
                String body = getRequestBody(request);
                if (StrUtil.isNotBlank(body)) {
                    result.merge(validateJsonBody(body, request.getRequestURI()));
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao validar corpo da requisição: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * Valida corpo JSON
     */
    private ValidationResult validateJsonBody(String jsonBody, String endpoint) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Parse básico para detectar estruturas maliciosas
            if (jsonBody.length() > 100000) { // 100KB limit
                result.addError("Corpo da requisição muito grande");
                return result;
            }
            
            // Verifica padrões perigosos no JSON
            ValidationResult jsonResult = inputValidator.validateInput(jsonBody, "requestBody", InputType.GENERAL);
            result.merge(jsonResult);
            
            // Para endpoints SQL, valida campos específicos
            if (isStrictValidationEndpoint(endpoint)) {
                result.merge(validateSqlInJson(jsonBody));
            }
            
        } catch (Exception e) {
            result.addError("Formato JSON inválido");
        }
        
        return result;
    }

    /**
     * Valida SQL dentro do JSON
     */
    private ValidationResult validateSqlInJson(String jsonBody) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Procura por campos que podem conter SQL
            String[] sqlFields = {"sql", "query", "ddl", "dml", "script"};
            
            for (String field : sqlFields) {
                if (jsonBody.contains("\"" + field + "\"")) {
                    // Extração simples do valor (não é um parser completo)
                    int startIndex = jsonBody.indexOf("\"" + field + "\":");
                    if (startIndex != -1) {
                        int valueStart = jsonBody.indexOf('"', startIndex + field.length() + 3);
                        if (valueStart != -1) {
                            int valueEnd = jsonBody.indexOf('"', valueStart + 1);
                            if (valueEnd != -1) {
                                String sqlValue = jsonBody.substring(valueStart + 1, valueEnd);
                                ValidationResult sqlResult = inputValidator.validateInput(
                                    sqlValue, field, InputType.SQL_QUERY
                                );
                                result.merge(sqlResult);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao validar SQL no JSON: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * Lê corpo da requisição
     */
    private String getRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    /**
     * Trata falha de validação
     */
    private void handleValidationFailure(HttpServletRequest request, HttpServletResponse response, 
                                       ValidationResult result) throws IOException {
        
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String endpoint = request.getRequestURI();
        
        // Log de auditoria
        auditLogger.logSecurityEvent(
            "INPUT_VALIDATION_FAILED",
            "Validação de entrada falhou: " + result.getAllErrors(),
            clientIp,
            getCurrentUser(request),
            Map.of(
                "endpoint", endpoint,
                "method", request.getMethod(),
                "userAgent", userAgent,
                "errors", result.getAllErrors(),
                "warnings", result.getAllWarnings()
            )
        );
        
        // Resposta de erro
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> errorResponse = Map.of(
            "success", false,
            "errorCode", "VALIDATION_FAILED",
            "errorMessage", "Dados de entrada inválidos",
            "details", result.getAllErrors()
        );
        
        response.getWriter().write(JSONUtil.toJsonStr(errorResponse));
    }

    /**
     * Determina tipo de entrada baseado no nome do parâmetro
     */
    private InputType determineInputType(String paramName) {
        String lowerParamName = paramName.toLowerCase();
        
        return PARAMETER_TYPE_MAPPING.entrySet().stream()
            .filter(entry -> lowerParamName.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(InputType.GENERAL);
    }

    /**
     * Verifica se endpoint deve ser ignorado
     */
    private boolean shouldIgnoreEndpoint(String requestURI) {
        return IGNORED_ENDPOINTS.stream()
            .anyMatch(requestURI::startsWith);
    }

    /**
     * Verifica se endpoint requer validação rigorosa
     */
    private boolean isStrictValidationEndpoint(String requestURI) {
        return STRICT_VALIDATION_ENDPOINTS.stream()
            .anyMatch(requestURI::startsWith);
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
     * Obtém usuário atual (implementação básica)
     */
    private String getCurrentUser(HttpServletRequest request) {
        // Implementação básica - pode ser melhorada com sistema de autenticação
        String authHeader = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(authHeader)) {
            return "authenticated_user";
        }
        return "anonymous";
    }
}