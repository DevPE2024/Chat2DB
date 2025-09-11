package ai.chat2db.spi.sql;

import ai.chat2db.spi.model.*;
import ai.chat2db.spi.util.JdbcUtils;
import ai.chat2db.spi.util.ResultSetUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Advanced SQL Execution Engine
 * Provides enhanced SQL execution capabilities with performance optimization,
 * caching, async execution, and comprehensive metrics.
 * 
 * @author Chat2DB Team
 */
@Slf4j
public class AdvancedSQLExecutor {

    private static final AdvancedSQLExecutor INSTANCE = new AdvancedSQLExecutor();
    
    // Thread pool for async execution
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // Query result cache
    private final Cache<String, QueryResult> queryCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
    
    // Query execution metrics
    private final Map<String, QueryMetrics> metricsMap = new ConcurrentHashMap<>();
    
    // Query optimization rules
    private final List<QueryOptimizer> optimizers = new ArrayList<>();
    
    private AdvancedSQLExecutor() {
        initializeOptimizers();
    }
    
    public static AdvancedSQLExecutor getInstance() {
        return INSTANCE;
    }
    
    /**
     * Execute SQL with advanced features
     */
    public ExecuteResult executeAdvanced(String sql, Connection connection, ExecutionOptions options) {
        String queryId = generateQueryId(sql);
        long startTime = System.currentTimeMillis();
        
        try {
            // Check cache if enabled
            if (options.isCacheEnabled()) {
                QueryResult cached = queryCache.getIfPresent(sql);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Query result retrieved from cache: {}", queryId);
                    return cached.getExecuteResult();
                }
            }
            
            // Optimize query if enabled
            String optimizedSql = sql;
            if (options.isOptimizationEnabled()) {
                optimizedSql = optimizeQuery(sql, connection);
            }
            
            // Execute query
            ExecuteResult result = executeWithMetrics(optimizedSql, connection, options, queryId);
            
            // Cache result if successful and caching is enabled
            if (result.getSuccess() && options.isCacheEnabled()) {
                QueryResult queryResult = new QueryResult(result, LocalDateTime.now().plusMinutes(30));
                queryCache.put(sql, queryResult);
            }
            
            // Record metrics
            recordMetrics(queryId, sql, System.currentTimeMillis() - startTime, result.getSuccess());
            
            return result;
            
        } catch (Exception e) {
            log.error("Advanced SQL execution failed for query: {}", queryId, e);
            recordMetrics(queryId, sql, System.currentTimeMillis() - startTime, false);
            return ExecuteResult.builder()
                    .sql(sql)
                    .success(false)
                    .message(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Execute SQL asynchronously
     */
    public CompletableFuture<ExecuteResult> executeAsync(String sql, Connection connection, ExecutionOptions options) {
        return CompletableFuture.supplyAsync(() -> executeAdvanced(sql, connection, options), executorService);
    }
    
    /**
     * Execute batch SQL statements
     */
    public List<ExecuteResult> executeBatch(List<String> sqlList, Connection connection, ExecutionOptions options) {
        List<ExecuteResult> results = new ArrayList<>();
        
        try {
            boolean originalAutoCommit = connection.getAutoCommit();
            
            if (options.isTransactional()) {
                connection.setAutoCommit(false);
            }
            
            for (String sql : sqlList) {
                ExecuteResult result = executeAdvanced(sql, connection, options);
                results.add(result);
                
                // If any statement fails and we're in transactional mode, rollback
                if (!result.getSuccess() && options.isTransactional()) {
                    connection.rollback();
                    break;
                }
            }
            
            if (options.isTransactional() && results.stream().allMatch(ExecuteResult::getSuccess)) {
                connection.commit();
            }
            
            connection.setAutoCommit(originalAutoCommit);
            
        } catch (SQLException e) {
            log.error("Batch execution failed", e);
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                log.error("Rollback failed", rollbackEx);
            }
        }
        
        return results;
    }
    
    /**
     * Execute SQL with pagination support
     */
    public PaginatedResult executePaginated(String sql, Connection connection, PaginationOptions pagination) {
        String countSql = generateCountQuery(sql);
        
        try {
            // Get total count
            long totalCount = 0;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next()) {
                    totalCount = rs.getLong(1);
                }
            }
            
            // Execute paginated query
            String paginatedSql = addPaginationToQuery(sql, pagination);
            ExecuteResult result = executeAdvanced(paginatedSql, connection, ExecutionOptions.builder().build());
            
            return PaginatedResult.builder()
                    .executeResult(result)
                    .totalCount(totalCount)
                    .pageNumber(pagination.getPageNumber())
                    .pageSize(pagination.getPageSize())
                    .totalPages((int) Math.ceil((double) totalCount / pagination.getPageSize()))
                    .build();
                    
        } catch (SQLException e) {
            log.error("Paginated execution failed", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Get query execution metrics
     */
    public QueryMetrics getMetrics(String queryId) {
        return metricsMap.get(queryId);
    }
    
    /**
     * Get all metrics
     */
    public Map<String, QueryMetrics> getAllMetrics() {
        return new HashMap<>(metricsMap);
    }
    
    /**
     * Clear cache
     */
    public void clearCache() {
        queryCache.invalidateAll();
        log.info("Query cache cleared");
    }
    
    /**
     * Clear metrics
     */
    public void clearMetrics() {
        metricsMap.clear();
        log.info("Query metrics cleared");
    }
    
    // Private helper methods
    
    private ExecuteResult executeWithMetrics(String sql, Connection connection, ExecutionOptions options, String queryId) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            
            // Configure statement
            if (options.getQueryTimeout() > 0) {
                stmt.setQueryTimeout(options.getQueryTimeout());
            }
            
            if (options.getFetchSize() > 0) {
                stmt.setFetchSize(options.getFetchSize());
            }
            
            boolean isQuery = stmt.execute(sql);
            
            if (isQuery) {
                try (ResultSet rs = stmt.getResultSet()) {
                    return processResultSet(rs, sql, options);
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                return ExecuteResult.builder()
                        .sql(sql)
                        .success(true)
                        .updateCount(updateCount)
                        .message("Query executed successfully. Rows affected: " + updateCount)
                        .build();
            }
        }
    }
    
    private ExecuteResult processResultSet(ResultSet rs, String sql, ExecutionOptions options) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        // Generate headers
        List<Header> headers = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            Header header = Header.builder()
                    .name(metaData.getColumnName(i))
                    .dataType(metaData.getColumnTypeName(i))
                    .build();
            headers.add(header);
        }
        
