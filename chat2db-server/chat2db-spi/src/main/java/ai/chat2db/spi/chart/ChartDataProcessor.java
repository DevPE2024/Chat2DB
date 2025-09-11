package ai.chat2db.spi.chart;

import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.model.ExecuteResult;
import ai.chat2db.spi.model.Header;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviço de processamento de dados para gráficos
 * Converte resultados de consultas SQL em formatos adequados para visualização
 */
@Slf4j
@Service
public class ChartDataProcessor {

    /**
     * Tipos de gráficos suportados
     */
    public enum ChartType {
        LINE,           // Gráfico de linha
        BAR,            // Gráfico de barras
        PIE,            // Gráfico de pizza
        AREA,           // Gráfico de área
        SCATTER,        // Gráfico de dispersão
        HISTOGRAM,      // Histograma
        HEATMAP,        // Mapa de calor
        GAUGE,          // Medidor
        FUNNEL,         // Funil
        RADAR           // Radar
    }

    /**
     * Configurações de processamento de dados
     */
    @Data
    public static class ProcessingOptions {
        private ChartType chartType = ChartType.BAR;
        private String xAxisColumn;
        private String yAxisColumn;
        private String groupByColumn;
        private String aggregationFunction = "SUM"; // SUM, COUNT, AVG, MIN, MAX
        private boolean enableDataLabels = true;
        private boolean enableLegend = true;
        private int maxDataPoints = 1000;
        private String dateFormat = "yyyy-MM-dd";
        private int decimalPlaces = 2;
        private boolean sortData = true;
        private String sortOrder = "ASC"; // ASC, DESC
        private Map<String, String> colorMapping = new HashMap<>();
        private List<String> excludeValues = new ArrayList<>();
        
        public static ProcessingOptions defaultOptions() {
            return new ProcessingOptions();
        }
        
        public static ProcessingOptions forLineChart(String xAxis, String yAxis) {
            ProcessingOptions options = new ProcessingOptions();
            options.setChartType(ChartType.LINE);
            options.setXAxisColumn(xAxis);
            options.setYAxisColumn(yAxis);
            return options;
        }
        
        public static ProcessingOptions forPieChart(String labelColumn, String valueColumn) {
            ProcessingOptions options = new ProcessingOptions();
            options.setChartType(ChartType.PIE);
            options.setXAxisColumn(labelColumn);
            options.setYAxisColumn(valueColumn);
            return options;
        }
    }

    /**
     * Dados processados para gráfico
     */
    @Data
    public static class ChartData {
        private ChartType type;
        private List<String> labels = new ArrayList<>();
        private List<DataSeries> series = new ArrayList<>();
        private ChartMetadata metadata = new ChartMetadata();
        private Map<String, Object> options = new HashMap<>();
        
        @Data
        public static class DataSeries {
            private String name;
            private List<Object> data = new ArrayList<>();
            private String color;
            private String type; // Para gráficos mistos
            private Map<String, Object> properties = new HashMap<>();
        }
        
        @Data
        public static class ChartMetadata {
            private String title;
            private String xAxisTitle;
            private String yAxisTitle;
            private int totalDataPoints;
            private LocalDateTime generatedAt = LocalDateTime.now();
            private String aggregationUsed;
            private Map<String, Object> statistics = new HashMap<>();
        }
    }

    /**
     * Processa dados de consulta SQL para formato de gráfico
     */
    public ChartData processData(ExecuteResult executeResult, ProcessingOptions options) {
        if (executeResult == null || executeResult.getDataList() == null || executeResult.getDataList().isEmpty()) {
            return createEmptyChartData(options.getChartType());
        }

        try {
            ChartData chartData = new ChartData();
            chartData.setType(options.getChartType());
            
            // Processar dados baseado no tipo de gráfico
            switch (options.getChartType()) {
                case LINE:
                case BAR:
                case AREA:
                    processSeriesData(executeResult, options, chartData);
                    break;
                case PIE:
                case FUNNEL:
                    processPieData(executeResult, options, chartData);
                    break;
                case SCATTER:
                    processScatterData(executeResult, options, chartData);
                    break;
                case HISTOGRAM:
                    processHistogramData(executeResult, options, chartData);
                    break;
                case HEATMAP:
                    processHeatmapData(executeResult, options, chartData);
                    break;
                case GAUGE:
                    processGaugeData(executeResult, options, chartData);
                    break;
                case RADAR:
                    processRadarData(executeResult, options, chartData);
                    break;
                default:
                    processSeriesData(executeResult, options, chartData);
            }
            
            // Aplicar configurações adicionais
            applyProcessingOptions(chartData, options);
            
            // Calcular estatísticas
            calculateStatistics(chartData);
            
            log.info("Dados processados para gráfico: {} pontos de dados, tipo: {}", 
                    chartData.getMetadata().getTotalDataPoints(), options.getChartType());
            
            return chartData;
            
        } catch (Exception e) {
            log.error("Erro ao processar dados para gráfico", e);
            return createErrorChartData(e.getMessage());
        }
    }

