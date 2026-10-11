package org.apache.plc4x.merlot.lector62x.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Dictionary;
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
import org.osgi.service.cm.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MerlotLecto620ShippingPackageServiceImpl implements MerlotLecto620ShippingPackageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MerlotLecto620ShippingPackageServiceImpl.class);

    private static final long CYCLE_SECONDS = 30; //30 seconds
    private static final long BASE_DELAY_MS = 30_000L; //30 seconds
    private static final long MAX_DELAY_MS = 600_000L;   // 10 minute

    private final MerlotLecto620ShippingPackageRepository repository;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String url;
    private volatile HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    public MerlotLecto620ShippingPackageServiceImpl(MerlotLecto620ShippingPackageRepository repository) {
        this.repository = repository;
    }

    // ---------------- Life Cycle ----------------
    public void init() {
        LOGGER.info("Initializing the Lector620 Service Module");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public synchronized void onDataSourceReady() {
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            LOGGER.info("DataSource is ready. Starting retry scheduler.");
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "lector62x-retry");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::retryPending, 10, CYCLE_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void destroy() {
        LOGGER.info("Destroying the Lector620 Service Module");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    // ---------------- Service API ----------------
    @Override
    public boolean confirmDataReceiptFromExternalServer(MerlotLector620ShippingPackageResponse r) throws Exception {
        if (r == null) {
            throw new Exception("JSON fields cannot be empty");
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
        throw new Exception("There is no record with that reference code in the database.");
    }

    @Override
    public void setUrl(String url) {
        if (url != null && !url.trim().isEmpty()) {
            this.url = url.trim();
        }
    }

    // ---------------- Send Package ----------------
    @Override
    public void postEventPackage(String[] data) throws JsonProcessingException {
        MerlotLector620ShippingPackageDTO dto = new MerlotLector620ShippingPackageDTO(data);
        send(pending.computeIfAbsent(dto.getCodguia(), k -> new Pending(dto)));
    }

    private void send(Pending p) {
        final String target = this.url;
        final HttpClient client = this.httpClient;

        if (!p.inFlight.compareAndSet(false, true)) {
            return; // A shipment for this guide is already in progress
        }
        if (target == null || client == null) {
            LOGGER.info("URL or HttpClient not initialized; the {} will be retried", p.dto.getCodguia());
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
                        LOGGER.error("Network error while sending the guide {}", p.dto.getCodguia(), t);
                        fail(p);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.info("The shipping label could not be sent {}", p.dto.getCodguia(), e);
            fail(p);
        }
    }

    private void processResponse(HttpResponse<String> resp, Pending p) {
        final int status = resp.statusCode();
        final String codguia = p.dto.getCodguia();
        final String body = resp.body();
        LOGGER.info("Guide {} -> HTTP {} body: {}", codguia, status, body);

        try {
            if (status == 200 || status == 201) {
                MerlotLector620ShippingPackageResponse r
                        = mapper.readValue(body, MerlotLector620ShippingPackageResponse.class);
                if (r.getResultado()) {
                    LOGGER.info("Guide {} registered on the server", codguia);
                    pending.remove(codguia);
                    updateGuideStatusInDataRecord(codguia);
                    return;
                }
                LOGGER.info("Guide {} not registered according to the server", codguia);
            } else if (status == 400) {
                LOGGER.info("Guide {} rejected due to invalid data (will be retried): {}", codguia, body);
            } else {
                LOGGER.info("HTTP {} for the guide {}", status, codguia);
            }
            fail(p);
        } catch (Exception e) {
            LOGGER.info("Invalid answer for the guide {}: {}", codguia, body, e);
            if (pending.containsKey(codguia)) {
                fail(p);
            }
        }
    }

    /**
     * Schedule the next attempt with exponential backoff
     */
    private void fail(Pending p) {
        int n = ++p.attempts;
        long delay = Math.min(BASE_DELAY_MS * (1L << Math.min(n - 1, 5)), MAX_DELAY_MS);
        p.nextAttemptAt = System.currentTimeMillis() + delay;
        p.inFlight.set(false);
        LOGGER.info("Guide {} retry #{} in {} s", p.dto.getCodguia(), n, delay / 1000);
    }

    /**
     * Forwarding Records to an External Server. Belongs to the SchedulerService
     */
    private void retryPending() {
        LOGGER.info("Enabling the message forwarding process to the server and "
                + "checking to see if there are any messages that need to be forwarded");
        try {

            reloadFromDatabase();
            if (url == null || pending.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            for (Pending p : pending.values()) {
                if (p.nextAttemptAt <= now && !p.inFlight.get()) {
                    LOGGER.info("Resending the message to the server with the guide code", p.dto.getCodguia());
                    send(p);
                }
            }
        } catch (Exception e) {
            LOGGER.info("Error in the retry cycle", e);
        }
    }

    /**
     * Reloading Pending Records from the Database
     */
    private void reloadFromDatabase() {
        LOGGER.info("The database is queried for unconfirmed records");
        try {
            for (MerlotLector620ShippingPackageDTO dto : repository.findUnconfirmed()) {
                pending.computeIfAbsent(dto.getCodguia(), k -> new Pending(dto));
            }
        } catch (Exception e) {
            LOGGER.info("Pending guides could not be reloaded from the database: {}", e.getMessage());
        }
    }


    /**
     * Guide pending confirmation, with its retry status.
     */
    private static final class Pending {

        final MerlotLector620ShippingPackageDTO dto;
        final AtomicBoolean inFlight = new AtomicBoolean(false);
        volatile int attempts = 0;
        volatile long nextAttemptAt = 0;

        Pending(MerlotLector620ShippingPackageDTO dto) {
            this.dto = dto;
        }
    }

}