        // Process rows
        List<List<String>> rows = new ArrayList<>();
        int rowCount = 0;
        int maxRows = options.getMaxRows() > 0 ? options.getMaxRows() : Integer.MAX_VALUE;
        
        while (rs.next() && rowCount < maxRows) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String value = rs.getString(i);
                row.add(value != null ? value : "");
            }
            rows.add(row);
            rowCount++;
        }
        
        return ExecuteResult.builder()
                .sql(sql)
                .success(true)
                .headerList(headers)
                .dataList(rows)
                .updateCount(rowCount)
                .message("Query executed successfully. Rows returned: " + rowCount)
                .build();
    }
    
    private String optimizeQuery(String sql, Connection connection) {
        String optimizedSql = sql;
        
        for (QueryOptimizer optimizer : optimizers) {
            if (optimizer.canOptimize(sql, connection)) {
                optimizedSql = optimizer.optimize(optimizedSql, connection);
            }
        }
        
        return optimizedSql;
    }
    
    private void initializeOptimizers() {
        // Add basic query optimizers
        optimizers.add(new IndexHintOptimizer());
        optimizers.add(new LimitOptimizer());
        optimizers.add(new JoinOptimizer());
    }
    
    private String generateQueryId(String sql) {
        return "query_" + Math.abs(sql.hashCode()) + "_" + System.currentTimeMillis();
    }
    
    private void recordMetrics(String queryId, String sql, long executionTime, boolean success) {
        QueryMetrics metrics = QueryMetrics.builder()
                .queryId(queryId)
                .sql(sql)
                .executionTime(executionTime)
                .success(success)
                .timestamp(LocalDateTime.now())
                .build();
        
        metricsMap.put(queryId, metrics);
    }
    
    private String generateCountQuery(String sql) {
        // Simple count query generation - can be enhanced
        return "SELECT COUNT(*) FROM (" + sql + ") AS count_query";
    }
    
    private String addPaginationToQuery(String sql, PaginationOptions pagination) {
        int offset = (pagination.getPageNumber() - 1) * pagination.getPageSize();
        return sql + " LIMIT " + pagination.getPageSize() + " OFFSET " + offset;
    }
    
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}