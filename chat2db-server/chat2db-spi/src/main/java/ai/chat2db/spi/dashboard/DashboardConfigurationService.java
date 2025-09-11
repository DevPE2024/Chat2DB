package ai.chat2db.spi.dashboard;

import ai.chat2db.spi.chart.ChartDataProcessor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de configuração de dashboards
 * Gerencia criação, edição e configuração de dashboards personalizados
 */
@Slf4j
@Service
public class DashboardConfigurationService {

    /**
     * Configuração completa de um dashboard
     */
    @Data
    public static class DashboardConfig {
        private String id;
        private String name;
        private String description;
        private String category;
        private DashboardLayout layout = new DashboardLayout();
        private List<WidgetConfig> widgets = new ArrayList<>();
        private DashboardSettings settings = new DashboardSettings();
        private DashboardMetadata metadata = new DashboardMetadata();
        private List<String> tags = new ArrayList<>();
        private Map<String, Object> customProperties = new HashMap<>();
        
        public static DashboardConfig createDefault(String name) {
            DashboardConfig config = new DashboardConfig();
            config.setId(UUID.randomUUID().toString());
            config.setName(name);
            config.setDescription("Dashboard criado automaticamente");
            config.setCategory("General");
            config.getMetadata().setCreatedAt(LocalDateTime.now());
            config.getMetadata().setUpdatedAt(LocalDateTime.now());
            return config;
        }
    }

    /**
     * Layout do dashboard
     */
    @Data
    public static class DashboardLayout {
        private LayoutType type = LayoutType.GRID;
        private int columns = 12;
        private int rows = 8;
        private int gap = 16;
        private boolean responsive = true;
        private Map<String, Object> breakpoints = new HashMap<>();
        
        public enum LayoutType {
            GRID,           // Layout em grade
            FLEX,           // Layout flexível
            ABSOLUTE,       // Posicionamento absoluto
            MASONRY,        // Layout tipo Pinterest
            TABS,           // Layout com abas
            ACCORDION       // Layout acordeão
        }
        
        public DashboardLayout() {
            // Configurar breakpoints responsivos padrão
            breakpoints.put("xs", Map.of("width", 480, "columns", 1));
            breakpoints.put("sm", Map.of("width", 768, "columns", 2));
            breakpoints.put("md", Map.of("width", 1024, "columns", 3));
            breakpoints.put("lg", Map.of("width", 1200, "columns", 4));
            breakpoints.put("xl", Map.of("width", 1600, "columns", 6));
        }
    }

    /**
     * Configuração de widget individual
     */
    @Data
    public static class WidgetConfig {
        private String id;
        private String title;
        private WidgetType type;
        private WidgetPosition position = new WidgetPosition();
        private WidgetSize size = new WidgetSize();
        private DataSource dataSource = new DataSource();
        private ChartDataProcessor.ProcessingOptions chartOptions = new ChartDataProcessor.ProcessingOptions();
        private WidgetStyle style = new WidgetStyle();
        private WidgetBehavior behavior = new WidgetBehavior();
        private Map<String, Object> customConfig = new HashMap<>();
        private boolean visible = true;
        private int zIndex = 1;
        
        public enum WidgetType {
            CHART,          // Gráfico
            TABLE,          // Tabela
            METRIC,         // Métrica simples
            TEXT,           // Texto/HTML
            IMAGE,          // Imagem
            IFRAME,         // Frame incorporado
            FILTER,         // Filtro
            BUTTON,         // Botão de ação
            TIMER,          // Timer/Relógio
            PROGRESS,       // Barra de progresso
            GAUGE,          // Medidor
            MAP,            // Mapa
            CALENDAR,       // Calendário
            FORM,           // Formulário
            CUSTOM          // Widget personalizado
        }
        
        public static WidgetConfig createChart(String title, ChartDataProcessor.ChartType chartType) {
            WidgetConfig widget = new WidgetConfig();
            widget.setId(UUID.randomUUID().toString());
            widget.setTitle(title);
            widget.setType(WidgetType.CHART);
            widget.getChartOptions().setChartType(chartType);
            return widget;
        }
        
