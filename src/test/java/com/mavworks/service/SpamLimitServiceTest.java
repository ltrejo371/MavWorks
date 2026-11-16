package com.mavworks.service;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.mavworks.service.SpamLimitService.Action.*;

class SpamLimitServiceTest {
    @Test void rollingWindowReopensWithoutRejectedAttemptsExtendingWait() {
        var time = new AtomicLong();
        var limits = new SpamLimitService(2, 1, 1, time::get);
        limits.check(1L, MESSAGE);
        time.set(Duration.ofSeconds(30).toNanos());
        limits.check(1L, MESSAGE);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> limits.check(1L, MESSAGE))
                .getMessage().contains("30 seconds"));
        time.set(Duration.ofSeconds(60).toNanos());
        limits.check(1L, MESSAGE);
        assertThrows(IllegalArgumentException.class, () -> limits.check(1L, MESSAGE));
        time.set(Duration.ofSeconds(90).toNanos());
        assertDoesNotThrow(() -> limits.check(1L, MESSAGE));
    }

    @Test void accountsAndActionsAreIndependentAndReportsWaitTenMinutes() {
        var time = new AtomicLong();
        var limits = new SpamLimitService(1, 1, 1, time::get);
        limits.check(1L, MESSAGE);
        limits.check(2L, MESSAGE);
        limits.check(1L, CONVERSATION);
        limits.check(1L, REPORT);
        time.set(Duration.ofMinutes(1).toNanos());
        limits.check(1L, MESSAGE);
        assertThrows(IllegalArgumentException.class, () -> limits.check(1L, REPORT));
        time.set(Duration.ofMinutes(10).toNanos());
        assertDoesNotThrow(() -> limits.check(1L, REPORT));
    }

    @Test void concurrentRequestsCannotExceedLimit() throws Exception {
        var limits = new SpamLimitService(20, 5, 3, () -> 0L);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 50; i++) jobs.add(() -> {
                try { limits.check(1L, MESSAGE); return true; }
                catch (IllegalArgumentException ex) { return false; }
            });
            int accepted = 0;
            for (var result : pool.invokeAll(jobs)) if (result.get()) accepted++;
            assertEquals(20, accepted);
        }
    }

    @Test void invalidConfigurationFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new SpamLimitService(0, 5, 3));
        assertThrows(IllegalArgumentException.class, () -> new SpamLimitService(20, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new SpamLimitService(20, 5, 0));
    }
}
