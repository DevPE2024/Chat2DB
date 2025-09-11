package ai.chat2db.spi.encryption;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Gerenciador de Criptografia de Dados Sensíveis
 * Implementa criptografia AES-GCM para proteção de dados confidenciais
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
@Slf4j
@Component
public class DataEncryptionManager {

    // Configurações de criptografia
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 12; // 96 bits para GCM
    private static final int TAG_LENGTH = 16; // 128 bits para GCM
    
    // Prefixos para identificar dados criptografados
    private static final String ENCRYPTED_PREFIX = "ENC:";
    private static final String HASHED_PREFIX = "HASH:";
    
    // Cache de chaves por contexto
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();
    
    // Padrões para identificar dados sensíveis
    private static final Set<Pattern> SENSITIVE_PATTERNS = Set.of(
        Pattern.compile("(?i).*(password|senha|secret|token|key|api_key).*"),
        Pattern.compile("(?i).*(credit_card|cartao|cpf|cnpj|ssn).*"),
        Pattern.compile("(?i).*(email|telefone|phone|address|endereco).*")
    );
    
    // Tipos de dados que devem ser sempre criptografados
    private static final Set<String> ALWAYS_ENCRYPT_TYPES = Set.of(
        "password", "token", "secret", "api_key", "private_key",
        "credit_card", "ssn", "cpf", "cnpj"
    );

