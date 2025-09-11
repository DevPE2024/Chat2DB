package ai.chat2db.spi.sql;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Sistema de streaming de dados em tempo real para Chat2DB
 * Responsável por transmitir dados em tempo real com suporte a filtros, transformações e múltiplos consumidores
 */
public class RealTimeDataStreaming {
    
    private final ScheduledExecutorService scheduler;
    private final ExecutorService eventProcessor;
    private final Map<String, DataStream> activeStreams;
    private final StreamMetrics metrics;
    private final AtomicBoolean isRunning;
    
    public RealTimeDataStreaming() {
        this.scheduler = Executors.newScheduledThreadPool(5);
        this.eventProcessor = Executors.newFixedThreadPool(10);
        this.activeStreams = new ConcurrentHashMap<>();
        this.metrics = new StreamMetrics();
        this.isRunning = new AtomicBoolean(true);
    }
    
    /**
     * Tipos de stream suportados
     */
    public enum StreamType {
        QUERY_RESULTS,      // Resultados de consulta
        DATABASE_CHANGES,   // Mudanças no banco de dados
        PERFORMANCE_METRICS,// Métricas de performance
        USER_ACTIVITY,      // Atividade do usuário
        SYSTEM_EVENTS,      // Eventos do sistema
        CUSTOM             // Stream customizado
    }
    
    /**
     * Modos de entrega
     */
    public enum DeliveryMode {
        PUSH,              // Push para consumidores
        PULL,              // Pull pelos consumidores
        HYBRID             // Híbrido
    }
    
    /**
     * Configuração do stream
     */
    public static class StreamConfig {
        private String streamId;
        private StreamType type;
        private DeliveryMode deliveryMode;
        private long intervalMs;
        private int bufferSize;
        private boolean enableFiltering;
        private boolean enableTransformation;
        private boolean enableBatching;
        private int batchSize;
        private long batchTimeoutMs;
        private Map<String, Object> parameters;
        private List<String> allowedConsumers;
        private boolean persistData;
        private int maxRetries;
        private long retryDelayMs;
        
        public StreamConfig() {
            this.intervalMs = 1000; // 1 segundo
            this.bufferSize = 1000;
            this.enableFiltering = true;
            this.enableTransformation = true;
            this.enableBatching = false;
            this.batchSize = 10;
            this.batchTimeoutMs = 5000;
            this.parameters = new HashMap<>();
            this.allowedConsumers = new ArrayList<>();
            this.persistData = false;
            this.maxRetries = 3;
            this.retryDelayMs = 1000;
        }
        
        // Getters e Setters
        public String getStreamId() { return streamId; }
        public void setStreamId(String streamId) { this.streamId = streamId; }
        
        public StreamType getType() { return type; }
        public void setType(StreamType type) { this.type = type; }
        
        public DeliveryMode getDeliveryMode() { return deliveryMode; }
        public void setDeliveryMode(DeliveryMode deliveryMode) { this.deliveryMode = deliveryMode; }
        
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        
        public int getBufferSize() { return bufferSize; }
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
        
        public boolean isEnableFiltering() { return enableFiltering; }
        public void setEnableFiltering(boolean enableFiltering) { this.enableFiltering = enableFiltering; }
        
        public boolean isEnableTransformation() { return enableTransformation; }
        public void setEnableTransformation(boolean enableTransformation) { this.enableTransformation = enableTransformation; }
        
