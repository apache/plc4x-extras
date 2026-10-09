package org.apache.plc4x.merlot.lector62x.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageRepository;
import org.apache.plc4x.merlot.lector62x.api.MerlotLecto620ShippingPackageService;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageDTO;
import org.apache.plc4x.merlot.lector62x.api.MerlotLector620ShippingPackageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageServiceImpl implements MerlotLecto620ShippingPackageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageServiceImpl.class);

    private static final long CYCLE_SECONDS = 30;
    private static final long BASE_DELAY_MS = 30_000L;
    private static final long MAX_DELAY_MS = 600_000L;   // 10 min

    /** Guía pendiente de confirmar, con su estado de reintento. */
    private static final class Pending {
        final MerlotLector620ShippingPackageDTO dto;
        final AtomicBoolean inFlight = new AtomicBoolean(false);
        volatile int attempts = 0;
        volatile long nextAttemptAt = 0;

        Pending(MerlotLector620ShippingPackageDTO dto) {
            this.dto = dto;
        }
    }

    private final MerlotLecto620ShippingPackageRepository repository;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String url;
    private volatile HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    public MerlotLecto620ShippingPackageServiceImpl(MerlotLecto620ShippingPackageRepository repository) {
        this.repository = repository;
    }

    // ---------------- Ciclo de vida ----------------

    public void init() {
        LOGGER.info(">>> init() llamado");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lector62x-retry");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::retryPending, 10, CYCLE_SECONDS, TimeUnit.SECONDS);
    }

    public void destroy() {
        LOGGER.info(">>> destroy() llamado");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    // ---------------- API del service ----------------

    @Override
    public boolean confirmDataReceiptFromExternalServer(MerlotLector620ShippingPackageResponse r) throws Exception {
        if (r == null) {
            throw new Exception("Los campos del json no pueden estar vacios");
        }
        return r.getResultado();
    }

    @Override
    public Queue<MerlotLector620ShippingPackageDTO> getGuidesNoProcess() {
        Queue<MerlotLector620ShippingPackageDTO> snapshot = new ConcurrentLinkedQueue<>();
        pending.values().forEach(p -> snapshot.add(p.dto));
        return snapshot;
    }

    @Override
    public boolean updateGuideStatusInDataRecord(String codGuide) throws Exception {
        if (repository.checkShippingGuide(codGuide)) {
            return repository.confirmWithShippingGuide(codGuide);
        }
        throw new Exception("No existe el registro con ese codigo de guia en la base de datos");
    }

    @Override
    public void setUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            this.url = url.trim();
        }
    }

    // ---------------- Envío ----------------

    @Override
    public void postEventPackage(String[] data) throws JsonProcessingException {
        MerlotLector620ShippingPackageDTO dto = new MerlotLector620ShippingPackageDTO(data);
        send(pending.computeIfAbsent(dto.getCodguia(), k -> new Pending(dto)));
    }

    private void send(Pending p) {
        final String target = this.url;
        final HttpClient client = this.httpClient;

        if (!p.inFlight.compareAndSet(false, true)) {
            return; // ya hay un envío en curso para esta guía
        }
        if (target == null || client == null) {
            LOGGER.warn("URL o HttpClient no inicializados; la guía {} se reintentará", p.dto.getCodguia());
            fail(p);
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(p.dto)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> processResponse(resp, p))
                    .exceptionally(t -> {
                        LOGGER.error("Error de red enviando la guía {}", p.dto.getCodguia(), t);
                        fail(p);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("No se pudo enviar la guía {}", p.dto.getCodguia(), e);
            fail(p);
        }
    }

    private void processResponse(HttpResponse<String> resp, Pending p) {
        final int status = resp.statusCode();
        final String codguia = p.dto.getCodguia();
        final String body = resp.body();
        LOGGER.info("Guía {} -> HTTP {} body: {}", codguia, status, body);

        try {
            if (status == 200 || status == 201) {
                MerlotLector620ShippingPackageResponse r
                        = mapper.readValue(body, MerlotLector620ShippingPackageResponse.class);
                if (r.getResultado()) {
                    LOGGER.info("Guía {} registrada en el servidor", codguia);
                    pending.remove(codguia);
                    updateGuideStatusInDataRecord(codguia);
                    return;
                }
                LOGGER.warn("Guía {} no registrada según el servidor", codguia);
            } else if (status == 400) {
                LOGGER.warn("Guía {} rechazada por datos inválidos (se reintentará): {}", codguia, body);
            } else {
                LOGGER.warn("HTTP {} para la guía {}", status, codguia);
            }
            fail(p);
        } catch (Exception e) {
            LOGGER.error("Respuesta no válida para la guía {}: {}", codguia, body, e);
            // Si ya se quitó de pending (éxito) no se reencola; si no, reintenta
            if (pending.containsKey(codguia)) {
                fail(p);
            }
        }
    }

    /** Programa el próximo intento con backoff exponencial (30 s ... 10 min). */
    private void fail(Pending p) {
        int n = ++p.attempts;
        long delay = Math.min(BASE_DELAY_MS * (1L << Math.min(n - 1, 5)), MAX_DELAY_MS);
        p.nextAttemptAt = System.currentTimeMillis() + delay;
        p.inFlight.set(false);
        LOGGER.info("Guía {} reintento #{} en {} s", p.dto.getCodguia(), n, delay / 1000);
    }

    // ---------------- Reintentos ----------------

    private void retryPending() {
        try {
            reloadFromDatabase();
            if (url == null || pending.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Pending p : pending.values()) {
                if (p.nextAttemptAt <= now && !p.inFlight.get()) {
                    send(p);
                }
            }
        } catch (Exception e) {
            // si una excepción escapa, el scheduler cancela las ejecuciones futuras
            LOGGER.error("Error en el ciclo de reintentos", e);
        }
    }

    /** La BD es la fuente de verdad: recupera guías sin confirmar tras un reinicio. */
    private void reloadFromDatabase() {
        try {
            for (MerlotLector620ShippingPackageDTO dto : repository.findUnconfirmed()) {
                pending.computeIfAbsent(dto.getCodguia(), k -> new Pending(dto));
            }
        } catch (Exception e) {
            LOGGER.warn("No se pudo recargar guías pendientes desde la BD: {}", e.getMessage());
        }
    }
}