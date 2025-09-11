package ai.chat2db.server.domain.repository;

import ai.chat2db.server.tools.common.model.ConfigJson;
import ai.chat2db.server.tools.common.util.ConfigUtils;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class Dbutils {

    private static final ThreadLocal<SqlSession> SQL_SESSION_THREAD_LOCAL = new ThreadLocal<>();

    public static void init() {
    }

    public static void setSession() {
        SqlSession session = sqlSessionFactory.openSession(true);
        SQL_SESSION_THREAD_LOCAL.set(session);
    }

    public static void removeSession() {
        SqlSession session = SQL_SESSION_THREAD_LOCAL.get();
        if (session != null) {
            session.close();
        }
        SQL_SESSION_THREAD_LOCAL.remove();
    }

    private static SqlSessionFactory sqlSessionFactory;

    static {
        try {
            before();
        } catch (IOException e) {
            log.error("Dbutils error", e);
        }
    }


    private static void before() throws IOException {
        SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        //This is the configuration object of mybatis-plus, which enhances the configuration of mybatis.
        MybatisConfiguration configuration = new MybatisConfiguration();
        //This is the initial configuration, this part of the code will be added later
        initConfiguration(configuration);
        //This is the initialization connector, such as the paging plug-in of mybatis-plus
        configuration.addInterceptor(initInterceptor());
        //Configuration log implementation
        configuration.setLogImpl(Slf4jImpl.class);
        //Scan the package where the mapper interface is located
        configuration.addMappers("ai.chat2db.server.domain.repository.mapper");
        //Globalconfig required to build mybatis-plus
        GlobalConfig globalConfig = GlobalConfigUtils.getGlobalConfig(configuration);
        //This parameter will automatically generate the basic method mapping that implements baseMapper.
        globalConfig.setSqlInjector(new DefaultSqlInjector());
        //Set up id generator
        globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());
        //Set super class mapper
        globalConfig.setSuperMapperClass(BaseMapper.class);
        DataSource dataSource = initDataSource();
        Environment environment = new Environment("1", new JdbcTransactionFactory(), dataSource);
        configuration.setEnvironment(environment);
        //Set data source
        registryMapperXml(configuration, "mapper");
        //Build sqlSessionFactory
        sqlSessionFactory = builder.build(configuration);

        initFlyway(dataSource);
        //create session

    }

    private static void initFlyway(DataSource dataSource) {
        String currentVersion = ConfigUtils.getLocalVersion();
        ConfigJson configJson = ConfigUtils.getConfig();
        // Represents that the current version has been successfully launched
        if (StringUtils.isNotBlank(currentVersion) && configJson != null && StringUtils.equals(currentVersion,
                configJson.getLatestStartupSuccessVersion())) {
            return;
        }else {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();


            configJson.setLatestStartupSuccessVersion(currentVersion);
            ConfigUtils.setConfig(configJson);
        }
    }

    /**
     * Initial configuration
     *
     * @param configuration
     */
    private static void initConfiguration(MybatisConfiguration configuration) {
        //Turn on camel case conversion
        configuration.setMapUnderscoreToCamelCase(true);
        //Configure adding data to automatically return the data primary key
        configuration.setUseGeneratedKeys(true);
    }

    /**
     * Initialize data source with optimized HikariCP configuration
     * Implementação otimizada do pool de conexões para melhor performance
     *
     * @return DataSource configurado com HikariCP otimizado
     */
    private static DataSource initDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        String environment = StringUtils.defaultString(System.getProperty("spring.profiles.active"), "dev");
        
        // Configuração de URL baseada no ambiente
        if ("dev".equalsIgnoreCase(environment)) {
            dataSource.setJdbcUrl("jdbc:h2:file:~/.chat2db/db/chat2db_dev;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        } else if ("test".equalsIgnoreCase(environment)) {
            dataSource.setJdbcUrl("jdbc:h2:file:~/.chat2db/db/chat2db_test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        } else {
            dataSource.setJdbcUrl("jdbc:h2:~/.chat2db/db/chat2db;MODE=MYSQL;FILE_LOCK=NO;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        }
        
        // Configurações básicas do driver
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setPoolName("Chat2DB-HikariCP");
        
        // Configurações otimizadas do pool de conexões
        dataSource.setMaximumPoolSize(50); // Reduzido de 500 para 50 (mais realista)
        dataSource.setMinimumIdle(5); // Aumentado de 1 para 5 (melhor responsividade)
        dataSource.setConnectionTimeout(30000); // 30 segundos timeout para obter conexão
        dataSource.setIdleTimeout(300000); // 5 minutos idle timeout (aumentado)
        dataSource.setMaxLifetime(1800000); // 30 minutos max lifetime (aumentado)
        dataSource.setLeakDetectionThreshold(60000); // 1 minuto para detectar vazamentos
        
        // Configurações de validação e performance
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setValidationTimeout(5000); // 5 segundos para validação
        dataSource.setAutoCommit(true);
        dataSource.setIsolateInternalQueries(true);
        dataSource.setAllowPoolSuspension(false);
        
        // Configurações de cache e otimização
        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource.addDataSourceProperty("useLocalSessionState", "true");
        dataSource.addDataSourceProperty("rewriteBatchedStatements", "true");
        dataSource.addDataSourceProperty("cacheResultSetMetadata", "true");
        dataSource.addDataSourceProperty("cacheServerConfiguration", "true");
        dataSource.addDataSourceProperty("elideSetAutoCommits", "true");
        dataSource.addDataSourceProperty("maintainTimeStats", "false");
        
        log.info("Pool de conexões HikariCP inicializado com configurações otimizadas - Ambiente: {}", environment);
        return dataSource;
    }

    /**
     * Initialize interceptor
     *
     * @return
     */
    private static Interceptor initInterceptor() {
        //Create mybatis-plus plug-in object
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //Build a pagination plugin
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setDbType(DbType.H2);
        paginationInnerInterceptor.setOverflow(true);
        paginationInnerInterceptor.setMaxLimit(2000L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    /**
     * Parse mapper.xml file
     *
     * @param configuration
     * @param classPath
     * @throws IOException
     */
    private static void registryMapperXml(MybatisConfiguration configuration, String classPath) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> mapper = contextClassLoader.getResources(classPath);
        while (mapper.hasMoreElements()) {
            URL url = mapper.nextElement();
            if (url.getProtocol().equals("file")) {
                String path = url.getPath();
                File file = new File(path);
                File[] files = file.listFiles();
                for (File f : files) {
                    FileInputStream in = new FileInputStream(f);
                    XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(in, configuration, f.getPath(), configuration.getSqlFragments());
                    xmlMapperBuilder.parse();
                    in.close();
                }
            } else {
                JarURLConnection urlConnection = (JarURLConnection) url.openConnection();
                JarFile jarFile = urlConnection.getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    if (jarEntry.getName().endsWith("Mapper.xml")) {
                        InputStream in = jarFile.getInputStream(jarEntry);
                        XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(in, configuration, jarEntry.getName(), configuration.getSqlFragments());
                        xmlMapperBuilder.parse();
                        in.close();
                    }
                }
            }
        }
    }

    public static <T> T getMapper(Class<T> clazz) {
        SqlSession session = SQL_SESSION_THREAD_LOCAL.get();
        return session.getMapper(clazz);
    }

//    public static void main(String[] args) {
//
//        ExecutorService e = Executors.newCachedThreadPool();
//        for (int i = 0; i < 20; i++) {
//            e.execute(() -> {
//                SqlSession session = sqlSessionFactory.openSession();
//                DataSourceMapper mapper = session.getMapper(DataSourceMapper.class);
//                DataSourceDO dataSourceDO = mapper.selectById(1);
//                session.close();
//                System.out.println(JSON.toJSONString(dataSourceDO));
//            });
//        }
//
//    }
}