        public boolean isEnableBatching() { return enableBatching; }
        public void setEnableBatching(boolean enableBatching) { this.enableBatching = enableBatching; }
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public long getBatchTimeoutMs() { return batchTimeoutMs; }
        public void setBatchTimeoutMs(long batchTimeoutMs) { this.batchTimeoutMs = batchTimeoutMs; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
        
        public List<String> getAllowedConsumers() { return allowedConsumers; }
        public void setAllowedConsumers(List<String> allowedConsumers) { this.allowedConsumers = allowedConsumers; }
        
        public boolean isPersistData() { return persistData; }
        public void setPersistData(boolean persistData) { this.persistData = persistData; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
    
    /**
     * Evento de dados
     */
    public static class DataEvent {
        private String eventId;
        private String streamId;
        private StreamType type;
        private Map<String, Object> data;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
        private String source;
        
        public DataEvent() {
            this.eventId = UUID.randomUUID().toString();
            this.timestamp = LocalDateTime.now();
            this.data = new HashMap<>();
            this.metadata = new HashMap<>();
        }
        
        // Getters e Setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
        
        public String getStreamId() { return streamId; }
        public void setStreamId(String streamId) { this.streamId = streamId; }
        
        public StreamType getType() { return type; }
        public void setType(StreamType type) { this.type = type; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
    
    /**
     * Consumidor de stream
     */
    public interface StreamConsumer {
        void onData(DataEvent event);
        void onError(String streamId, Exception error);
        void onStreamClosed(String streamId);
        String getConsumerId();
    }
    
    /**
     * Filtro de dados
     */
    public interface DataFilter extends Predicate<DataEvent> {
        String getFilterId();
        String getDescription();
    }
    
    /**
     * Transformador de dados
     */
    public interface DataTransformer {
        DataEvent transform(DataEvent event);
        String getTransformerId();
        String getDescription();
    }
    
    /**
     * Stream de dados
     */
    private class DataStream {
        private final StreamConfig config;
        private final BlockingQueue<DataEvent> buffer;
        private final Set<StreamConsumer> consumers;
        private final List<DataFilter> filters;
        private final List<DataTransformer> transformers;
        private final AtomicBoolean active;
        private final AtomicLong eventCount;
        private ScheduledFuture<?> scheduledTask;
        private final List<DataEvent> batch;
        private LocalDateTime lastBatchTime;
        
        public DataStream(StreamConfig config) {
            this.config = config;
            this.buffer = new ArrayBlockingQueue<>(config.getBufferSize());
            this.consumers = ConcurrentHashMap.newKeySet();
            this.filters = new ArrayList<>();
            this.transformers = new ArrayList<>();
            this.active = new AtomicBoolean(true);
            this.eventCount = new AtomicLong(0);
            this.batch = new ArrayList<>();
            this.lastBatchTime = LocalDateTime.now();
        }
        
        public void start() {
            if (config.getDeliveryMode() == DeliveryMode.PUSH || config.getDeliveryMode() == DeliveryMode.HYBRID) {
                scheduledTask = scheduler.scheduleAtFixedRate(
                    this::processEvents,
                    0,
                    config.getIntervalMs(),
                    TimeUnit.MILLISECONDS
                );
            }
        }
        
        public void stop() {
            active.set(false);
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            
            // Processar eventos restantes
            processEvents();
            
            // Notificar consumidores
            consumers.forEach(consumer -> {
                try {
                    consumer.onStreamClosed(config.getStreamId());
                } catch (Exception e) {
                    // Log error
                }
            });
        }
        
        public boolean addEvent(DataEvent event) {
            if (!active.get()) {
                return false;
            }
            
            event.setStreamId(config.getStreamId());
            
            // Aplicar filtros
            if (config.isEnableFiltering()) {
                for (DataFilter filter : filters) {
                    if (!filter.test(event)) {
                        return false;
                    }
                }
            }
            
            // Aplicar transformações
            if (config.isEnableTransformation()) {
                for (DataTransformer transformer : transformers) {
                    event = transformer.transform(event);
                    if (event == null) {
                        return false;
                    }
                }
            }
            
            boolean added = buffer.offer(event);
            if (added) {
                eventCount.incrementAndGet();
                metrics.recordEvent(config.getStreamId());
            }
            
            return added;
        }
        
        private void processEvents() {
            if (!active.get()) {
                return;
            }
            
            List<DataEvent> events = new ArrayList<>();
            buffer.drainTo(events);
            
            if (events.isEmpty()) {
                return;
            }
            
            if (config.isEnableBatching()) {
                processBatchedEvents(events);
            } else {
                processIndividualEvents(events);
            }
        }
        
        private void processBatchedEvents(List<DataEvent> events) {
            batch.addAll(events);
            
            boolean shouldSendBatch = batch.size() >= config.getBatchSize() ||
                LocalDateTime.now().isAfter(lastBatchTime.plusNanos(config.getBatchTimeoutMs() * 1_000_000));
            
            if (shouldSendBatch && !batch.isEmpty()) {
                List<DataEvent> batchToSend = new ArrayList<>(batch);
                batch.clear();
                lastBatchTime = LocalDateTime.now();
                
                deliverBatch(batchToSend);
            }
        }
        
        private void processIndividualEvents(List<DataEvent> events) {
            for (DataEvent event : events) {
                deliverEvent(event);
            }
        }
        
        private void deliverEvent(DataEvent event) {
            for (StreamConsumer consumer : consumers) {
                if (isConsumerAllowed(consumer.getConsumerId())) {
                    eventProcessor.submit(() -> {
                        try {
                            consumer.onData(event);
                            metrics.recordDelivery(config.getStreamId(), consumer.getConsumerId());
                        } catch (Exception e) {
                            consumer.onError(config.getStreamId(), e);
                            metrics.recordError(config.getStreamId(), consumer.getConsumerId());
                        }
                    });
                }
            }
        }
        
        private void deliverBatch(List<DataEvent> events) {
            for (StreamConsumer consumer : consumers) {
                if (isConsumerAllowed(consumer.getConsumerId())) {
                    eventProcessor.submit(() -> {
                        try {
                            for (DataEvent event : events) {
                                consumer.onData(event);
                            }
                            metrics.recordBatchDelivery(config.getStreamId(), consumer.getConsumerId(), events.size());
                        } catch (Exception e) {
                            consumer.onError(config.getStreamId(), e);
                            metrics.recordError(config.getStreamId(), consumer.getConsumerId());
                        }
                    });
                }
            }
        }
        
        private boolean isConsumerAllowed(String consumerId) {
            return config.getAllowedConsumers().isEmpty() || config.getAllowedConsumers().contains(consumerId);
        }
        
        public void addConsumer(StreamConsumer consumer) {
            consumers.add(consumer);
        }
        
        public void removeConsumer(StreamConsumer consumer) {
            consumers.remove(consumer);
        }
        
        public void addFilter(DataFilter filter) {
            filters.add(filter);
        }
        
        public void removeFilter(String filterId) {
            filters.removeIf(filter -> filter.getFilterId().equals(filterId));
        }
        
        public void addTransformer(DataTransformer transformer) {
            transformers.add(transformer);
        }
        
        public void removeTransformer(String transformerId) {
            transformers.removeIf(transformer -> transformer.getTransformerId().equals(transformerId));
        }
        
        public StreamConfig getConfig() {
            return config;
        }
        
        public long getEventCount() {
            return eventCount.get();
        }
        
        public int getConsumerCount() {
            return consumers.size();
        }
        
        public int getBufferSize() {
            return buffer.size();
        }
        
        public boolean isActive() {
            return active.get();
        }
    }
    
    /**
     * Métricas do stream
     */
    private static class StreamMetrics {
        private final Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> deliveryCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> batchCounts = new ConcurrentHashMap<>();
        
        public void recordEvent(String streamId) {
            eventCounts.computeIfAbsent(streamId, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordDelivery(String streamId, String consumerId) {
            String key = streamId + ":" + consumerId;
            deliveryCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordError(String streamId, String consumerId) {
            String key = streamId + ":" + consumerId;
            errorCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }
        
        public void recordBatchDelivery(String streamId, String consumerId, int batchSize) {
            String key = streamId + ":" + consumerId;
            batchCounts.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(batchSize);
        }
        
        public Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            Map<String, Long> events = eventCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("events_by_stream", events);
            
            Map<String, Long> deliveries = deliveryCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("deliveries_by_consumer", deliveries);
            
            Map<String, Long> errors = errorCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("errors_by_consumer", errors);
            
            Map<String, Long> batches = batchCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
            metrics.put("batch_deliveries_by_consumer", batches);
            
            return metrics;
        }
    }
    
    /**
     * Cria um novo stream
     */
    public String createStream(StreamConfig config) {
        validateConfig(config);
        
        if (config.getStreamId() == null) {
            config.setStreamId(UUID.randomUUID().toString());
        }
        
        DataStream stream = new DataStream(config);
        activeStreams.put(config.getStreamId(), stream);
        stream.start();
        
        return config.getStreamId();
    }
    
    /**
     * Remove um stream
     */
    public boolean removeStream(String streamId) {
        DataStream stream = activeStreams.remove(streamId);
        if (stream != null) {
            stream.stop();
            return true;
        }
        return false;
    }
    
    /**
     * Adiciona evento ao stream
     */
    public boolean publishEvent(String streamId, DataEvent event) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            return stream.addEvent(event);
        }
        return false;
    }
    
    /**
     * Adiciona evento com dados simples
     */
    public boolean publishData(String streamId, Map<String, Object> data) {
        DataEvent event = new DataEvent();
        event.setData(data);
        return publishEvent(streamId, event);
    }
    
    /**
     * Adiciona consumidor ao stream
     */
    public boolean addConsumer(String streamId, StreamConsumer consumer) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.addConsumer(consumer);
            return true;
        }
        return false;
    }
    
    /**
     * Remove consumidor do stream
     */
    public boolean removeConsumer(String streamId, StreamConsumer consumer) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.removeConsumer(consumer);
            return true;
        }
        return false;
    }
    
