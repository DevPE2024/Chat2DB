package ai.chat2db.spi.manager;

import ai.chat2db.spi.Plugin;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gerenciador centralizado de drivers de banco de dados
 * Responsável por carregar, registrar e gerenciar todos os drivers suportados
 * 
 * @author Chat2DB Team
 * @version 1.0
 */
@Slf4j
public class DatabaseDriverManager {
    
    private static final Map<String, Plugin> PLUGIN_REGISTRY = new ConcurrentHashMap<>();
    private static final Map<String, List<DriverConfig>> DRIVER_CONFIGS = new ConcurrentHashMap<>();
    private static final Set<String> SUPPORTED_DB_TYPES = new HashSet<>();
    
    // Inicialização estática dos plugins
    static {
        loadAllPlugins();
        validatePlugins();
        logSupportedDatabases();
    }
    
    /**
     * Carrega todos os plugins disponíveis usando ServiceLoader
     */
    private static void loadAllPlugins() {
        log.info("Iniciando carregamento de plugins de banco de dados...");
        
        ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class);
        Iterator<Plugin> iterator = serviceLoader.iterator();
        
        int pluginCount = 0;
        while (iterator.hasNext()) {
            try {
                Plugin plugin = iterator.next();
                DBConfig dbConfig = plugin.getDBConfig();
                
                if (dbConfig != null && StringUtils.isNotBlank(dbConfig.getDbType())) {
                    String dbType = dbConfig.getDbType().toUpperCase();
                    PLUGIN_REGISTRY.put(dbType, plugin);
                    SUPPORTED_DB_TYPES.add(dbType);
                    
                    // Registra configurações de drivers
                    if (dbConfig.getDriverConfigList() != null) {
                        DRIVER_CONFIGS.put(dbType, dbConfig.getDriverConfigList());
                    }
                    
                    pluginCount++;
                    log.debug("Plugin carregado: {} - {}", dbType, dbConfig.getName());
                } else {
                    log.warn("Plugin com configuração inválida ignorado: {}", plugin.getClass().getName());
                }
            } catch (Exception e) {
                log.error("Erro ao carregar plugin: {}", e.getMessage(), e);
            }
        }
        
        log.info("Carregamento concluído. {} plugins registrados.", pluginCount);
    }
    
    /**
     * Valida se todos os plugins essenciais estão carregados
     */
    private static void validatePlugins() {
        String[] essentialDatabases = {"MYSQL", "POSTGRESQL", "H2", "SQLITE"};
        
        for (String dbType : essentialDatabases) {
            if (!PLUGIN_REGISTRY.containsKey(dbType)) {
                log.warn("Plugin essencial não encontrado: {}", dbType);
            }
        }
    }
    
    /**
     * Registra no log todos os bancos de dados suportados
     */
    private static void logSupportedDatabases() {
        log.info("Bancos de dados suportados: {}", 
            String.join(", ", SUPPORTED_DB_TYPES));
    }
    
    /**
     * Obtém o plugin para um tipo de banco específico
     */
    public static Plugin getPlugin(String dbType) {
        if (StringUtils.isBlank(dbType)) {
            throw new IllegalArgumentException("Tipo de banco não pode ser vazio");
        }
        
        Plugin plugin = PLUGIN_REGISTRY.get(dbType.toUpperCase());
        if (plugin == null) {
            throw new UnsupportedOperationException(
                String.format("Banco de dados não suportado: %s. Tipos suportados: %s", 
                    dbType, String.join(", ", SUPPORTED_DB_TYPES)));
        }
        
        return plugin;
    }
    
    /**
     * Verifica se um tipo de banco é suportado
     */
    public static boolean isSupported(String dbType) {
        return StringUtils.isNotBlank(dbType) && 
               SUPPORTED_DB_TYPES.contains(dbType.toUpperCase());
    }
    
    /**
     * Obtém todos os tipos de banco suportados
     */
    public static Set<String> getSupportedDatabaseTypes() {
        return new HashSet<>(SUPPORTED_DB_TYPES);
    }
    
    /**
     * Obtém a configuração padrão do driver para um tipo de banco
     */
    public static DriverConfig getDefaultDriverConfig(String dbType) {
        Plugin plugin = getPlugin(dbType);
        return plugin.getDBConfig().getDefaultDriverConfig();
    }
    
    /**
     * Obtém todas as configurações de driver disponíveis para um tipo de banco
     */
    public static List<DriverConfig> getDriverConfigs(String dbType) {
        return DRIVER_CONFIGS.getOrDefault(dbType.toUpperCase(), Collections.emptyList());
    }
    
    /**
     * Obtém informações detalhadas sobre um banco de dados
     */
    public static DBConfig getDatabaseConfig(String dbType) {
        Plugin plugin = getPlugin(dbType);
        return plugin.getDBConfig();
    }
    
    /**
     * Lista todos os bancos de dados com suas configurações
     */
    public static Map<String, DBConfig> getAllDatabaseConfigs() {
        return PLUGIN_REGISTRY.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getDBConfig()
            ));
    }
    
    /**
     * Recarrega todos os plugins (útil para desenvolvimento)
     */
    public static synchronized void reloadPlugins() {
        log.info("Recarregando plugins de banco de dados...");
        
        PLUGIN_REGISTRY.clear();
        DRIVER_CONFIGS.clear();
        SUPPORTED_DB_TYPES.clear();
        
        loadAllPlugins();
        validatePlugins();
        logSupportedDatabases();
    }
    
    /**
     * Obtém estatísticas dos plugins carregados
     */
    public static Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPlugins", PLUGIN_REGISTRY.size());
        stats.put("supportedDatabases", SUPPORTED_DB_TYPES);
        stats.put("totalDriverConfigs", DRIVER_CONFIGS.values().stream()
            .mapToInt(List::size).sum());
        
        return stats;
    }
}