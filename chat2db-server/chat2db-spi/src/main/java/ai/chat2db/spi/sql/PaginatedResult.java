package ai.chat2db.spi.sql;

import ai.chat2db.spi.model.ExecuteResult;
import lombok.Builder;
import lombok.Data;

/**
 * Paginated Query Result
 * Contains the result of a paginated SQL query along with pagination metadata.
 * 
 * @author Chat2DB Team
 */
@Data
@Builder
public class PaginatedResult {
    
    /**
     * The actual query execution result
     */
    private ExecuteResult executeResult;
    
    /**
     * Total number of records across all pages
     */
    private long totalCount;
    
    /**
     * Current page number (1-based)
     */
    private int pageNumber;
    
    /**
     * Number of records per page
     */
    private int pageSize;
    
    /**
     * Total number of pages
     */
    private int totalPages;
    
    /**
     * Number of records in current page
     */
    private int currentPageSize;
    
    /**
     * Whether there is a next page
     */
    private boolean hasNext;
    
    /**
     * Whether there is a previous page
     */
    private boolean hasPrevious;
    
    /**
     * First record number in current page (1-based)
     */
    private long firstRecord;
    
    /**
     * Last record number in current page (1-based)
     */
    private long lastRecord;
    
    /**
     * Cursor value for cursor-based pagination
     */
    private String nextCursor;
    
    /**
     * Previous cursor value for cursor-based pagination
     */
    private String previousCursor;
    
    /**
     * Query execution time in milliseconds
     */
    private long executionTime;
    
    /**
     * Count query execution time in milliseconds
     */
    private long countExecutionTime;
    
    /**
     * Calculate derived fields after building
     */
    public static class PaginatedResultBuilder {
        public PaginatedResult build() {
            PaginatedResult result = new PaginatedResult(
                executeResult, totalCount, pageNumber, pageSize, totalPages,
                currentPageSize, hasNext, hasPrevious, firstRecord, lastRecord,
                nextCursor, previousCursor, executionTime, countExecutionTime
            );
            
            // Calculate derived fields
            result.calculateDerivedFields();
            
            return result;
        }
    }
    
    /**
     * Calculate derived pagination fields
     */
    private void calculateDerivedFields() {
        // Calculate total pages
        if (totalPages == 0 && totalCount > 0) {
            this.totalPages = (int) Math.ceil((double) totalCount / pageSize);
        }
        
        // Calculate current page size
        if (executeResult != null && executeResult.getDataList() != null) {
            this.currentPageSize = executeResult.getDataList().size();
        }
        
        // Calculate navigation flags
        this.hasPrevious = pageNumber > 1;
        this.hasNext = pageNumber < totalPages;
        
        // Calculate record range
        this.firstRecord = (long) (pageNumber - 1) * pageSize + 1;
        this.lastRecord = Math.min(firstRecord + currentPageSize - 1, totalCount);
        
        // Adjust for empty results
        if (totalCount == 0) {
            this.firstRecord = 0;
            this.lastRecord = 0;
        }
    }
    
    /**
     * Get pagination summary as string
     */
    public String getPaginationSummary() {
        if (totalCount == 0) {
            return "No records found";
        }
        
        return String.format("Showing %d-%d of %d records (Page %d of %d)",
                firstRecord, lastRecord, totalCount, pageNumber, totalPages);
    }
    
    /**
     * Check if this is the first page
     */
    public boolean isFirstPage() {
        return pageNumber == 1;
    }
    
    /**
     * Check if this is the last page
     */
    public boolean isLastPage() {
        return pageNumber == totalPages;
    }
    
    /**
     * Get next page number (null if no next page)
     */
    public Integer getNextPageNumber() {
        return hasNext ? pageNumber + 1 : null;
    }
    
    /**
     * Get previous page number (null if no previous page)
     */
    public Integer getPreviousPageNumber() {
        return hasPrevious ? pageNumber - 1 : null;
    }
    
    /**
     * Create empty paginated result
     */
    public static PaginatedResult empty(int pageNumber, int pageSize) {
        return PaginatedResult.builder()
                .executeResult(ExecuteResult.builder()
                        .success(true)
                        .message("No records found")
                        .build())
                .totalCount(0)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(0)
                .build();
    }
    
    /**
     * Create error paginated result
     */
    public static PaginatedResult error(String errorMessage, int pageNumber, int pageSize) {
        return PaginatedResult.builder()
                .executeResult(ExecuteResult.builder()
                        .success(false)
                        .message(errorMessage)
                        .build())
                .totalCount(0)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(0)
                .build();
    }
}