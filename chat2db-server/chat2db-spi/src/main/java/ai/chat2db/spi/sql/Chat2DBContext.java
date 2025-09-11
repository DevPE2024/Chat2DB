
package ai.chat2db.spi.sql;

import ai.chat2db.spi.DBManage;
import ai.chat2db.spi.MetaData;
import ai.chat2db.spi.Plugin;
import ai.chat2db.spi.SqlBuilder;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.manager.DatabaseDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jipengfei
 * @version : Chat2DBContext.java
 */
@Slf4j
public class Chat2DBContext {
    private static final ThreadLocal<ConnectInfo> CONNECT_INFO_THREAD_LOCAL = new ThreadLocal<>();



    public static Map<String, Plugin> PLUGIN_MAP = new ConcurrentHashMap<>();

    static {
        // Mantém compatibilidade com código legado
        ServiceLoader<Plugin> s = ServiceLoader.load(Plugin.class);
        Iterator<Plugin> iterator = s.iterator();
        while (iterator.hasNext()) {
            Plugin plugin = iterator.next();
            PLUGIN_MAP.put(plugin.getDBConfig().getDbType(), plugin);
        }
        
        log.info("Chat2DBContext inicializado com {} plugins. Usando DatabaseDriverManager para gerenciamento avançado.", 
            PLUGIN_MAP.size());
    }

    public static DriverConfig getDefaultDriverConfig(String dbType) {
        // Usa o novo gerenciador com fallback para compatibilidade
        try {
            return DatabaseDriverManager.getDefaultDriverConfig(dbType);
        } catch (Exception e) {
            log.warn("Fallback para método legado para dbType: {}", dbType);
            return PLUGIN_MAP.get(dbType).getDBConfig().getDefaultDriverConfig();
        }
    }

    public static SqlBuilder getSqlBuilder() {
        String dbType = getConnectInfo().getDbType();
        try {
            return DatabaseDriverManager.getPlugin(dbType).getMetaData().getSqlBuilder();
        } catch (Exception e) {
            log.warn("Fallback para método legado para getSqlBuilder: {}", dbType);
            return PLUGIN_MAP.get(dbType).getMetaData().getSqlBuilder();
        }
    }

    /**
     * Get the ContentContext of the current thread
     *
     * @return
     */
    public static ConnectInfo getConnectInfo() {
        return CONNECT_INFO_THREAD_LOCAL.get();
    }

    public static MetaData getMetaData() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getMetaData();
    }

    public static MetaData getMetaData(String dbType) {
        if (StringUtils.isBlank(dbType)) {
            return getMetaData();
        }
        return PLUGIN_MAP.get(dbType).getMetaData();
    }

    public static DBConfig getDBConfig(String dbType) {
        return PLUGIN_MAP.get(dbType).getDBConfig();
    }

    public static DBConfig getDBConfig() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getDBConfig();
    }

    public static DBManage getDBManage() {
        return PLUGIN_MAP.get(getConnectInfo().getDbType()).getDBManage();
    }

    public static Connection getConnection() {
//        ConnectInfo connectInfo = getConnectInfo();
//        Connection connection = connectInfo.getConnection();
//        try {
//            if (connection == null || connection.isClosed()) {
//                synchronized (connectInfo) {
//                    connection = connectInfo.getConnection();
//                    try {
//                        if (connection != null && !connection.isClosed()) {
//                            log.info("get connection from cache");
//                            return connection;
//                        } else {
//                            log.info("get connection from db begin");
//                            connection = getDBManage().getConnection(connectInfo);
//                            log.info("get connection from db end");
//                        }
//                    } catch (SQLException e) {
//                        log.error("get connection error", e);
//                        log.info("get connection from db begin2");
//                        connection = getDBManage().getConnection(connectInfo);
//                        log.info("get connection from db end2");
//                    }
//                    connectInfo.setConnection(connection);
//                }
//            }
//        } catch (SQLException e) {
//            log.error("get connection error", e);
//        }
        return ConnectionPool.getConnection(getConnectInfo());
    }


    public static String getDbVersion() {
        ConnectInfo connectInfo = getConnectInfo();
        String dbVersion = connectInfo.getDbVersion();
        if (dbVersion == null) {
            synchronized (connectInfo) {
                if (connectInfo.getDbVersion() != null) {
                    return connectInfo.getDbVersion();
                } else {
                    dbVersion = SQLExecutor.getInstance().getDbVersion(getConnection());
                    connectInfo.setDbVersion(dbVersion);
                    return connectInfo.getDbVersion();
                }
            }
        } else {
            return dbVersion;
        }

    }


    /**
     * Set context
     *
     * @param info
     */
    public static void putContext(ConnectInfo info) {
        DriverConfig config = info.getDriverConfig();
        if (config == null) {
            config = getDefaultDriverConfig(info.getDbType());
            info.setDriverConfig(config);
        }
        CONNECT_INFO_THREAD_LOCAL.set(info);
    }

    /**
     * Set context
     */
    public static void removeContext() {
        ConnectInfo connectInfo = CONNECT_INFO_THREAD_LOCAL.get();
        if (connectInfo != null) {
//            connectInfo.close();
            CONNECT_INFO_THREAD_LOCAL.remove();
            ConnectionPool.close(connectInfo);
        }
    }

    public static void close() {
        removeContext();
    }

}
