package com.mavworks.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Per-account rolling limits for the single-instance pilot. No message text is retained. */
@Service
public class SpamLimitService {
    public enum Action { MESSAGE, CONVERSATION, REPORT }
    private record Key(Long accountId, Action action) {}
    private final Map<Key, ArrayDeque<Long>> events = new HashMap<>();
    private final int messages, conversations, reports;
    private final LongSupplier clock;
    private static final long MINUTE = Duration.ofMinutes(1).toNanos();

    @Autowired
    public SpamLimitService(
            @Value("${mavworks.spam.messages-per-minute:20}") int messages,
            @Value("${mavworks.spam.conversations-per-minute:5}") int conversations,
            @Value("${mavworks.spam.reports-per-ten-minutes:3}") int reports) {
        this(messages, conversations, reports, System::nanoTime);
    }

    SpamLimitService(int messages, int conversations, int reports, LongSupplier clock) {
        if (messages < 1 || conversations < 1 || reports < 1)
            throw new IllegalArgumentException("Spam limits must be positive.");
        this.messages = messages;
        this.conversations = conversations;
        this.reports = reports;
        this.clock = clock;
    }

    public synchronized void check(Long accountId, Action action) {
        java.util.Objects.requireNonNull(accountId);
        java.util.Objects.requireNonNull(action);
        long now = clock.getAsLong();
        // Remove idle accounts without retaining their counters for the life of the app.
        events.entrySet().removeIf(entry -> now - entry.getValue().getLast() >= 10 * MINUTE);
        long window = action == Action.REPORT ? 10 * MINUTE : MINUTE;
        int limit = switch (action) {
            case MESSAGE -> messages;
            case CONVERSATION -> conversations;
            case REPORT -> reports;
        };
        var history = events.computeIfAbsent(new Key(accountId, action), key -> new ArrayDeque<>());
        while (!history.isEmpty() && now - history.getFirst() >= window) history.removeFirst();
        if (history.size() >= limit) {
            long seconds = Math.max(1, (window - (now - history.getFirst()) + 999_999_999L) / 1_000_000_000L);
            String activity = switch (action) {
                case MESSAGE -> "sending another message";
                case CONVERSATION -> "starting another conversation";
                case REPORT -> "submitting another report";
            };
            throw new IllegalArgumentException("Please wait " + seconds + " seconds before " + activity + ".");
        }
        history.addLast(now);
    }
}
