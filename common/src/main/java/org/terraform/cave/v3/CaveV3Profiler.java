package org.terraform.cave.v3;

import org.jetbrains.annotations.NotNull;
import org.terraform.main.config.TConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class CaveV3Profiler {
    private static final ConcurrentHashMap<String, Stat> STATS = new ConcurrentHashMap<>();
    private static final Scope DISABLED_SCOPE = new Scope(null, 0L);

    private CaveV3Profiler() {
    }

    public static boolean isEnabled() {
        return TConfig.c != null && TConfig.c.DEVSTUFF_CAVE_V3_PROFILE;
    }

    public static @NotNull Scope start(@NotNull String key) {
        if (!isEnabled()) {
            return DISABLED_SCOPE;
        }
        return new Scope(key, System.nanoTime());
    }

    public static void recordEvent(@NotNull String key) {
        recordEvents(key, 1L);
    }

    public static void recordEvents(@NotNull String key, long count) {
        if (!isEnabled()) {
            return;
        }
        STATS.computeIfAbsent(key, ignored -> new Stat()).recordEvent(count);
    }

    public static boolean hasSamples() {
        return !STATS.isEmpty();
    }

    public static void clear() {
        STATS.clear();
    }

    public static @NotNull List<SectionSnapshot> snapshot() {
        List<SectionSnapshot> snapshots = new ArrayList<>(STATS.size());
        for (var entry : STATS.entrySet()) {
            snapshots.add(entry.getValue().snapshot(entry.getKey()));
        }
        snapshots.sort(Comparator.comparingLong(SectionSnapshot::totalNanos).reversed()
                                 .thenComparing(Comparator.comparingLong(SectionSnapshot::calls).reversed())
                                 .thenComparing(SectionSnapshot::key));
        return snapshots;
    }

    private static void recordDuration(@NotNull String key, long nanos) {
        if (!isEnabled()) {
            return;
        }
        STATS.computeIfAbsent(key, ignored -> new Stat()).recordDuration(nanos);
    }

    public record SectionSnapshot(@NotNull String key, long calls, long totalNanos, long maxNanos) {
        public double totalMillis() {
            return totalNanos / 1_000_000d;
        }

        public double averageMillis() {
            return calls == 0 ? 0d : (totalNanos / 1_000_000d) / calls;
        }

        public double maxMillis() {
            return maxNanos / 1_000_000d;
        }

        public boolean isTimed() {
            return totalNanos > 0L;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final String key;
        private final long startNanos;

        private Scope(String key, long startNanos) {
            this.key = key;
            this.startNanos = startNanos;
        }

        @Override
        public void close() {
            if (key == null) {
                return;
            }
            recordDuration(key, System.nanoTime() - startNanos);
        }
    }

    private static final class Stat {
        private final LongAdder calls = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private void recordEvent(long count) {
            calls.add(count);
        }

        private void recordDuration(long nanos) {
            calls.increment();
            totalNanos.add(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);
        }

        private @NotNull SectionSnapshot snapshot(@NotNull String key) {
            return new SectionSnapshot(key, calls.sum(), totalNanos.sum(), maxNanos.get());
        }
    }
}