        public static WidgetConfig createTable(String title) {
            WidgetConfig widget = new WidgetConfig();
            widget.setId(UUID.randomUUID().toString());
            widget.setTitle(title);
            widget.setType(WidgetType.TABLE);
            return widget;
        }
        
        public static WidgetConfig createMetric(String title, String value) {
            WidgetConfig widget = new WidgetConfig();
            widget.setId(UUID.randomUUID().toString());
            widget.setTitle(title);
            widget.setType(WidgetType.METRIC);
            widget.getCustomConfig().put("value", value);
            return widget;
        }
    }

    /**
     * Posição do widget
     */
    @Data
    public static class WidgetPosition {
        private int x = 0;
        private int y = 0;
        private int row = 0;
        private int column = 0;
        private String anchor = "top-left"; // top-left, top-right, bottom-left, bottom-right, center
        private boolean draggable = true;
        private boolean resizable = true;
    }

    /**
     * Tamanho do widget
     */
    @Data
    public static class WidgetSize {
        private int width = 4;      // Colunas da grade
        private int height = 3;     // Linhas da grade
        private int minWidth = 2;
        private int minHeight = 2;
        private int maxWidth = 12;
        private int maxHeight = 8;
        private String unit = "grid"; // grid, px, %, vh, vw
        private boolean autoHeight = false;
    }

    /**
     * Fonte de dados do widget
     */
    @Data
    public static class DataSource {
        private DataSourceType type = DataSourceType.SQL;
        private String connectionId;
        private String query;
        private Map<String, Object> parameters = new HashMap<>();
        private RefreshConfig refresh = new RefreshConfig();
        private CacheConfig cache = new CacheConfig();
        private List<DataTransformation> transformations = new ArrayList<>();
        
        public enum DataSourceType {
            SQL,            // Consulta SQL
            API,            // API REST
            STATIC,         // Dados estáticos
            REALTIME,       // Dados em tempo real
            FILE,           // Arquivo (CSV, JSON, etc.)
            CALCULATED      // Dados calculados
        }
        
        @Data
        public static class RefreshConfig {
            private boolean enabled = false;
            private int intervalSeconds = 300; // 5 minutos
            private RefreshTrigger trigger = RefreshTrigger.INTERVAL;
            private List<String> dependencies = new ArrayList<>();
            
            public enum RefreshTrigger {
                INTERVAL,       // Intervalo fixo
                ON_CHANGE,      // Quando dados mudam
                MANUAL,         // Manual apenas
                ON_FOCUS,       // Quando widget ganha foco
                ON_LOAD         // Ao carregar dashboard
            }
        }
        
        @Data
        public static class CacheConfig {
            private boolean enabled = true;
            private int ttlSeconds = 300;
            private String key;
            private boolean shareCache = false;
        }
        
        @Data
        public static class DataTransformation {
            private TransformationType type;
            private Map<String, Object> config = new HashMap<>();
            
            public enum TransformationType {
                FILTER,         // Filtrar dados
                SORT,           // Ordenar dados
                GROUP,          // Agrupar dados
                AGGREGATE,      // Agregar dados
                CALCULATE,      // Calcular campos
                FORMAT,         // Formatar valores
                JOIN,           // Juntar com outros dados
                PIVOT           // Tabela dinâmica
            }
        }
    }

    /**
     * Estilo visual do widget
     */
    @Data
    public static class WidgetStyle {
        private String theme = "default";
        private Map<String, String> colors = new HashMap<>();
        private BorderStyle border = new BorderStyle();
        private PaddingStyle padding = new PaddingStyle();
        private ShadowStyle shadow = new ShadowStyle();
        private String backgroundColor = "#ffffff";
        private String backgroundImage;
        private double opacity = 1.0;
        private String borderRadius = "4px";
        private String fontFamily = "Arial, sans-serif";
        private String fontSize = "14px";
        private Map<String, Object> customCss = new HashMap<>();
        
