package ai.chat2db.spi.sql;

import lombok.Builder;
import lombok.Data;

/**
 * SQL Execution Options
 * Configures various aspects of SQL execution including caching,
 * optimization, timeouts, and result limits.
 * 
 * @author Chat2DB Team
 */
@Data
@Builder
public class ExecutionOptions {
    
    /**
     * Enable query result caching
     */
    @Builder.Default
    private boolean cacheEnabled = false;
    
    /**
     * Enable query optimization
     */
    @Builder.Default
    private boolean optimizationEnabled = true;
    
    /**
     * Execute in transactional mode
     */
    @Builder.Default
    private boolean transactional = false;
    
    /**
     * Query timeout in seconds (0 = no timeout)
     */
    @Builder.Default
    private int queryTimeout = 30;
    
    /**
     * Fetch size for result sets (0 = use driver default)
     */
    @Builder.Default
    private int fetchSize = 1000;
    
    /**
     * Maximum number of rows to return (0 = no limit)
     */
    @Builder.Default
    private int maxRows = 10000;
    
    /**
     * Enable detailed execution logging
     */
    @Builder.Default
    private boolean detailedLogging = false;
    
    /**
     * Enable query plan analysis
     */
    @Builder.Default
    private boolean analyzeQueryPlan = false;
    
    /**
     * Connection pool timeout in seconds
     */
    @Builder.Default
    private int connectionTimeout = 10;
    
    /**
     * Enable result streaming for large datasets
     */
    @Builder.Default
    private boolean streamResults = false;
    
    /**
     * Batch size for streaming results
     */
    @Builder.Default
    private int streamBatchSize = 1000;
    
    /**
     * Enable query validation before execution
     */
    @Builder.Default
    private boolean validateQuery = true;
    
    /**
     * Enable performance metrics collection
     */
    @Builder.Default
    private boolean collectMetrics = true;
    
    /**
     * Create default execution options
     */
    public static ExecutionOptions defaultOptions() {
        return ExecutionOptions.builder().build();
    }
    
    /**
     * Create options for fast queries (no caching, minimal overhead)
     */
    public static ExecutionOptions fastExecution() {
        return ExecutionOptions.builder()
                .cacheEnabled(false)
                .optimizationEnabled(false)
                .collectMetrics(false)
                .validateQuery(false)
                .queryTimeout(5)
                .build();
    }
    
    /**
     * Create options for large data queries
     */
    public static ExecutionOptions largeDataExecution() {
        return ExecutionOptions.builder()
                .streamResults(true)
                .streamBatchSize(5000)
                .fetchSize(5000)
                .maxRows(0) // No limit
                .queryTimeout(300) // 5 minutes
                .build();
    }
    
    /**
     * Create options for cached queries
     */
    public static ExecutionOptions cachedExecution() {
        return ExecutionOptions.builder()
                .cacheEnabled(true)
                .optimizationEnabled(true)
                .collectMetrics(true)
                .build();
    }
    
    /**
     * Create options for transactional operations
     */
    public static ExecutionOptions transactionalExecution() {
        return ExecutionOptions.builder()
                .transactional(true)
                .cacheEnabled(false)
                .validateQuery(true)
                .detailedLogging(true)
                .build();
    }
}