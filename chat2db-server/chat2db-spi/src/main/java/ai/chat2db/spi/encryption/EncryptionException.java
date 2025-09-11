package ai.chat2db.spi.encryption;

/**
 * Exceção específica para operações de criptografia
 * 
 * @author Chat2DB Security Team
 * @version 1.0
 */
public class EncryptionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Construtor padrão
     */
    public EncryptionException() {
        super();
    }

    /**
     * Construtor com mensagem
     */
    public EncryptionException(String message) {
        super(message);
    }

    /**
     * Construtor com mensagem e causa
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Construtor com causa
     */
    public EncryptionException(Throwable cause) {
        super(cause);
    }

    /**
     * Construtor completo
     */
    public EncryptionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}