        @Data
        public static class BorderStyle {
            private String width = "1px";
            private String style = "solid";
            private String color = "#e0e0e0";
        }
        
        @Data
        public static class PaddingStyle {
            private String top = "16px";
            private String right = "16px";
            private String bottom = "16px";
            private String left = "16px";
        }
        
        @Data
        public static class ShadowStyle {
            private boolean enabled = true;
            private String color = "rgba(0,0,0,0.1)";
            private String blur = "4px";
            private String spread = "0px";
            private String offsetX = "0px";
            private String offsetY = "2px";
        }
    }

    /**
     * Comportamento do widget
     */
    @Data
    public static class WidgetBehavior {
        private boolean interactive = true;
        private boolean exportable = true;
        private boolean printable = true;
        private boolean fullscreenable = true;
        private List<ActionConfig> actions = new ArrayList<>();
        private List<EventHandler> eventHandlers = new ArrayList<>();
        private TooltipConfig tooltip = new TooltipConfig();
        private AnimationConfig animation = new AnimationConfig();
        
        @Data
        public static class ActionConfig {
            private String id;
            private String label;
            private ActionType type;
            private Map<String, Object> config = new HashMap<>();
            private String icon;
            private boolean visible = true;
            
            public enum ActionType {
                REFRESH,        // Atualizar dados
                EXPORT,         // Exportar dados
                DRILL_DOWN,     // Drill down
                FILTER,         // Aplicar filtro
                NAVIGATE,       // Navegar para outra página
                CUSTOM          // Ação personalizada
            }
        }
        
        @Data
        public static class EventHandler {
            private String event; // click, hover, load, etc.
            private String action;
            private Map<String, Object> parameters = new HashMap<>();
        }
        
        @Data
        public static class TooltipConfig {
            private boolean enabled = true;
            private String template;
            private String position = "auto";
            private int delay = 500;
        }
        
        @Data
        public static class AnimationConfig {
            private boolean enabled = true;
            private String entrance = "fadeIn";
            private String exit = "fadeOut";
            private int duration = 300;
            private String easing = "ease-in-out";
        }
    }

    /**
     * Configurações globais do dashboard
     */
    @Data
    public static class DashboardSettings {
        private boolean autoRefresh = false;
        private int refreshInterval = 300; // segundos
        private String timezone = "UTC";
        private String dateFormat = "yyyy-MM-dd";
        private String timeFormat = "HH:mm:ss";
        private String numberFormat = "#,##0.00";
        private String currency = "USD";
        private String locale = "en-US";
        private ThemeConfig theme = new ThemeConfig();
        private SecurityConfig security = new SecurityConfig();
        private PerformanceConfig performance = new PerformanceConfig();
        private Map<String, Object> globalFilters = new HashMap<>();
        private Map<String, Object> globalParameters = new HashMap<>();
        
        @Data
        public static class ThemeConfig {
            private String name = "default";
            private String primaryColor = "#1890ff";
            private String secondaryColor = "#52c41a";
            private String backgroundColor = "#f0f2f5";
            private String textColor = "#262626";
            private boolean darkMode = false;
            private Map<String, String> customColors = new HashMap<>();
        }
        
        @Data
        public static class SecurityConfig {
            private boolean requireAuth = false;
            private List<String> allowedUsers = new ArrayList<>();
            private List<String> allowedRoles = new ArrayList<>();
            private boolean publicAccess = true;
            private String accessToken;
            private int sessionTimeout = 3600; // segundos
        }
        
        @Data
        public static class PerformanceConfig {
            private boolean enableCache = true;
            private int maxConcurrentQueries = 10;
            private int queryTimeout = 30; // segundos
            private boolean enableLazyLoading = true;
            private boolean enableVirtualization = false;
            private int maxDataPoints = 10000;
        }
    }