    /**
     * Criptografa dados sensíveis
     */
    public String encryptSensitiveData(String data, String context) {
        if (StrUtil.isBlank(data)) {
            return data;
        }
        
        // Verifica se já está criptografado
        if (isEncrypted(data)) {
            return data;
        }
        
        try {
            SecretKey key = getOrCreateKey(context);
            
            // Gera IV aleatório
            byte[] iv = generateIV();
            
            // Configura GCM
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            
            // Criptografa
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION);
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // Combina IV + dados criptografados
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
            
            // Codifica em Base64 com prefixo
            return ENCRYPTED_PREFIX + Base64.encode(combined);
            
        } catch (Exception e) {
            log.error("Erro ao criptografar dados sensíveis", e);
            throw new EncryptionException("Falha na criptografia", e);
        }
    }
    
    /**
     * Descriptografa dados sensíveis
     */
    public String decryptSensitiveData(String encryptedData, String context) {
        if (StrUtil.isBlank(encryptedData)) {
            return encryptedData;
        }
        
        // Verifica se está criptografado
        if (!isEncrypted(encryptedData)) {
            return encryptedData;
        }
        
        try {
            // Remove prefixo e decodifica
            String base64Data = encryptedData.substring(ENCRYPTED_PREFIX.length());
            byte[] combined = Base64.decode(base64Data);
            
            // Separa IV e dados
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            
            // Obtém chave
            SecretKey key = getOrCreateKey(context);
            
            // Configura GCM
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            
            // Descriptografa
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, gcmSpec);
            
            byte[] decryptedData = cipher.doFinal(encrypted);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Erro ao descriptografar dados sensíveis", e);
            throw new EncryptionException("Falha na descriptografia", e);
        }
    }
    
    /**
     * Gera hash seguro para dados
     */
    public String hashData(String data, String salt) {
        if (StrUtil.isBlank(data)) {
            return data;
        }
        
        try {
            // Usa PBKDF2 com SHA-256
            String saltedData = data + (salt != null ? salt : "");
            String hash = SecureUtil.sha256(saltedData);
            
            return HASHED_PREFIX + hash;
            
        } catch (Exception e) {
            log.error("Erro ao gerar hash", e);
            throw new EncryptionException("Falha na geração de hash", e);
        }
    }
    
    /**
     * Verifica se um hash corresponde aos dados
     */
    public boolean verifyHash(String data, String hash, String salt) {
        if (StrUtil.isBlank(data) || StrUtil.isBlank(hash)) {
            return false;
        }
        
        String computedHash = hashData(data, salt);
        return computedHash.equals(hash);
    }
    
    /**
     * Criptografa mapa de propriedades
     */
    public Map<String, String> encryptProperties(Map<String, String> properties, String context) {
        if (properties == null || properties.isEmpty()) {
            return properties;
        }
        
        Map<String, String> encrypted = new HashMap<>();
        
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (shouldEncrypt(key, value)) {
                encrypted.put(key, encryptSensitiveData(value, context));
            } else {
                encrypted.put(key, value);
            }
        }
        
        return encrypted;
    }
    
    /**
     * Descriptografa mapa de propriedades
     */
    public Map<String, String> decryptProperties(Map<String, String> properties, String context) {
        if (properties == null || properties.isEmpty()) {
            return properties;
        }
        
        Map<String, String> decrypted = new HashMap<>();
        
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (isEncrypted(value)) {
                decrypted.put(key, decryptSensitiveData(value, context));
            } else {
                decrypted.put(key, value);
            }
        }
        
        return decrypted;
    }
    
    /**
     * Mascara dados sensíveis para logs
     */
    public String maskSensitiveData(String data) {
        if (StrUtil.isBlank(data)) {
            return data;
        }
        
        // Se já está criptografado, mostra apenas o prefixo
        if (isEncrypted(data)) {
            return ENCRYPTED_PREFIX + "***";
        }
        
        // Mascara baseado no tamanho
        int length = data.length();
        if (length <= 4) {
            return "***";
        } else if (length <= 8) {
            return data.substring(0, 2) + "***";
        } else {
            return data.substring(0, 3) + "***" + data.substring(length - 2);
        }
    }
    
    /**
     * Verifica se dados estão criptografados
     */
    public boolean isEncrypted(String data) {
        return data != null && data.startsWith(ENCRYPTED_PREFIX);
    }
    
    /**
     * Verifica se dados são um hash
     */
    public boolean isHashed(String data) {
        return data != null && data.startsWith(HASHED_PREFIX);
    }
    
    /**
     * Determina se um campo deve ser criptografado
     */
    private boolean shouldEncrypt(String fieldName, String value) {
        if (StrUtil.isBlank(fieldName) || StrUtil.isBlank(value)) {
            return false;
        }
        
        // Verifica tipos que sempre devem ser criptografados
        String lowerFieldName = fieldName.toLowerCase();
        if (ALWAYS_ENCRYPT_TYPES.stream().anyMatch(lowerFieldName::contains)) {
            return true;
        }
        
        // Verifica padrões sensíveis
        return SENSITIVE_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(fieldName).matches());
    }
    
    /**
     * Obtém ou cria chave para contexto
     */
    private SecretKey getOrCreateKey(String context) {
        return keyCache.computeIfAbsent(context, this::generateKey);
    }
    
    /**
     * Gera nova chave AES
     */
    private SecretKey generateKey(String context) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_LENGTH);
            
            SecretKey key = keyGen.generateKey();
            
            log.info("Nova chave de criptografia gerada para contexto: {}", context);
            
            return key;
            
        } catch (NoSuchAlgorithmException e) {
            throw new EncryptionException("Algoritmo de criptografia não disponível", e);
        }
    }
    
    /**
     * Gera IV aleatório
     */
    private byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    /**
     * Carrega chave de configuração (para persistência)
     */
    public void loadKey(String context, String base64Key) {
        try {
            byte[] keyBytes = Base64.decode(base64Key);
            SecretKey key = new SecretKeySpec(keyBytes, ALGORITHM);
            keyCache.put(context, key);
            
            log.info("Chave carregada para contexto: {}", context);
            
        } catch (Exception e) {
            log.error("Erro ao carregar chave para contexto: {}", context, e);
            throw new EncryptionException("Falha ao carregar chave", e);
        }
    }
    
    /**
     * Exporta chave para persistência
     */
    public String exportKey(String context) {
        SecretKey key = keyCache.get(context);
        if (key == null) {
            throw new EncryptionException("Chave não encontrada para contexto: " + context);
        }
        
        return Base64.encode(key.getEncoded());
    }
    
    /**
     * Remove chave do cache
     */
    public void removeKey(String context) {
        keyCache.remove(context);
        log.info("Chave removida para contexto: {}", context);
    }
    
    /**
     * Limpa todas as chaves
     */
    public void clearAllKeys() {
        keyCache.clear();
        log.warn("Todas as chaves de criptografia foram removidas");
    }
    
    /**
     * Obtém estatísticas de criptografia
     */
    public Map<String, Object> getEncryptionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeContexts", keyCache.size());
        stats.put("algorithm", ALGORITHM);
        stats.put("keyLength", KEY_LENGTH);
        stats.put("transformation", TRANSFORMATION);
        return stats;
    }
    
    /**
     * Valida integridade dos dados criptografados
     */
    public boolean validateIntegrity(String encryptedData, String context) {
        try {
            String decrypted = decryptSensitiveData(encryptedData, context);
            return decrypted != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Rotaciona chave de contexto
     */
    public void rotateKey(String context) {
        removeKey(context);
        getOrCreateKey(context);
        log.info("Chave rotacionada para contexto: {}", context);
    }
}