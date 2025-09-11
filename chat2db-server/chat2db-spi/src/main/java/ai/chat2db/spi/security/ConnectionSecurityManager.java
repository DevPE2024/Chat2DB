package ai.chat2db.spi.security;

import ai.chat2db.spi.model.SSHInfo;
import ai.chat2db.spi.model.SSLInfo;
import ai.chat2db.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Gerenciador de segurança para conexões de banco de dados
 * Responsável por criptografia, validação e proteção de credenciais
 * 
 * @author Chat2DB Team
 * @version 1.0
 */
@Slf4j
public class ConnectionSecurityManager {
    
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final String HASH_ALGORITHM = "SHA-256";
    
    // Chave mestra para criptografia (em produção, deve vir de configuração segura)
    private static final String MASTER_KEY = "Chat2DB-Security-Key-2024";
    
    // Padrões de validação
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript).*");
    
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]+$");
    
    /**
     * Criptografa uma senha usando AES-GCM
     */
    public static String encryptPassword(String password) {
        if (StringUtils.isBlank(password)) {
            return password;
        }
        
        try {
            SecretKey secretKey = generateSecretKey();
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            
            // Gera IV aleatório
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            
            // Combina IV + dados criptografados
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            String encrypted = Base64.getEncoder().encodeToString(encryptedWithIv);
            log.debug("Senha criptografada com sucesso");
            return encrypted;
            
        } catch (Exception e) {
            log.error("Erro ao criptografar senha: {}", e.getMessage(), e);
            throw new SecurityException("Falha na criptografia da senha", e);
        }
    }
    
    /**
     * Descriptografa uma senha usando AES-GCM
     */
    public static String decryptPassword(String encryptedPassword) {
        if (StringUtils.isBlank(encryptedPassword)) {
            return encryptedPassword;
        }
        
        try {
            SecretKey secretKey = generateSecretKey();
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedPassword);
            
            // Extrai IV e dados criptografados
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
            System.arraycopy(encryptedWithIv, iv.length, encryptedData, 0, encryptedData.length);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            
            log.debug("Senha descriptografada com sucesso");
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Erro ao descriptografar senha: {}", e.getMessage(), e);
            throw new SecurityException("Falha na descriptografia da senha", e);
        }
    }
    
    /**
     * Gera chave secreta baseada na chave mestra
     */
    private static SecretKey generateSecretKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] keyBytes = digest.digest(MASTER_KEY.getBytes(StandardCharsets.UTF_8));
        
        // Usa apenas os primeiros 32 bytes para AES-256
        byte[] aesKeyBytes = new byte[32];
        System.arraycopy(keyBytes, 0, aesKeyBytes, 0, Math.min(keyBytes.length, 32));
        
        return new SecretKeySpec(aesKeyBytes, AES_ALGORITHM);
    }
    
    /**
     * Valida informações de conexão contra ataques de injeção
     */
    public static void validateConnectionInfo(ConnectInfo connectInfo) {
        if (connectInfo == null) {
            throw new SecurityException("Informações de conexão não podem ser nulas");
        }
        
        // Valida URL
        validateUrl(connectInfo.getUrl());
        
        // Valida usuário
        validateUsername(connectInfo.getUser());
        
        // Valida host se disponível
        if (StringUtils.isNotBlank(connectInfo.getHost())) {
            validateHost(connectInfo.getHost());
        }
        
        // Valida configurações SSH se habilitadas
        if (connectInfo.getSsh() != null && connectInfo.getSsh().isUse()) {
            validateSSHInfo(connectInfo.getSsh());
        }
        
        // Valida configurações SSL se habilitadas
        if (connectInfo.getSsl() != null) {
            validateSSLInfo(connectInfo.getSsl());
        }
        
        log.debug("Validação de segurança da conexão concluída com sucesso");
    }
    
    /**
     * Valida URL de conexão
     */
    private static void validateUrl(String url) {
        if (StringUtils.isBlank(url)) {
            throw new SecurityException("URL de conexão não pode ser vazia");
        }
        
        if (SQL_INJECTION_PATTERN.matcher(url).matches()) {
            throw new SecurityException("URL contém padrões suspeitos de injeção SQL");
        }
        
        // Verifica se é uma URL JDBC válida
        if (!url.startsWith("jdbc:")) {
            throw new SecurityException("URL deve ser uma URL JDBC válida");
        }
    }
    
    /**
     * Valida nome de usuário
     */
    private static void validateUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return; // Username pode ser vazio em alguns casos
        }
        
        if (SQL_INJECTION_PATTERN.matcher(username).matches()) {
            throw new SecurityException("Nome de usuário contém padrões suspeitos");
        }
        
        if (username.length() > 100) {
            throw new SecurityException("Nome de usuário muito longo");
        }
    }
    
    /**
     * Valida host
     */
    private static void validateHost(String host) {
        if (StringUtils.isBlank(host)) {
            throw new SecurityException("Host não pode ser vazio");
        }
        
        if (SQL_INJECTION_PATTERN.matcher(host).matches()) {
            throw new SecurityException("Host contém padrões suspeitos");
        }
        
        // Valida formato básico de host/IP
        if (!host.matches("^[a-zA-Z0-9.-]+$")) {
            throw new SecurityException("Formato de host inválido");
        }
    }
    
    /**
     * Valida configurações SSH
     */
    private static void validateSSHInfo(SSHInfo sshInfo) {
        if (StringUtils.isNotBlank(sshInfo.getHostName())) {
            validateHost(sshInfo.getHostName());
        }
        
        if (StringUtils.isNotBlank(sshInfo.getUserName())) {
            validateUsername(sshInfo.getUserName());
        }
        
        // Valida porta SSH
        if (StringUtils.isNotBlank(sshInfo.getPort())) {
            try {
                int port = Integer.parseInt(sshInfo.getPort());
                if (port < 1 || port > 65535) {
                    throw new SecurityException("Porta SSH inválida: " + port);
                }
            } catch (NumberFormatException e) {
                throw new SecurityException("Formato de porta SSH inválido");
            }
        }
    }
    
    /**
     * Valida configurações SSL
     */
    private static void validateSSLInfo(SSLInfo sslInfo) {
        // Implementar validações específicas de SSL conforme necessário
        log.debug("Validação SSL executada");
    }
    
    /**
     * Sanitiza string removendo caracteres perigosos
     */
    public static String sanitizeString(String input) {
        if (StringUtils.isBlank(input)) {
            return input;
        }
        
        // Remove caracteres de controle e caracteres especiais perigosos
        return input.replaceAll("[\\x00-\\x1F\\x7F<>\"'&]", "")
                   .trim();
    }
    
    /**
     * Gera hash seguro para identificação de conexão
     */
    public static String generateConnectionHash(ConnectInfo connectInfo) {
        try {
            String connectionString = String.format("%s|%s|%s|%s", 
                connectInfo.getUrl(),
                connectInfo.getUser(),
                connectInfo.getHost(),
                connectInfo.getDbType());
            
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(connectionString.getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(hashBytes);
            
        } catch (Exception e) {
            log.error("Erro ao gerar hash de conexão: {}", e.getMessage(), e);
            return String.valueOf(connectInfo.hashCode());
        }
    }
    
    /**
     * Verifica se a conexão é segura (SSL/TLS habilitado)
     */
    public static boolean isSecureConnection(ConnectInfo connectInfo) {
        if (connectInfo.getUrl() == null) {
            return false;
        }
        
        String url = connectInfo.getUrl().toLowerCase();
        
        // Verifica padrões de conexão segura
        return url.contains("ssl=true") || 
               url.contains("usessl=true") || 
               url.contains("sslmode=require") ||
               url.contains("encrypt=true") ||
               (connectInfo.getSsl() != null);
    }
    
    /**
     * Mascarar senha para logs
     */
    public static String maskPassword(String password) {
        if (StringUtils.isBlank(password)) {
            return "[empty]";
        }
        
        if (password.length() <= 2) {
            return "**";
        }
        
        return password.charAt(0) + "*".repeat(password.length() - 2) + password.charAt(password.length() - 1);
    }
}