    /**
     * Metadados do dashboard
     */
    @Data
    public static class DashboardMetadata {
        private String createdBy;
        private LocalDateTime createdAt;
        private String updatedBy;
        private LocalDateTime updatedAt;
        private String version = "1.0.0";
        private int viewCount = 0;
        private LocalDateTime lastViewed;
        private List<String> collaborators = new ArrayList<>();
        private Map<String, Object> analytics = new HashMap<>();
        private boolean isTemplate = false;
        private boolean isPublic = false;
        private String shareUrl;
        private List<String> exportFormats = Arrays.asList("PDF", "PNG", "JSON");
    }

    /**
     * Template de dashboard pré-configurado
     */
    @Data
    public static class DashboardTemplate {
        private String id;
        private String name;
        private String description;
        private String category;
        private String previewImage;
        private DashboardConfig config;
        private List<String> requiredFields = new ArrayList<>();
        private Map<String, Object> defaultValues = new HashMap<>();
        private boolean isBuiltIn = false;
        
        public static DashboardTemplate createExecutiveSummary() {
            DashboardTemplate template = new DashboardTemplate();
            template.setId("executive-summary");
            template.setName("Executive Summary");
            template.setDescription("Dashboard executivo com métricas principais");
            template.setCategory("Business");
            template.setBuiltIn(true);
            
            DashboardConfig config = DashboardConfig.createDefault("Executive Summary");
            
            // Adicionar widgets padrão
            config.getWidgets().add(WidgetConfig.createMetric("Total Revenue", "$0"));
            config.getWidgets().add(WidgetConfig.createChart("Sales Trend", ChartDataProcessor.ChartType.LINE));
            config.getWidgets().add(WidgetConfig.createChart("Top Products", ChartDataProcessor.ChartType.BAR));
            config.getWidgets().add(WidgetConfig.createTable("Recent Orders"));
            
            template.setConfig(config);
            return template;
        }
        
        public static DashboardTemplate createAnalyticsDashboard() {
            DashboardTemplate template = new DashboardTemplate();
            template.setId("analytics-dashboard");
            template.setName("Analytics Dashboard");
            template.setDescription("Dashboard de análise de dados com gráficos avançados");
            template.setCategory("Analytics");
            template.setBuiltIn(true);
            
            DashboardConfig config = DashboardConfig.createDefault("Analytics Dashboard");
            
            // Adicionar widgets de análise
            config.getWidgets().add(WidgetConfig.createChart("Distribution", ChartDataProcessor.ChartType.HISTOGRAM));
            config.getWidgets().add(WidgetConfig.createChart("Correlation", ChartDataProcessor.ChartType.SCATTER));
            config.getWidgets().add(WidgetConfig.createChart("Heatmap", ChartDataProcessor.ChartType.HEATMAP));
            config.getWidgets().add(WidgetConfig.createChart("Trends", ChartDataProcessor.ChartType.AREA));
            
            template.setConfig(config);
            return template;
        }
    }

    // Cache de configurações
    private final Map<String, DashboardConfig> configCache = new HashMap<>();
    private final Map<String, DashboardTemplate> templateCache = new HashMap<>();

    /**
     * Construtor - inicializa templates padrão
     */
    public DashboardConfigurationService() {
        initializeBuiltInTemplates();
    }

    /**
     * Cria uma nova configuração de dashboard
     */
    public DashboardConfig createDashboard(String name, String templateId) {
        DashboardConfig config;
        
        if (templateId != null && templateCache.containsKey(templateId)) {
            // Criar a partir de template
            DashboardTemplate template = templateCache.get(templateId);
            config = cloneDashboardConfig(template.getConfig());
            config.setId(UUID.randomUUID().toString());
            config.setName(name);
        } else {
            // Criar dashboard vazio
            config = DashboardConfig.createDefault(name);
        }
        
        config.getMetadata().setCreatedAt(LocalDateTime.now());
        config.getMetadata().setUpdatedAt(LocalDateTime.now());
        
        // Salvar no cache
        configCache.put(config.getId(), config);
        
        log.info("Dashboard criado: {} (ID: {})", name, config.getId());
        return config;
    }

