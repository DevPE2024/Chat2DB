package ai.chat2db.spi.sql;

import ai.chat2db.spi.model.ExecuteResult;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced Transaction Manager
 * Provides comprehensive transaction management with savepoints, nested transactions,
 * and transaction monitoring capabilities.
 * 
 * @author Chat2DB Team
 */
@Slf4j
public class TransactionManager {
    
    private static final AtomicLong transactionIdGenerator = new AtomicLong(1);
    private final Map<String, TransactionContext> activeTransactions = new ConcurrentHashMap<>();
    private final Map<String, List<TransactionEvent>> transactionHistory = new ConcurrentHashMap<>();
    
    /**
     * Transaction Context
     */
    @Data
    @Builder
    public static class TransactionContext {
        private String transactionId;
        private Connection connection;
        private TransactionOptions options;
        private LocalDateTime startTime;
        private TransactionStatus status;
        private Stack<Savepoint> savepoints;
        private List<String> executedStatements;
        private Map<String, Object> metadata;
        private String parentTransactionId;
        private int nestingLevel;
        
        public TransactionContext() {
            this.savepoints = new Stack<>();
            this.executedStatements = new ArrayList<>();
            this.metadata = new HashMap<>();
        }
    }
    
    /**
     * Transaction Options
     */
    @Data
    @Builder
    public static class TransactionOptions {
        @Builder.Default
        private TransactionIsolation isolation = TransactionIsolation.READ_COMMITTED;
        
        @Builder.Default
        private boolean autoCommit = false;
        
        @Builder.Default
        private int timeoutSeconds = 300; // 5 minutes default
        
        @Builder.Default
        private boolean readOnly = false;
        
        @Builder.Default
        private boolean enableSavepoints = true;
        
        @Builder.Default
        private int maxSavepoints = 10;
        
        @Builder.Default
        private boolean logStatements = true;
        
        @Builder.Default
        private boolean enableDeadlockDetection = true;
    }
    
    /**
     * Transaction Isolation Levels
     */
    public enum TransactionIsolation {
        READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
        READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
        REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
        SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);
        
        private final int level;
        
