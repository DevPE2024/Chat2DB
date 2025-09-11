package ai.chat2db.spi.discovery;

import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.Schema;
import ai.chat2db.spi.model.Table;
import ai.chat2db.spi.model.TableColumn;
import ai.chat2db.spi.sql.ConnectInfo;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Gerenciador de descoberta automática de esquemas de banco de dados
 * Implementa cache inteligente e descoberta assíncrona para melhor performance
 */
@Slf4j
public class DatabaseSchemaDiscovery {
    
    private static final Map<String, SchemaCache> SCHEMA_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1); // 1 hora
    private static final int MAX_TABLES_PER_SCHEMA = 1000;
    private static final int MAX_COLUMNS_PER_TABLE = 500;
    
    /**
     * Descobre todos os bancos de dados disponíveis
     */
    public static List<Database> discoverDatabases(ConnectInfo connectInfo) {
        List<Database> databases = new ArrayList<>();
        
        try (Connection connection = getConnection(connectInfo)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Diferentes SGBDs têm diferentes formas de listar bancos
            switch (connectInfo.getDbType().toLowerCase()) {
                case "mysql":
                    databases = discoverMySQLDatabases(metaData);
                    break;
                case "postgresql":
                    databases = discoverPostgreSQLDatabases(metaData);
                    break;
                case "oracle":
                    databases = discoverOracleDatabases(metaData);
                    break;
                case "sqlserver":
                    databases = discoverSQLServerDatabases(metaData);
                    break;
                default:
                    databases = discoverGenericDatabases(metaData);
            }
            
            log.info("Descobertos {} bancos de dados para tipo: {}", databases.size(), connectInfo.getDbType());
            
        } catch (Exception e) {
            log.error("Erro ao descobrir bancos de dados: {}", e.getMessage(), e);
        }
        
        return databases;
    }
    
    /**
     * Descobre esquemas de um banco específico com cache
     */
    public static List<Schema> discoverSchemas(ConnectInfo connectInfo, String databaseName) {
        String cacheKey = generateCacheKey(connectInfo, databaseName);
        
        // Verifica cache primeiro
        SchemaCache cached = SCHEMA_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Retornando esquemas do cache para: {}", databaseName);
            return cached.getSchemas();
        }
        
        List<Schema> schemas = new ArrayList<>();
        
        try (Connection connection = getConnection(connectInfo)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    Schema schema = new Schema();
                    schema.setSchemaName(rs.getString("TABLE_SCHEM"));
                    schema.setDatabaseName(databaseName);
                    
                    // Adiciona informações extras se disponíveis
                    try {
                        schema.setCatalogName(rs.getString("TABLE_CATALOG"));
                    } catch (SQLException ignored) {
                        // Nem todos os SGBDs suportam catalog
                    }
                    
                    schemas.add(schema);
                }
            }
            
            // Se não encontrou esquemas, tenta usar catalogs
            if (schemas.isEmpty()) {
                schemas = discoverSchemasFromCatalogs(metaData, databaseName);
            }
            
            // Atualiza cache
            SCHEMA_CACHE.put(cacheKey, new SchemaCache(schemas));
            
            log.info("Descobertos {} esquemas para banco: {}", schemas.size(), databaseName);
            
        } catch (Exception e) {
            log.error("Erro ao descobrir esquemas para banco {}: {}", databaseName, e.getMessage(), e);
        }
        
        return schemas;
    }
    
    /**
     * Descobre tabelas de um esquema específico
     */
    public static List<Table> discoverTables(ConnectInfo connectInfo, String databaseName, String schemaName) {
        List<Table> tables = new ArrayList<>();
        
        try (Connection connection = getConnection(connectInfo)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            String[] types = {"TABLE", "VIEW", "MATERIALIZED VIEW"};
            
            try (ResultSet rs = metaData.getTables(databaseName, schemaName, "%", types)) {
                int tableCount = 0;
                
                while (rs.next() && tableCount < MAX_TABLES_PER_SCHEMA) {
                    Table table = new Table();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setTableType(rs.getString("TABLE_TYPE"));
                    table.setSchemaName(schemaName);
                    table.setDatabaseName(databaseName);
                    
                    try {
                        table.setRemarks(rs.getString("REMARKS"));
                    } catch (SQLException ignored) {
                        // Nem todos os SGBDs suportam remarks
                    }
                    
                    tables.add(table);
                    tableCount++;
                }
                
                if (tableCount >= MAX_TABLES_PER_SCHEMA) {
                    log.warn("Limite de {} tabelas atingido para esquema: {}.{}", 
                            MAX_TABLES_PER_SCHEMA, databaseName, schemaName);
                }
            }
            
            log.info("Descobertas {} tabelas para esquema: {}.{}", tables.size(), databaseName, schemaName);
            
        } catch (Exception e) {
            log.error("Erro ao descobrir tabelas para esquema {}.{}: {}", 
                    databaseName, schemaName, e.getMessage(), e);
        }
        
        return tables;
    }
    
    /**
     * Descobre colunas de uma tabela específica
     */
    public static List<TableColumn> discoverColumns(ConnectInfo connectInfo, String databaseName, 
                                                   String schemaName, String tableName) {
        List<TableColumn> columns = new ArrayList<>();
        
        try (Connection connection = getConnection(connectInfo)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            try (ResultSet rs = metaData.getColumns(databaseName, schemaName, tableName, "%")) {
                int columnCount = 0;
                
                while (rs.next() && columnCount < MAX_COLUMNS_PER_TABLE) {
                    TableColumn column = new TableColumn();
                    column.setColumnName(rs.getString("COLUMN_NAME"));
                    column.setColumnType(rs.getString("TYPE_NAME"));
                    column.setColumnSize(rs.getInt("COLUMN_SIZE"));
                    column.setDecimalDigits(rs.getInt("DECIMAL_DIGITS"));
                    column.setNullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.setAutoIncrement("YES".equals(rs.getString("IS_AUTOINCREMENT")));
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    column.setRemarks(rs.getString("REMARKS"));
                    column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                    
                    columns.add(column);
                    columnCount++;
                }
                
                if (columnCount >= MAX_COLUMNS_PER_TABLE) {
                    log.warn("Limite de {} colunas atingido para tabela: {}.{}.{}", 
                            MAX_COLUMNS_PER_TABLE, databaseName, schemaName, tableName);
                }
            }
            
            // Descobre chaves primárias
            discoverPrimaryKeys(metaData, databaseName, schemaName, tableName, columns);
            
            log.info("Descobertas {} colunas para tabela: {}.{}.{}", 
                    columns.size(), databaseName, schemaName, tableName);
            
        } catch (Exception e) {
            log.error("Erro ao descobrir colunas para tabela {}.{}.{}: {}", 
                    databaseName, schemaName, tableName, e.getMessage(), e);
        }
        
        return columns;
    }
    
    /**
     * Descoberta assíncrona completa de um banco
     */
    public static CompletableFuture<Map<String, Object>> discoverDatabaseAsync(ConnectInfo connectInfo) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            
            try {
                // Descobre bancos
                List<Database> databases = discoverDatabases(connectInfo);
                result.put("databases", databases);
                
                // Para cada banco, descobre esquemas (limitado aos primeiros 5)
                Map<String, List<Schema>> schemasByDatabase = new HashMap<>();
                databases.stream().limit(5).forEach(db -> {
                    List<Schema> schemas = discoverSchemas(connectInfo, db.getName());
                    schemasByDatabase.put(db.getName(), schemas);
                });
                result.put("schemas", schemasByDatabase);
                
                log.info("Descoberta assíncrona concluída para conexão: {}", connectInfo.getAlias());
                
            } catch (Exception e) {
                log.error("Erro na descoberta assíncrona: {}", e.getMessage(), e);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Limpa cache expirado
     */
    public static void cleanExpiredCache() {
        SCHEMA_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
        log.debug("Cache de esquemas limpo. Entradas restantes: {}", SCHEMA_CACHE.size());
    }
    
    // Métodos privados auxiliares
    
    private static Connection getConnection(ConnectInfo connectInfo) throws SQLException {
        // Implementação simplificada - na prática usaria o pool de conexões
        return DriverManager.getConnection(
            connectInfo.getUrl(), 
            connectInfo.getUser(), 
            connectInfo.getDecryptedPassword()
        );
    }
    
    private static List<Database> discoverMySQLDatabases(DatabaseMetaData metaData) throws SQLException {
        List<Database> databases = new ArrayList<>();
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                Database db = new Database();
                db.setName(rs.getString("TABLE_CAT"));
                databases.add(db);
            }
        }
        return databases;
    }
    
    private static List<Database> discoverPostgreSQLDatabases(DatabaseMetaData metaData) throws SQLException {
        List<Database> databases = new ArrayList<>();
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                Database db = new Database();
                db.setName(rs.getString("TABLE_CAT"));
                databases.add(db);
            }
        }
        return databases;
    }
    
    private static List<Database> discoverOracleDatabases(DatabaseMetaData metaData) throws SQLException {
        // Oracle usa schemas como "databases"
        List<Database> databases = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas()) {
            while (rs.next()) {
                Database db = new Database();
                db.setName(rs.getString("TABLE_SCHEM"));
                databases.add(db);
            }
        }
        return databases;
    }
    
    private static List<Database> discoverSQLServerDatabases(DatabaseMetaData metaData) throws SQLException {
        List<Database> databases = new ArrayList<>();
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                Database db = new Database();
                db.setName(rs.getString("TABLE_CAT"));
                databases.add(db);
            }
        }
        return databases;
    }
    
    private static List<Database> discoverGenericDatabases(DatabaseMetaData metaData) throws SQLException {
        List<Database> databases = new ArrayList<>();
        
        // Tenta catalogs primeiro
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                Database db = new Database();
                db.setName(rs.getString("TABLE_CAT"));
                databases.add(db);
            }
        }
        
        // Se não encontrou, tenta schemas
        if (databases.isEmpty()) {
            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    Database db = new Database();
                    db.setName(rs.getString("TABLE_SCHEM"));
                    databases.add(db);
                }
            }
        }
        
        return databases;
    }
    
    private static List<Schema> discoverSchemasFromCatalogs(DatabaseMetaData metaData, String databaseName) 
            throws SQLException {
        List<Schema> schemas = new ArrayList<>();
        
        try (ResultSet rs = metaData.getCatalogs()) {
            while (rs.next()) {
                Schema schema = new Schema();
                schema.setSchemaName(rs.getString("TABLE_CAT"));
                schema.setDatabaseName(databaseName);
                schemas.add(schema);
            }
        }
        
        return schemas;
    }
    
    private static void discoverPrimaryKeys(DatabaseMetaData metaData, String databaseName, 
                                          String schemaName, String tableName, 
                                          List<TableColumn> columns) {
        try (ResultSet rs = metaData.getPrimaryKeys(databaseName, schemaName, tableName)) {
            Set<String> primaryKeyColumns = new HashSet<>();
            
            while (rs.next()) {
                primaryKeyColumns.add(rs.getString("COLUMN_NAME"));
            }
            
            // Marca colunas como chave primária
            columns.forEach(column -> {
                if (primaryKeyColumns.contains(column.getColumnName())) {
                    column.setPrimaryKey(true);
                }
            });
            
        } catch (SQLException e) {
            log.warn("Erro ao descobrir chaves primárias para {}.{}.{}: {}", 
                    databaseName, schemaName, tableName, e.getMessage());
        }
    }
    
    private static String generateCacheKey(ConnectInfo connectInfo, String databaseName) {
        return String.format("%s_%s_%s_%s", 
                connectInfo.getHost(), 
                connectInfo.getPort(), 
                connectInfo.getDbType(), 
                databaseName);
    }
    
    /**
     * Classe interna para cache de esquemas
     */
    private static class SchemaCache {
        private final List<Schema> schemas;
        private final long timestamp;
        
        public SchemaCache(List<Schema> schemas) {
            this.schemas = new ArrayList<>(schemas);
            this.timestamp = System.currentTimeMillis();
        }
        
        public List<Schema> getSchemas() {
            return new ArrayList<>(schemas);
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }
}