    /**
     * Atualiza configuração de dashboard
     */
    public DashboardConfig updateDashboard(String dashboardId, DashboardConfig updatedConfig) {
        if (!configCache.containsKey(dashboardId)) {
            throw new IllegalArgumentException("Dashboard não encontrado: " + dashboardId);
        }
        
        updatedConfig.setId(dashboardId);
        updatedConfig.getMetadata().setUpdatedAt(LocalDateTime.now());
        
        // Validar configuração
        validateDashboardConfig(updatedConfig);
        
        configCache.put(dashboardId, updatedConfig);
        
        log.info("Dashboard atualizado: {}", dashboardId);
        return updatedConfig;
    }

    /**
     * Obtém configuração de dashboard
     */
    public DashboardConfig getDashboard(String dashboardId) {
        DashboardConfig config = configCache.get(dashboardId);
        if (config == null) {
            throw new IllegalArgumentException("Dashboard não encontrado: " + dashboardId);
        }
        
        // Atualizar contador de visualizações
        config.getMetadata().setViewCount(config.getMetadata().getViewCount() + 1);
        config.getMetadata().setLastViewed(LocalDateTime.now());
        
        return config;
    }

    /**
     * Lista todos os dashboards
     */
    public List<DashboardConfig> listDashboards(String category, String createdBy) {
        return configCache.values().stream()
            .filter(config -> category == null || category.equals(config.getCategory()))
            .filter(config -> createdBy == null || createdBy.equals(config.getMetadata().getCreatedBy()))
            .sorted((a, b) -> b.getMetadata().getUpdatedAt().compareTo(a.getMetadata().getUpdatedAt()))
            .collect(Collectors.toList());
    }

    /**
     * Remove dashboard
     */
    public boolean deleteDashboard(String dashboardId) {
        if (configCache.containsKey(dashboardId)) {
            configCache.remove(dashboardId);
            log.info("Dashboard removido: {}", dashboardId);
            return true;
        }
        return false;
    }

    /**
     * Adiciona widget ao dashboard
     */
    public DashboardConfig addWidget(String dashboardId, WidgetConfig widget) {
        DashboardConfig config = getDashboard(dashboardId);
        
        // Gerar ID se não fornecido
        if (widget.getId() == null || widget.getId().isEmpty()) {
            widget.setId(UUID.randomUUID().toString());
        }
        
        // Posicionar widget automaticamente se necessário
        if (widget.getPosition().getX() == 0 && widget.getPosition().getY() == 0) {
            positionWidgetAutomatically(config, widget);
        }
        
        config.getWidgets().add(widget);
        config.getMetadata().setUpdatedAt(LocalDateTime.now());
        
        configCache.put(dashboardId, config);
        
        log.info("Widget adicionado ao dashboard {}: {} ({})", dashboardId, widget.getTitle(), widget.getType());
        return config;
    }

    /**
     * Remove widget do dashboard
     */
    public DashboardConfig removeWidget(String dashboardId, String widgetId) {
        DashboardConfig config = getDashboard(dashboardId);
        
        boolean removed = config.getWidgets().removeIf(widget -> widget.getId().equals(widgetId));
        
        if (removed) {
            config.getMetadata().setUpdatedAt(LocalDateTime.now());
            configCache.put(dashboardId, config);
            log.info("Widget removido do dashboard {}: {}", dashboardId, widgetId);
        }
        
        return config;
    }

    /**
     * Atualiza widget específico
     */
    public DashboardConfig updateWidget(String dashboardId, String widgetId, WidgetConfig updatedWidget) {
        DashboardConfig config = getDashboard(dashboardId);
        
        for (int i = 0; i < config.getWidgets().size(); i++) {
            if (config.getWidgets().get(i).getId().equals(widgetId)) {
                updatedWidget.setId(widgetId);
                config.getWidgets().set(i, updatedWidget);
                config.getMetadata().setUpdatedAt(LocalDateTime.now());
                configCache.put(dashboardId, config);
                log.info("Widget atualizado no dashboard {}: {}", dashboardId, widgetId);
                break;
            }
        }
        
        return config;
    }

