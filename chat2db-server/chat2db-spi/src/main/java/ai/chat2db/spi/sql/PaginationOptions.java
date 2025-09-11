package ai.chat2db.spi.sql;

import lombok.Builder;
import lombok.Data;

/**
 * Pagination Options for SQL Queries
 * Configures pagination parameters for large result sets.
 * 
 * @author Chat2DB Team
 */
@Data
@Builder
public class PaginationOptions {
    
    /**
     * Page number (1-based)
     */
    @Builder.Default
    private int pageNumber = 1;
    
    /**
     * Number of records per page
     */
    @Builder.Default
    private int pageSize = 100;
    
    /**
     * Sort column name
     */
    private String sortColumn;
    
    /**
     * Sort direction (ASC/DESC)
     */
    @Builder.Default
    private SortDirection sortDirection = SortDirection.ASC;
    
    /**
     * Enable count query optimization
     */
    @Builder.Default
    private boolean optimizeCount = true;
    
    /**
     * Use cursor-based pagination instead of offset
     */
    @Builder.Default
    private boolean useCursor = false;
    
    /**
     * Cursor value for cursor-based pagination
     */
    private String cursorValue;
    
    /**
     * Maximum allowed page size
     */
    @Builder.Default
    private int maxPageSize = 10000;
    
    /**
     * Sort Direction Enum
     */
    public enum SortDirection {
        ASC, DESC
    }
    
    /**
     * Validate pagination parameters
     */
    public void validate() {
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Page number must be greater than 0");
        }
        
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be greater than 0");
        }
        
        if (pageSize > maxPageSize) {
            throw new IllegalArgumentException("Page size cannot exceed " + maxPageSize);
        }
    }
    
    /**
     * Calculate offset for OFFSET-LIMIT pagination
     */
    public int getOffset() {
        return (pageNumber - 1) * pageSize;
    }
    
    /**
     * Create default pagination options
     */
    public static PaginationOptions defaultPagination() {
        return PaginationOptions.builder().build();
    }
    
    /**
     * Create pagination for small datasets
     */
    public static PaginationOptions smallDataset() {
        return PaginationOptions.builder()
                .pageSize(50)
                .build();
    }
    
    /**
     * Create pagination for large datasets
     */
    public static PaginationOptions largeDataset() {
        return PaginationOptions.builder()
                .pageSize(1000)
                .useCursor(true)
                .optimizeCount(false)
                .build();
    }
    
    /**
     * Create pagination with sorting
     */
    public static PaginationOptions withSorting(String column, SortDirection direction) {
        return PaginationOptions.builder()
                .sortColumn(column)
                .sortDirection(direction)
                .build();
    }
    
    /**
     * Create cursor-based pagination
     */
    public static PaginationOptions cursorBased(String cursorValue, int pageSize) {
        return PaginationOptions.builder()
                .useCursor(true)
                .cursorValue(cursorValue)
                .pageSize(pageSize)
                .build();
    }
}