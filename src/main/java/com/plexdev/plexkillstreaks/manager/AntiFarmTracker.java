package com.plexdev.plexkillstreaks.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class AntiFarmTracker {
    private static final int CAPACITY = 100000;
    private final long windowSeconds;
    private final int maxCountedKills;
    private final LongSupplier clock;
    private final Map<Pair, Window> accepted = new HashMap<>();
    private boolean saturated;
    private long nextCapacityCleanup = Long.MIN_VALUE;

    public AntiFarmTracker(long windowSeconds, int maxCountedKills, LongSupplier clock) {
        if (windowSeconds < 0 || maxCountedKills < 1) throw new IllegalArgumentException("Invalid anti-farming window or threshold.");
        this.windowSeconds = windowSeconds;
        this.maxCountedKills = maxCountedKills;
        this.clock = clock;
    }

    public boolean tryCount(UUID killer, UUID victim) {
        if (windowSeconds == 0) return true;
        long now = clock.getAsLong();
        Pair pair = new Pair(killer,victim);
        Window window = accepted.get(pair);
        if (window != null && now - window.start() < windowSeconds) {
            if (window.countedKills() >= maxCountedKills) return false;
            accepted.put(pair, new Window(window.start(), window.countedKills() + 1));
            return true;
        }
        if (window == null && accepted.size() >= CAPACITY) {
            if (now >= nextCapacityCleanup) cleanup();
            if (accepted.size() >= CAPACITY) { saturated = true; return false; }
        }
        accepted.put(pair, new Window(now, 1));
        return true;
    }

    public int cleanup() {
        long now = clock.getAsLong();
        int before = accepted.size();
        nextCapacityCleanup = Long.MAX_VALUE;
        var iterator = accepted.values().iterator();
        while (iterator.hasNext()) {
            long time = iterator.next().start();
            if (now - time >= windowSeconds) iterator.remove();
            else nextCapacityCleanup = Math.min(nextCapacityCleanup, time + windowSeconds);
        }
        if (accepted.size() < CAPACITY) saturated = false;
        return before - accepted.size();
    }
    public boolean isSaturated() { return saturated; }
    public int size() { return accepted.size(); }
    public void clear() { accepted.clear(); saturated = false; nextCapacityCleanup = Long.MIN_VALUE; }
    private record Pair(UUID killer, UUID victim) {}
    private record Window(long start, int countedKills) {}
}