        TransactionIsolation(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    /**
     * Transaction Status
     */
    public enum TransactionStatus {
        ACTIVE, COMMITTED, ROLLED_BACK, TIMEOUT, ERROR
    }
    
    /**
     * Transaction Event
     */
    @Data
    @Builder
    public static class TransactionEvent {
        private LocalDateTime timestamp;
        private String eventType;
        private String description;
        private Map<String, Object> details;
    }
    
    /**
     * Start a new transaction
     */
    public String beginTransaction(Connection connection, TransactionOptions options) throws SQLException {
        String transactionId = "TXN_" + transactionIdGenerator.getAndIncrement();
        
        // Configure connection
        connection.setAutoCommit(options.isAutoCommit());
        connection.setTransactionIsolation(options.getIsolation().getLevel());
        connection.setReadOnly(options.isReadOnly());
        
        // Create transaction context
        TransactionContext context = TransactionContext.builder()
                .transactionId(transactionId)
                .connection(connection)
                .options(options)
                .startTime(LocalDateTime.now())
                .status(TransactionStatus.ACTIVE)
                .nestingLevel(0)
                .build();
        
        activeTransactions.put(transactionId, context);
        transactionHistory.put(transactionId, new ArrayList<>());
        
        logTransactionEvent(transactionId, "BEGIN", "Transaction started", 
                Map.of("isolation", options.getIsolation().name(),
                       "readOnly", options.isReadOnly(),
                       "timeout", options.getTimeoutSeconds()));
        
        log.info("Transaction {} started with isolation {}", transactionId, options.getIsolation());
        
        return transactionId;
    }
    
    /**
     * Start a nested transaction
     */
    public String beginNestedTransaction(String parentTransactionId, String savepointName) throws SQLException {
        TransactionContext parentContext = activeTransactions.get(parentTransactionId);
        if (parentContext == null) {
            throw new IllegalArgumentException("Parent transaction not found: " + parentTransactionId);
        }
        
        if (!parentContext.getOptions().isEnableSavepoints()) {
            throw new IllegalStateException("Savepoints are disabled for this transaction");
        }
        
        String nestedTransactionId = parentTransactionId + "_NESTED_" + (parentContext.getNestingLevel() + 1);
        
        // Create savepoint
        Savepoint savepoint = parentContext.getConnection().setSavepoint(savepointName);
        parentContext.getSavepoints().push(savepoint);
        
        // Create nested transaction context
        TransactionContext nestedContext = TransactionContext.builder()
                .transactionId(nestedTransactionId)
                .connection(parentContext.getConnection())
                .options(parentContext.getOptions())
                .startTime(LocalDateTime.now())
                .status(TransactionStatus.ACTIVE)
                .parentTransactionId(parentTransactionId)
                .nestingLevel(parentContext.getNestingLevel() + 1)
                .build();
        
        activeTransactions.put(nestedTransactionId, nestedContext);
        transactionHistory.put(nestedTransactionId, new ArrayList<>());
        
        logTransactionEvent(nestedTransactionId, "BEGIN_NESTED", "Nested transaction started",
                Map.of("parent", parentTransactionId, "savepoint", savepointName));
        
        return nestedTransactionId;
    }
    
    /**
     * Create a savepoint
     */
    public String createSavepoint(String transactionId, String savepointName) throws SQLException {
        TransactionContext context = getTransactionContext(transactionId);
        
        if (!context.getOptions().isEnableSavepoints()) {
            throw new IllegalStateException("Savepoints are disabled for this transaction");
        }
        
        if (context.getSavepoints().size() >= context.getOptions().getMaxSavepoints()) {
            throw new IllegalStateException("Maximum number of savepoints reached");
        }
        
        Savepoint savepoint = context.getConnection().setSavepoint(savepointName);
        context.getSavepoints().push(savepoint);
        
        logTransactionEvent(transactionId, "SAVEPOINT", "Savepoint created: " + savepointName, null);
        
        return savepointName;
    }
    
    /**
     * Rollback to savepoint
     */
    public void rollbackToSavepoint(String transactionId, String savepointName) throws SQLException {
        TransactionContext context = getTransactionContext(transactionId);
        
        // Find and rollback to the specified savepoint
        Stack<Savepoint> savepoints = context.getSavepoints();
        Savepoint targetSavepoint = null;
        
        // Remove savepoints until we find the target
        while (!savepoints.isEmpty()) {
            Savepoint savepoint = savepoints.pop();
            if (savepoint.getSavepointName().equals(savepointName)) {
                targetSavepoint = savepoint;
                break;
            }
        }
        
        if (targetSavepoint == null) {
            throw new IllegalArgumentException("Savepoint not found: " + savepointName);
        }
        
        context.getConnection().rollback(targetSavepoint);
        
        logTransactionEvent(transactionId, "ROLLBACK_SAVEPOINT", 
                "Rolled back to savepoint: " + savepointName, null);
    }
    
    /**
     * Commit transaction
     */
    public ExecuteResult commitTransaction(String transactionId) {
        try {
            TransactionContext context = getTransactionContext(transactionId);
            
            context.getConnection().commit();
            context.setStatus(TransactionStatus.COMMITTED);
            
            logTransactionEvent(transactionId, "COMMIT", "Transaction committed successfully", null);
            
            // Clean up
            activeTransactions.remove(transactionId);
            
            log.info("Transaction {} committed successfully", transactionId);
            
            return ExecuteResult.builder()
                    .success(true)
                    .message("Transaction committed successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to commit transaction {}", transactionId, e);
            return ExecuteResult.builder()
                    .success(false)
                    .message("Failed to commit transaction: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Rollback transaction
     */
    public ExecuteResult rollbackTransaction(String transactionId) {
        try {
            TransactionContext context = getTransactionContext(transactionId);
            
            context.getConnection().rollback();
            context.setStatus(TransactionStatus.ROLLED_BACK);
            
            logTransactionEvent(transactionId, "ROLLBACK", "Transaction rolled back", null);
            
            // Clean up
            activeTransactions.remove(transactionId);
            
            log.info("Transaction {} rolled back", transactionId);
            
            return ExecuteResult.builder()
                    .success(true)
                    .message("Transaction rolled back successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to rollback transaction {}", transactionId, e);
            return ExecuteResult.builder()
                    .success(false)
                    .message("Failed to rollback transaction: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * Get transaction context
     */
    private TransactionContext getTransactionContext(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        if (context == null) {
            throw new IllegalArgumentException("Transaction not found: " + transactionId);
        }
        return context;
    }
    
    /**
     * Log transaction event
     */
    private void logTransactionEvent(String transactionId, String eventType, String description, Map<String, Object> details) {
        TransactionEvent event = TransactionEvent.builder()
                .timestamp(LocalDateTime.now())
                .eventType(eventType)
                .description(description)
                .details(details != null ? details : new HashMap<>())
                .build();
        
        transactionHistory.computeIfAbsent(transactionId, k -> new ArrayList<>()).add(event);
    }
    
    /**
     * Get transaction status
     */
    public TransactionStatus getTransactionStatus(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        return context != null ? context.getStatus() : null;
    }
    
    /**
     * Get transaction history
     */
    public List<TransactionEvent> getTransactionHistory(String transactionId) {
        return transactionHistory.getOrDefault(transactionId, new ArrayList<>());
    }
    
    /**
     * Get all active transactions
     */
    public Set<String> getActiveTransactions() {
        return new HashSet<>(activeTransactions.keySet());
    }
    
    /**
     * Check if transaction is active
     */
    public boolean isTransactionActive(String transactionId) {
        TransactionContext context = activeTransactions.get(transactionId);
        return context != null && context.getStatus() == TransactionStatus.ACTIVE;
    }
    
    /**
     * Create default transaction options
     */
    public static TransactionOptions defaultOptions() {
        return TransactionOptions.builder().build();
    }
    
    /**
     * Create read-only transaction options
     */
    public static TransactionOptions readOnlyOptions() {
        return TransactionOptions.builder()
                .readOnly(true)
                .isolation(TransactionIsolation.READ_COMMITTED)
                .build();
    }
    
    /**
     * Create high-isolation transaction options
     */
    public static TransactionOptions highIsolationOptions() {
        return TransactionOptions.builder()
                .isolation(TransactionIsolation.SERIALIZABLE)
                .enableDeadlockDetection(true)
                .build();
    }
}