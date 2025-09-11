package ai.chat2db.server.web.start.config.security;

import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.model.LoginUser;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Middleware de Segurança para APIs
 * Implementa proteções contra ataques comuns e validações de segurança
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Slf4j
@Component
public class ApiSecurityMiddleware implements AsyncHandlerInterceptor {

    // Rate limiting - controle de taxa por IP
    private final Map<String, List<Long>> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIps = new ConcurrentHashMap<>();
    
    // Configurações de segurança
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final int MAX_REQUESTS_PER_HOUR = 1000;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutos
    private static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10MB
    
    // Padrões de segurança
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror).*"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i).*(<script|javascript:|vbscript:|onload=|onerror=|alert\\(|confirm\\(|prompt\\().*"
    );
    
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
        ".*(\\.\\.[\\/\\\\]|\\.\\.%2f|\\.\\.%5c).*"
    );
    
    // Headers de segurança obrigatórios
    private static final Set<String> REQUIRED_SECURITY_HEADERS = Set.of(
        "X-Content-Type-Options",
        "X-Frame-Options", 
        "X-XSS-Protection",
        "Strict-Transport-Security"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        try {
            // 1. Verificar IP bloqueado
            String clientIp = getClientIpAddress(request);
            if (isIpBlocked(clientIp)) {
                log.warn("Blocked IP attempted access: {}", clientIp);
                sendSecurityResponse(response, 429, "IP temporariamente bloqueado");
                return false;
            }
            
            // 2. Rate limiting
            if (!checkRateLimit(clientIp)) {
                log.warn("Rate limit exceeded for IP: {}", clientIp);
                blockIp(clientIp);
                sendSecurityResponse(response, 429, "Limite de requisições excedido");
                return false;
            }
            
            // 3. Validar tamanho do payload
            if (!validatePayloadSize(request)) {
                log.warn("Payload size exceeded for IP: {}", clientIp);
                sendSecurityResponse(response, 413, "Payload muito grande");
                return false;
            }
            
            // 4. Validar headers de segurança
            if (!validateSecurityHeaders(request)) {
                log.warn("Missing security headers from IP: {}", clientIp);
                // Não bloquear, apenas logar
            }
            
            // 5. Detectar ataques de injeção
            if (detectInjectionAttacks(request)) {
                log.error("Injection attack detected from IP: {} - URI: {}", clientIp, request.getRequestURI());
                blockIp(clientIp);
                sendSecurityResponse(response, 400, "Requisição maliciosa detectada");
                return false;
            }
            
            // 6. Validar autenticação para endpoints protegidos
            if (requiresAuthentication(request) && !isAuthenticated(request)) {
                log.warn("Unauthenticated access attempt to protected endpoint: {}", request.getRequestURI());
                sendSecurityResponse(response, 401, "Autenticação necessária");
                return false;
            }
            
            // 7. Adicionar headers de segurança na resposta
            addSecurityHeaders(response);
            
            // 8. Log de auditoria
            logSecurityEvent(request, "ACCESS_GRANTED");
            
            return true;
            
        } catch (Exception e) {
            log.error("Erro no middleware de segurança", e);
            sendSecurityResponse(response, 500, "Erro interno de segurança");
            return false;
        }
    }
    
    /**
     * Obtém o endereço IP real do cliente
     */
    private String getClientIpAddress(HttpServletRequest request) {
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
     * Verifica se o IP está bloqueado
     */
    private boolean isIpBlocked(String ip) {
        Long blockTime = blockedIps.get(ip);
        if (blockTime == null) {
            return false;
        }
        
        if (System.currentTimeMillis() - blockTime > BLOCK_DURATION_MS) {
            blockedIps.remove(ip);
            return false;
        }
        
        return true;
    }
    
    /**
     * Controle de taxa de requisições
     */
    private boolean checkRateLimit(String ip) {
        long currentTime = System.currentTimeMillis();
        List<Long> requests = requestCounts.computeIfAbsent(ip, k -> new ArrayList<>());
        
        // Remove requisições antigas (mais de 1 hora)
        requests.removeIf(time -> currentTime - time > 60 * 60 * 1000);
        
        // Verifica limite por minuto
        long oneMinuteAgo = currentTime - 60 * 1000;
        long recentRequests = requests.stream().filter(time -> time > oneMinuteAgo).count();
        
        if (recentRequests >= MAX_REQUESTS_PER_MINUTE) {
            return false;
        }
        
        // Verifica limite por hora
        if (requests.size() >= MAX_REQUESTS_PER_HOUR) {
            return false;
        }
        
        // Adiciona a requisição atual
        requests.add(currentTime);
        return true;
    }
    
    /**
     * Bloqueia um IP temporariamente
     */
    private void blockIp(String ip) {
        blockedIps.put(ip, System.currentTimeMillis());
        log.warn("IP {} foi bloqueado temporariamente", ip);
    }
    
    /**
     * Valida o tamanho do payload
     */
    private boolean validatePayloadSize(HttpServletRequest request) {
        int contentLength = request.getContentLength();
        return contentLength <= MAX_PAYLOAD_SIZE;
    }
    
    /**
     * Valida headers de segurança
     */
    private boolean validateSecurityHeaders(HttpServletRequest request) {
        // Para requisições de APIs externas, verificar se tem headers básicos
        String userAgent = request.getHeader("User-Agent");
        return StrUtil.isNotBlank(userAgent) && !userAgent.toLowerCase().contains("bot");
    }
    
    /**
     * Detecta ataques de injeção
     */
    private boolean detectInjectionAttacks(HttpServletRequest request) {
        // Verificar URI
        String uri = request.getRequestURI();
        if (containsMaliciousPattern(uri)) {
            return true;
        }
        
        // Verificar parâmetros
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String[] paramValues = request.getParameterValues(paramName);
            
            if (paramValues != null) {
                for (String value : paramValues) {
                    if (containsMaliciousPattern(value)) {
                        return true;
                    }
                }
            }
        }
        
        // Verificar headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            
            if (containsMaliciousPattern(headerValue)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Verifica se o conteúdo contém padrões maliciosos
     */
    private boolean containsMaliciousPattern(String content) {
        if (StrUtil.isBlank(content)) {
            return false;
        }
        
        return SQL_INJECTION_PATTERN.matcher(content).matches() ||
               XSS_PATTERN.matcher(content).matches() ||
               PATH_TRAVERSAL_PATTERN.matcher(content).matches();
    }
    
    /**
     * Verifica se o endpoint requer autenticação
     */
    private boolean requiresAuthentication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        
        // Endpoints públicos
        if (uri.startsWith("/api/auth/") || 
            uri.startsWith("/api/public/") ||
            uri.equals("/api/health") ||
            uri.equals("/api/version")) {
            return false;
        }
        
        // Todos os outros endpoints da API requerem autenticação
        return uri.startsWith("/api/");
    }
    
    /**
     * Verifica se o usuário está autenticado
     */
    private boolean isAuthenticated(HttpServletRequest request) {
        try {
            LoginUser loginUser = ContextUtils.getLoginUser();
            return loginUser != null && loginUser.getId() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Adiciona headers de segurança na resposta
     */
    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("Content-Security-Policy", "default-src 'self'");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }
    
    /**
     * Envia resposta de erro de segurança
     */
    private void sendSecurityResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("errorCode", "SECURITY_VIOLATION");
        errorResponse.put("errorMessage", message);
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        response.getWriter().write(JSONUtil.toJsonStr(errorResponse));
    }
    
    /**
     * Log de eventos de segurança
     */
    private void logSecurityEvent(HttpServletRequest request, String eventType) {
        try {
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");
            String uri = request.getRequestURI();
            String method = request.getMethod();
            
            LoginUser user = null;
            try {
                user = ContextUtils.getLoginUser();
            } catch (Exception ignored) {}
            
            log.info("SECURITY_EVENT: {} | IP: {} | User: {} | Method: {} | URI: {} | UserAgent: {}",
                eventType, clientIp, user != null ? user.getId() : "anonymous", 
                method, uri, userAgent);
                
        } catch (Exception e) {
            log.error("Erro ao registrar evento de segurança", e);
        }
    }
    
    /**
     * Limpa dados antigos periodicamente
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        
        // Remove IPs desbloqueados
        blockedIps.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > BLOCK_DURATION_MS);
        
        // Remove contadores antigos
        requestCounts.entrySet().removeIf(entry -> {
            List<Long> requests = entry.getValue();
            requests.removeIf(time -> currentTime - time > 60 * 60 * 1000);
            return requests.isEmpty();
        });
    }
    
    /**
     * Obtém estatísticas de segurança
     */
    public Map<String, Object> getSecurityStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("blockedIpsCount", blockedIps.size());
        stats.put("activeIpsCount", requestCounts.size());
        stats.put("totalRequests", requestCounts.values().stream()
            .mapToInt(List::size).sum());
        return stats;
    }
}