    /**
     * Obtém templates disponíveis
     */
    public List<DashboardTemplate> getAvailableTemplates(String category) {
        return templateCache.values().stream()
            .filter(template -> category == null || category.equals(template.getCategory()))
            .sorted(Comparator.comparing(DashboardTemplate::getName))
            .collect(Collectors.toList());
    }

    /**
     * Cria template personalizado a partir de dashboard existente
     */
    public DashboardTemplate createTemplate(String dashboardId, String templateName, String description) {
        DashboardConfig config = getDashboard(dashboardId);
        
        DashboardTemplate template = new DashboardTemplate();
        template.setId(UUID.randomUUID().toString());
        template.setName(templateName);
        template.setDescription(description);
        template.setCategory(config.getCategory());
        template.setConfig(cloneDashboardConfig(config));
        template.setBuiltIn(false);
        
        templateCache.put(template.getId(), template);
        
        log.info("Template criado: {} a partir do dashboard {}", templateName, dashboardId);
        return template;
    }

    /**
     * Exporta configuração de dashboard
     */
    public Map<String, Object> exportDashboard(String dashboardId, String format) {
        DashboardConfig config = getDashboard(dashboardId);
        
        Map<String, Object> export = new HashMap<>();
        export.put("version", "1.0");
        export.put("exportedAt", LocalDateTime.now());
        export.put("format", format);
        export.put("dashboard", config);
        
        log.info("Dashboard exportado: {} (formato: {})", dashboardId, format);
        return export;
    }

    /**
     * Importa configuração de dashboard
     */
    public DashboardConfig importDashboard(Map<String, Object> importData) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> dashboardData = (Map<String, Object>) importData.get("dashboard");
            
            // Converter dados importados para DashboardConfig
            // (implementação simplificada - em produção usaria Jackson ou similar)
            DashboardConfig config = convertMapToDashboardConfig(dashboardData);
            
            // Gerar novo ID
            config.setId(UUID.randomUUID().toString());
            config.getMetadata().setCreatedAt(LocalDateTime.now());
            config.getMetadata().setUpdatedAt(LocalDateTime.now());
            
            // Validar e salvar
            validateDashboardConfig(config);
            configCache.put(config.getId(), config);
            