    /**
     * Processa dados para gráficos de série (linha, barra, área)
     */
    private void processSeriesData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        // Determinar colunas
        String xColumn = options.getXAxisColumn() != null ? options.getXAxisColumn() : headers.get(0).getName();
        String yColumn = options.getYAxisColumn() != null ? options.getYAxisColumn() : headers.get(1).getName();
        String groupColumn = options.getGroupByColumn();
        
        // Extrair labels (eixo X)
        Set<String> labelSet = new LinkedHashSet<>();
        for (Map<String, Object> row : dataList) {
            Object xValue = row.get(xColumn);
            if (xValue != null && !options.getExcludeValues().contains(xValue.toString())) {
                labelSet.add(formatValue(xValue, options));
            }
        }
        chartData.setLabels(new ArrayList<>(labelSet));
        
        // Processar séries de dados
        if (groupColumn != null && !groupColumn.isEmpty()) {
            // Dados agrupados - múltiplas séries
            Map<String, List<Object>> seriesMap = new HashMap<>();
            
            for (Map<String, Object> row : dataList) {
                String groupValue = String.valueOf(row.get(groupColumn));
                String xValue = formatValue(row.get(xColumn), options);
                Object yValue = row.get(yColumn);
                
                seriesMap.computeIfAbsent(groupValue, k -> {
                    List<Object> data = new ArrayList<>(Collections.nCopies(chartData.getLabels().size(), 0));
                    return data;
                });
                
                int index = chartData.getLabels().indexOf(xValue);
                if (index >= 0) {
                    seriesMap.get(groupValue).set(index, formatNumericValue(yValue, options));
                }
            }
            
            // Criar séries
            for (Map.Entry<String, List<Object>> entry : seriesMap.entrySet()) {
                ChartData.DataSeries series = new ChartData.DataSeries();
                series.setName(entry.getKey());
                series.setData(entry.getValue());
                series.setColor(options.getColorMapping().get(entry.getKey()));
                chartData.getSeries().add(series);
            }
        } else {
            // Dados simples - uma série
            ChartData.DataSeries series = new ChartData.DataSeries();
            series.setName(yColumn);
            
            List<Object> data = new ArrayList<>();
            for (String label : chartData.getLabels()) {
                Object value = dataList.stream()
                    .filter(row -> formatValue(row.get(xColumn), options).equals(label))
                    .map(row -> row.get(yColumn))
                    .findFirst()
                    .orElse(0);
                data.add(formatNumericValue(value, options));
            }
            
            series.setData(data);
            chartData.getSeries().add(series);
        }
        