    /**
     * Adiciona filtro ao stream
     */
    public boolean addFilter(String streamId, DataFilter filter) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.addFilter(filter);
            return true;
        }
        return false;
    }
    
    /**
     * Remove filtro do stream
     */
    public boolean removeFilter(String streamId, String filterId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.removeFilter(filterId);
            return true;
        }
        return false;
    }
    
    /**
     * Adiciona transformador ao stream
     */
    public boolean addTransformer(String streamId, DataTransformer transformer) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.addTransformer(transformer);
            return true;
        }
        return false;
    }
    
    /**
     * Remove transformador do stream
     */
    public boolean removeTransformer(String streamId, String transformerId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.removeTransformer(transformerId);
            return true;
        }
        return false;
    }
    
    /**
     * Obtém informações do stream
     */
    public Map<String, Object> getStreamInfo(String streamId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream == null) {
            return null;
        }
        
        Map<String, Object> info = new HashMap<>();
        info.put("stream_id", streamId);
        info.put("type", stream.getConfig().getType().toString());
        info.put("delivery_mode", stream.getConfig().getDeliveryMode().toString());
        info.put("active", stream.isActive());
        info.put("event_count", stream.getEventCount());
        info.put("consumer_count", stream.getConsumerCount());
        info.put("buffer_size", stream.getBufferSize());
        info.put("max_buffer_size", stream.getConfig().getBufferSize());
        info.put("interval_ms", stream.getConfig().getIntervalMs());
        info.put("batching_enabled", stream.getConfig().isEnableBatching());
        
        return info;
    }
    
    /**
     * Lista todos os streams ativos
     */
    public List<String> listActiveStreams() {
        return new ArrayList<>(activeStreams.keySet());
    }
    
    /**
     * Obtém métricas gerais
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> generalMetrics = new HashMap<>();
        generalMetrics.put("active_streams", activeStreams.size());
        generalMetrics.put("total_consumers", activeStreams.values().stream()
            .mapToInt(DataStream::getConsumerCount)
            .sum());
        generalMetrics.put("total_events", activeStreams.values().stream()
            .mapToLong(DataStream::getEventCount)
            .sum());
        
        Map<String, Object> streamMetrics = metrics.getMetrics();
        generalMetrics.putAll(streamMetrics);
        
        return generalMetrics;
    }
    
    /**
     * Pausa um stream
     */
    public boolean pauseStream(String streamId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null && stream.isActive()) {
            if (stream.scheduledTask != null) {
                stream.scheduledTask.cancel(false);
            }
            return true;
        }
        return false;
    }
    
    /**
     * Resume um stream
     */
    public boolean resumeStream(String streamId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null && stream.isActive()) {
            stream.start();
            return true;
        }
        return false;
    }
    
    /**
     * Limpa buffer de um stream
     */
    public boolean clearStreamBuffer(String streamId) {
        DataStream stream = activeStreams.get(streamId);
        if (stream != null) {
            stream.buffer.clear();
            return true;
        }
        return false;
    }
    
    /**
     * Valida configuração do stream
     */
    private void validateConfig(StreamConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuração do stream não pode ser nula");
        }
        
        if (config.getType() == null) {
            throw new IllegalArgumentException("Tipo do stream deve ser especificado");
        }
        
        if (config.getDeliveryMode() == null) {
            throw new IllegalArgumentException("Modo de entrega deve ser especificado");
        }
        
        if (config.getIntervalMs() <= 0) {
            throw new IllegalArgumentException("Intervalo deve ser maior que zero");
        }
        
        if (config.getBufferSize() <= 0) {
            throw new IllegalArgumentException("Tamanho do buffer deve ser maior que zero");
        }
    }
    
    /**
     * Implementações de filtros comuns
     */
    public static class CommonFilters {
        
        public static DataFilter byEventType(StreamType type) {
            return new DataFilter() {
                @Override
                public boolean test(DataEvent event) {
                    return event.getType() == type;
                }
                
                @Override
                public String getFilterId() {
                    return "event_type_" + type.name();
                }
                
                @Override
                public String getDescription() {
                    return "Filtra eventos por tipo: " + type.name();
                }
            };
        }
        
        public static DataFilter byDataField(String field, Object value) {
            return new DataFilter() {
                @Override
                public boolean test(DataEvent event) {
                    Object fieldValue = event.getData().get(field);
                    return Objects.equals(fieldValue, value);
                }
                
                @Override
                public String getFilterId() {
                    return "data_field_" + field + "_" + value;
                }
                
                @Override
                public String getDescription() {
                    return "Filtra por campo " + field + " = " + value;
                }
            };
        }
        
        public static DataFilter bySource(String source) {
            return new DataFilter() {
                @Override
                public boolean test(DataEvent event) {
                    return Objects.equals(event.getSource(), source);
                }
                
                @Override
                public String getFilterId() {
                    return "source_" + source;
                }
                
                @Override
                public String getDescription() {
                    return "Filtra por fonte: " + source;
                }
            };
        }
    }
    
    /**
     * Implementações de transformadores comuns
     */
    public static class CommonTransformers {
        
        public static DataTransformer addTimestamp() {
            return new DataTransformer() {
                @Override
                public DataEvent transform(DataEvent event) {
                    event.getMetadata().put("processed_at", LocalDateTime.now());
                    return event;
                }
                
                @Override
                public String getTransformerId() {
                    return "add_timestamp";
                }
                
                @Override
                public String getDescription() {
                    return "Adiciona timestamp de processamento";
                }
            };
        }
        
        public static DataTransformer enrichWithMetadata(Map<String, Object> metadata) {
            return new DataTransformer() {
                @Override
                public DataEvent transform(DataEvent event) {
                    event.getMetadata().putAll(metadata);
                    return event;
                }
                
                @Override
                public String getTransformerId() {
                    return "enrich_metadata";
                }
                
                @Override
                public String getDescription() {
                    return "Enriquece com metadados adicionais";
                }
            };
        }
        
        public static DataTransformer formatData(String field, String format) {
            return new DataTransformer() {
                @Override
                public DataEvent transform(DataEvent event) {
                    Object value = event.getData().get(field);
                    if (value != null) {
                        // Implementar formatação baseada no formato especificado
                        String formattedValue = String.format(format, value);
                        event.getData().put(field, formattedValue);
                    }
                    return event;
                }
                
                @Override
                public String getTransformerId() {
                    return "format_" + field;
                }
                
                @Override
                public String getDescription() {
                    return "Formata campo " + field + " com formato " + format;
                }
            };
        }
    }
    
    /**
     * Finaliza o sistema de streaming
     */
    public void shutdown() {
        isRunning.set(false);
        
        // Parar todos os streams
        activeStreams.values().forEach(DataStream::stop);
        activeStreams.clear();
        
        // Finalizar executors
        scheduler.shutdown();
        eventProcessor.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!eventProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                eventProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            eventProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Verifica se o sistema está rodando
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}