            log.info("Dashboard importado: {}", config.getName());
            return config;
            
        } catch (Exception e) {
            log.error("Erro ao importar dashboard", e);
            throw new RuntimeException("Erro ao importar dashboard: " + e.getMessage());
        }
    }

    /**
     * Valida configuração de dashboard
     */
    private void validateDashboardConfig(DashboardConfig config) {
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do dashboard é obrigatório");
        }
        
        if (config.getLayout().getColumns() < 1 || config.getLayout().getColumns() > 24) {
            throw new IllegalArgumentException("Número de colunas deve estar entre 1 e 24");
        }
        
        // Validar widgets
        for (WidgetConfig widget : config.getWidgets()) {
            validateWidgetConfig(widget);
        }
        
        // Validar posicionamento dos widgets
        validateWidgetPositions(config);
    }

    /**
     * Valida configuração de widget
     */
    private void validateWidgetConfig(WidgetConfig widget) {
        if (widget.getTitle() == null || widget.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Título do widget é obrigatório");
        }
        
        if (widget.getType() == null) {
            throw new IllegalArgumentException("Tipo do widget é obrigatório");
        }
        
        if (widget.getSize().getWidth() < 1 || widget.getSize().getHeight() < 1) {
            throw new IllegalArgumentException("Tamanho do widget deve ser positivo");
        }
    }

    /**
     * Valida posicionamento dos widgets
     */
    private void validateWidgetPositions(DashboardConfig config) {
        // Verificar sobreposições
        List<WidgetConfig> widgets = config.getWidgets();
        
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                if (widgetsOverlap(widgets.get(i), widgets.get(j))) {
                    log.warn("Widgets sobrepostos detectados: {} e {}", 
                            widgets.get(i).getTitle(), widgets.get(j).getTitle());
                }
            }
        }
    }

    /**
     * Verifica se dois widgets se sobrepõem
     */
    private boolean widgetsOverlap(WidgetConfig widget1, WidgetConfig widget2) {
        int x1 = widget1.getPosition().getX();
        int y1 = widget1.getPosition().getY();
        int w1 = widget1.getSize().getWidth();
        int h1 = widget1.getSize().getHeight();
        
        int x2 = widget2.getPosition().getX();
        int y2 = widget2.getPosition().getY();
        int w2 = widget2.getSize().getWidth();
        int h2 = widget2.getSize().getHeight();
        
        return !(x1 + w1 <= x2 || x2 + w2 <= x1 || y1 + h1 <= y2 || y2 + h2 <= y1);
    }

    /**
     * Posiciona widget automaticamente
     */
    private void positionWidgetAutomatically(DashboardConfig config, WidgetConfig widget) {
        int columns = config.getLayout().getColumns();
        int widgetWidth = widget.getSize().getWidth();
        int widgetHeight = widget.getSize().getHeight();
        
        // Encontrar primeira posição disponível
        for (int y = 0; y < 100; y++) { // Limite de 100 linhas
            for (int x = 0; x <= columns - widgetWidth; x++) {
                widget.getPosition().setX(x);
                widget.getPosition().setY(y);
                
                // Verificar se posição está livre
                boolean positionFree = config.getWidgets().stream()
                    .noneMatch(existingWidget -> widgetsOverlap(widget, existingWidget));
                
                if (positionFree) {
                    return; // Posição encontrada
                }
            }
        }
        
        // Se não encontrou posição, colocar no final
        widget.getPosition().setX(0);
        widget.getPosition().setY(config.getWidgets().size());
    }

    /**
     * Clona configuração de dashboard
     */
    private DashboardConfig cloneDashboardConfig(DashboardConfig original) {
        // Implementação simplificada - em produção usaria deep copy
        DashboardConfig clone = new DashboardConfig();
        clone.setName(original.getName());
        clone.setDescription(original.getDescription());
        clone.setCategory(original.getCategory());
        // ... copiar outros campos conforme necessário
        return clone;
    }

    /**
     * Converte Map para DashboardConfig
     */
    private DashboardConfig convertMapToDashboardConfig(Map<String, Object> data) {
        // Implementação simplificada - em produção usaria Jackson ObjectMapper
        DashboardConfig config = new DashboardConfig();
        config.setName((String) data.get("name"));
        config.setDescription((String) data.get("description"));
        config.setCategory((String) data.get("category"));
        // ... converter outros campos conforme necessário
        return config;
    }

    /**
     * Inicializa templates padrão
     */
    private void initializeBuiltInTemplates() {
        templateCache.put("executive-summary", DashboardTemplate.createExecutiveSummary());
        templateCache.put("analytics-dashboard", DashboardTemplate.createAnalyticsDashboard());
        
        log.info("Templates padrão inicializados: {}", templateCache.size());
    }

    /**
     * Obtém estatísticas de uso dos dashboards
     */
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDashboards", configCache.size());
        stats.put("totalTemplates", templateCache.size());
        
        int totalWidgets = configCache.values().stream()
            .mapToInt(config -> config.getWidgets().size())
            .sum();
        stats.put("totalWidgets", totalWidgets);
        
        Map<String, Long> widgetTypeCount = configCache.values().stream()
            .flatMap(config -> config.getWidgets().stream())
            .collect(Collectors.groupingBy(
                widget -> widget.getType().toString(),
                Collectors.counting()
            ));
        stats.put("widgetTypeDistribution", widgetTypeCount);
        
        Map<String, Long> categoryCount = configCache.values().stream()
            .collect(Collectors.groupingBy(
                DashboardConfig::getCategory,
                Collectors.counting()
            ));
        stats.put("categoryDistribution", categoryCount);
        
        return stats;
    }
}