package osfx.kubert.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import osfx.kubert.service.Monitor;
import osfx.kubert.service.MonitoringException;

public final class PollingRunner implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PollingRunner.class.getName());

    private final Monitor monitor;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public PollingRunner(Monitor monitor, Duration interval) {
        this(
                monitor,
                interval,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "kubert-poller");
                    thread.setDaemon(false);
                    return thread;
                }));
    }

    PollingRunner(
            Monitor monitor,
            Duration interval,
            ScheduledExecutorService executor) {
        this.monitor = Objects.requireNonNull(monitor, "monitor is required");
        this.interval = Objects.requireNonNull(interval, "interval is required");
        this.executor = Objects.requireNonNull(executor, "executor is required");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("polling runner has already started");
        }
        executor.scheduleWithFixedDelay(
                this::checkSafely,
                0,
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void awaitTermination() throws InterruptedException {
        while (!executor.awaitTermination(1, TimeUnit.DAYS)) {
            // Continue waiting until a shutdown hook closes the runner.
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    void checkSafely() {
        try {
            monitor.check();
        } catch (MonitoringException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Scheduled image update check failed", exception);
        }
    }
}