        // Configurar metadados
        chartData.getMetadata().setXAxisTitle(xColumn);
        chartData.getMetadata().setYAxisTitle(yColumn);
        chartData.getMetadata().setAggregationUsed(options.getAggregationFunction());
    }

    /**
     * Processa dados para gráfico de pizza
     */
    private void processPieData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        String labelColumn = options.getXAxisColumn() != null ? options.getXAxisColumn() : headers.get(0).getName();
        String valueColumn = options.getYAxisColumn() != null ? options.getYAxisColumn() : headers.get(1).getName();
        
        ChartData.DataSeries series = new ChartData.DataSeries();
        series.setName("Data");
        
        List<String> labels = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        
        for (Map<String, Object> row : dataList) {
            Object labelValue = row.get(labelColumn);
            Object value = row.get(valueColumn);
            
            if (labelValue != null && value != null && !options.getExcludeValues().contains(labelValue.toString())) {
                labels.add(formatValue(labelValue, options));
                data.add(formatNumericValue(value, options));
            }
        }
        
        chartData.setLabels(labels);
        series.setData(data);
        chartData.getSeries().add(series);
    }

    /**
     * Processa dados para gráfico de dispersão
     */
    private void processScatterData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        String xColumn = options.getXAxisColumn() != null ? options.getXAxisColumn() : headers.get(0).getName();
        String yColumn = options.getYAxisColumn() != null ? options.getYAxisColumn() : headers.get(1).getName();
        
        ChartData.DataSeries series = new ChartData.DataSeries();
        series.setName("Scatter Data");
        
        List<Object> data = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            Object xValue = row.get(xColumn);
            Object yValue = row.get(yColumn);
            
            if (xValue != null && yValue != null) {
                Map<String, Object> point = new HashMap<>();
                point.put("x", formatNumericValue(xValue, options));
                point.put("y", formatNumericValue(yValue, options));
                data.add(point);
            }
        }
        
        series.setData(data);
        chartData.getSeries().add(series);
        
        chartData.getMetadata().setXAxisTitle(xColumn);
        chartData.getMetadata().setYAxisTitle(yColumn);
    }

    /**
     * Processa dados para histograma
     */
    private void processHistogramData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        String valueColumn = options.getXAxisColumn() != null ? options.getXAxisColumn() : headers.get(0).getName();
        
        // Extrair valores numéricos
        List<Double> values = dataList.stream()
            .map(row -> row.get(valueColumn))
            .filter(Objects::nonNull)
            .map(v -> Double.parseDouble(v.toString()))
            .collect(Collectors.toList());
        
        if (values.isEmpty()) {
            return;
        }
        
        // Calcular bins do histograma
        double min = values.stream().min(Double::compare).orElse(0.0);
        double max = values.stream().max(Double::compare).orElse(0.0);
        int binCount = Math.min(20, (int) Math.sqrt(values.size())); // Regra de Sturges modificada
        double binWidth = (max - min) / binCount;
        
        List<String> labels = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        
        for (int i = 0; i < binCount; i++) {
            double binStart = min + i * binWidth;
            double binEnd = binStart + binWidth;
            
            long count = values.stream()
                .filter(v -> v >= binStart && (i == binCount - 1 ? v <= binEnd : v < binEnd))
                .count();
            
            labels.add(String.format("%.2f-%.2f", binStart, binEnd));
            data.add(count);
        }
        
        chartData.setLabels(labels);
        
        ChartData.DataSeries series = new ChartData.DataSeries();
        series.setName("Frequency");
        series.setData(data);
        chartData.getSeries().add(series);
    }

    /**
     * Processa dados para mapa de calor
     */
    private void processHeatmapData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        // Implementação básica para mapa de calor
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        if (headers.size() < 3) {
            throw new IllegalArgumentException("Mapa de calor requer pelo menos 3 colunas (X, Y, Valor)");
        }
        
        String xColumn = headers.get(0).getName();
        String yColumn = headers.get(1).getName();
        String valueColumn = headers.get(2).getName();
        
        ChartData.DataSeries series = new ChartData.DataSeries();
        series.setName("Heatmap Data");
        
        List<Object> data = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            Map<String, Object> point = new HashMap<>();
            point.put("x", row.get(xColumn));
            point.put("y", row.get(yColumn));
            point.put("value", formatNumericValue(row.get(valueColumn), options));
            data.add(point);
        }
        
        series.setData(data);
        chartData.getSeries().add(series);
    }

    /**
     * Processa dados para medidor (gauge)
     */
    private void processGaugeData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        
        if (dataList.isEmpty()) {
            return;
        }
        
        // Para gauge, usamos apenas o primeiro valor
        Map<String, Object> firstRow = dataList.get(0);
        Object value = firstRow.values().iterator().next();
        
        ChartData.DataSeries series = new ChartData.DataSeries();
        series.setName("Gauge Value");
        series.setData(Arrays.asList(formatNumericValue(value, options)));
        chartData.getSeries().add(series);
    }

    /**
     * Processa dados para gráfico radar
     */
    private void processRadarData(ExecuteResult executeResult, ProcessingOptions options, ChartData chartData) {
        List<Map<String, Object>> dataList = executeResult.getDataList();
        List<Header> headers = executeResult.getHeaderList();
        
        // Para radar, cada coluna numérica é uma dimensão
        List<String> dimensions = headers.stream()
            .map(Header::getName)
            .collect(Collectors.toList());
        
        chartData.setLabels(dimensions);
        
        // Cada linha é uma série
        for (int i = 0; i < Math.min(dataList.size(), 5); i++) { // Limitar a 5 séries
            Map<String, Object> row = dataList.get(i);
            
            ChartData.DataSeries series = new ChartData.DataSeries();
            series.setName("Series " + (i + 1));
            
            List<Object> data = new ArrayList<>();
            for (String dimension : dimensions) {
                Object value = row.get(dimension);
                data.add(formatNumericValue(value, options));
            }
            
            series.setData(data);
            chartData.getSeries().add(series);
        }
    }

    /**
     * Aplica opções de processamento aos dados do gráfico
     */
    private void applyProcessingOptions(ChartData chartData, ProcessingOptions options) {
        // Limitar número de pontos de dados
        if (chartData.getLabels().size() > options.getMaxDataPoints()) {
            int step = chartData.getLabels().size() / options.getMaxDataPoints();
            List<String> sampledLabels = new ArrayList<>();
            
            for (int i = 0; i < chartData.getLabels().size(); i += step) {
                sampledLabels.add(chartData.getLabels().get(i));
            }
            
            chartData.setLabels(sampledLabels);
            
            // Aplicar amostragem às séries
            for (ChartData.DataSeries series : chartData.getSeries()) {
                List<Object> sampledData = new ArrayList<>();
                for (int i = 0; i < series.getData().size(); i += step) {
                    sampledData.add(series.getData().get(i));
                }
                series.setData(sampledData);
            }
        }
        
        // Ordenar dados se solicitado
        if (options.isSortData()) {
            sortChartData(chartData, options.getSortOrder());
        }
        
        // Configurar opções do gráfico
        chartData.getOptions().put("enableDataLabels", options.isEnableDataLabels());
        chartData.getOptions().put("enableLegend", options.isEnableLegend());
        chartData.getOptions().put("colorMapping", options.getColorMapping());
    }

    /**
     * Ordena dados do gráfico
     */
    private void sortChartData(ChartData chartData, String sortOrder) {
        if (chartData.getSeries().isEmpty()) {
            return;
        }
        
        // Criar índices ordenados baseados na primeira série
        List<Object> firstSeriesData = chartData.getSeries().get(0).getData();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < firstSeriesData.size(); i++) {
            indices.add(i);
        }
        
        indices.sort((i1, i2) -> {
            Object v1 = firstSeriesData.get(i1);
            Object v2 = firstSeriesData.get(i2);
            
            if (v1 instanceof Number && v2 instanceof Number) {
                double d1 = ((Number) v1).doubleValue();
                double d2 = ((Number) v2).doubleValue();
                int result = Double.compare(d1, d2);
                return "DESC".equals(sortOrder) ? -result : result;
            }
            
            return "DESC".equals(sortOrder) ? 
                v2.toString().compareTo(v1.toString()) : 
                v1.toString().compareTo(v2.toString());
        });
        
        // Reordenar labels
        List<String> sortedLabels = new ArrayList<>();
        for (Integer index : indices) {
            sortedLabels.add(chartData.getLabels().get(index));
        }
        chartData.setLabels(sortedLabels);
        
        // Reordenar todas as séries
        for (ChartData.DataSeries series : chartData.getSeries()) {
            List<Object> sortedData = new ArrayList<>();
            for (Integer index : indices) {
                sortedData.add(series.getData().get(index));
            }
            series.setData(sortedData);
        }
    }

    /**
     * Calcula estatísticas dos dados
     */
    private void calculateStatistics(ChartData chartData) {
        ChartData.ChartMetadata metadata = chartData.getMetadata();
        metadata.setTotalDataPoints(chartData.getLabels().size());
        
        if (!chartData.getSeries().isEmpty()) {
            ChartData.DataSeries firstSeries = chartData.getSeries().get(0);
            List<Double> numericValues = firstSeries.getData().stream()
                .filter(v -> v instanceof Number)
                .map(v -> ((Number) v).doubleValue())
                .collect(Collectors.toList());
            
            if (!numericValues.isEmpty()) {
                double sum = numericValues.stream().mapToDouble(Double::doubleValue).sum();
                double avg = sum / numericValues.size();
                double min = numericValues.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double max = numericValues.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                
                metadata.getStatistics().put("sum", sum);
                metadata.getStatistics().put("average", avg);
                metadata.getStatistics().put("min", min);
                metadata.getStatistics().put("max", max);
                metadata.getStatistics().put("count", numericValues.size());
            }
        }
    }

    /**
     * Formata valor para exibição
     */
    private String formatValue(Object value, ProcessingOptions options) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp) {
            return LocalDateTime.parse(value.toString()).format(DateTimeFormatter.ofPattern(options.getDateFormat()));
        }
        
        return value.toString();
    }

    /**
     * Formata valor numérico
     */
    private Object formatNumericValue(Object value, ProcessingOptions options) {
        if (value == null) {
            return 0;
        }
        
        if (value instanceof Number) {
            BigDecimal bd = new BigDecimal(value.toString());
            return bd.setScale(options.getDecimalPlaces(), RoundingMode.HALF_UP).doubleValue();
        }
        
        try {
            BigDecimal bd = new BigDecimal(value.toString());
            return bd.setScale(options.getDecimalPlaces(), RoundingMode.HALF_UP).doubleValue();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Cria dados de gráfico vazio
     */
    private ChartData createEmptyChartData(ChartType type) {
        ChartData chartData = new ChartData();
        chartData.setType(type);
        chartData.getMetadata().setTitle("Sem dados");
        chartData.getMetadata().setTotalDataPoints(0);
        return chartData;
    }

    /**
     * Cria dados de gráfico com erro
     */
    private ChartData createErrorChartData(String errorMessage) {
        ChartData chartData = new ChartData();
        chartData.setType(ChartType.BAR);
        chartData.getMetadata().setTitle("Erro: " + errorMessage);
        chartData.getMetadata().setTotalDataPoints(0);
        return chartData;
    }

    /**
     * Valida se os dados são adequados para o tipo de gráfico
     */
    public boolean validateDataForChartType(ExecuteResult executeResult, ChartType chartType) {
        if (executeResult == null || executeResult.getDataList() == null || executeResult.getDataList().isEmpty()) {
            return false;
        }
        
        List<Header> headers = executeResult.getHeaderList();
        
        switch (chartType) {
            case PIE:
            case FUNNEL:
                return headers.size() >= 2; // Precisa de label e valor
            case SCATTER:
                return headers.size() >= 2; // Precisa de X e Y
            case HEATMAP:
                return headers.size() >= 3; // Precisa de X, Y e valor
            case GAUGE:
                return headers.size() >= 1; // Precisa de pelo menos um valor
            default:
                return headers.size() >= 1; // Outros tipos são mais flexíveis
        }
    }

    /**
     * Sugere o melhor tipo de gráfico baseado nos dados
     */
    public ChartType suggestChartType(ExecuteResult executeResult) {
        if (executeResult == null || executeResult.getDataList() == null || executeResult.getDataList().isEmpty()) {
            return ChartType.BAR;
        }
        
        List<Header> headers = executeResult.getHeaderList();
        int numericColumns = 0;
        int textColumns = 0;
        
        for (Header header : headers) {
            if (isNumericType(header.getDataType())) {
                numericColumns++;
            } else {
                textColumns++;
            }
        }
        
        // Lógica de sugestão baseada na estrutura dos dados
        if (headers.size() == 2 && textColumns == 1 && numericColumns == 1) {
            return ChartType.PIE; // Uma categoria e um valor
        } else if (numericColumns >= 2) {
            return ChartType.SCATTER; // Múltiplos valores numéricos
        } else if (executeResult.getDataList().size() > 50) {
            return ChartType.LINE; // Muitos pontos de dados
        } else {
            return ChartType.BAR; // Padrão
        }
    }

    /**
     * Verifica se o tipo de dados é numérico
     */
    private boolean isNumericType(String dataType) {
        if (dataType == null) {
            return false;
        }
        
        String type = dataType.toLowerCase();
        return type.contains("int") || type.contains("decimal") || type.contains("float") || 
               type.contains("double") || type.contains("numeric") || type.contains("number");
    }
}