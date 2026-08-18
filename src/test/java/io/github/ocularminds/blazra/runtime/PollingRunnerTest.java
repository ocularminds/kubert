package io.github.ocularminds.blazra.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import io.github.ocularminds.blazra.service.Monitor;
import io.github.ocularminds.blazra.service.MonitoringException;
import io.github.ocularminds.blazra.service.UpdateResult;

class PollingRunnerTest {
    @Test
    void schedulesAtFixedDelayAndClosesExecutor() {
        Monitor monitor = mock(Monitor.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        PollingRunner runner = new PollingRunner(monitor, Duration.ofSeconds(30), executor);

        runner.start();
        verify(executor).scheduleWithFixedDelay(
                any(Runnable.class),
                eq(0L),
                eq(30_000L),
                eq(TimeUnit.MILLISECONDS));
        assertThrows(IllegalStateException.class, runner::start);

        runner.close();
        verify(executor).shutdownNow();
    }

    @Test
    void containsScheduledFailures() throws Exception {
        Monitor monitor = mock(Monitor.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        PollingRunner runner = new PollingRunner(monitor, Duration.ofSeconds(1), executor);

        doThrow(new MonitoringException("failed", new RuntimeException())).when(monitor).check();
        runner.checkSafely();
        doThrow(new IllegalStateException("failed")).when(monitor).check();
        runner.checkSafely();
    }

    @Test
    void awaitsTerminationUntilExecutorStops() throws Exception {
        Monitor monitor = () -> UpdateResult.NO_UPDATE;
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.DAYS)))
                .thenReturn(false)
                .thenReturn(true);
        PollingRunner runner = new PollingRunner(monitor, Duration.ofSeconds(1), executor);

        runner.awaitTermination();
        verify(executor, org.mockito.Mockito.times(2))
                .awaitTermination(1, TimeUnit.DAYS);
    }

    @Test
    void rejectsInvalidConstruction() {
        Monitor monitor = () -> UpdateResult.NO_UPDATE;
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PollingRunner(monitor, Duration.ZERO, executor));
    }

    @Test
    void defaultExecutorRunsChecks() throws Exception {
        CountDownLatch checked = new CountDownLatch(1);
        Monitor monitor = () -> {
            checked.countDown();
            return UpdateResult.NO_UPDATE;
        };
        try (PollingRunner runner = new PollingRunner(monitor, Duration.ofMillis(10))) {
            runner.start();
            assertTrue(checked.await(1, TimeUnit.SECONDS));
        }
    }
}
