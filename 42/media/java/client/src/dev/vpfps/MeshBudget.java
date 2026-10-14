package dev.vpfps;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-frame time budget for Viewpoint's chunk-mesh rebuilds - the analogue
 * of PZ_Optimization's bakeScheduler, applied to Viewpoint's pipeline
 * (ChunkWalk -> ChunkBuilds.rebuild).
 *
 * Design: a thread-safe token bucket. Every frame the bucket is refilled
 * with `budgetMs` worth of time; every rebuild DEDUCTS ITS ACTUAL MEASURED
 * COST, so the budget self-calibrates against real mesh complexity instead
 * of a guess. Viewpoint may run its chunk walk on a worker thread, so the
 * refill (frame loop) and the spend (mesh thread) can race - the bucket
 * handles that safely. Skipped chunks are simply rebuilt on a later frame.
 *
 * When disabled (kill switch) everything passes through untouched.
 */
public final class MeshBudget {

    private static final long MICROS_PER_MS = 1_000L;

    /** Available mesh time in microseconds. */
    private static final AtomicLong tokens = new AtomicLong();
    private static volatile float budgetMs = Config.meshBudgetMs;

    private static final AtomicInteger allowed = new AtomicInteger();
    private static final AtomicInteger skipped  = new AtomicInteger();
    private static final AtomicInteger built    = new AtomicInteger();

    private static final Stats rebuildMs = new Stats(128);

    private MeshBudget() {}

    public static void setBudgetMs(float ms) {
        budgetMs = Math.max(0.1f, ms);
    }

    public static float budget() {
        return budgetMs;
    }

    /** Called once per frame from Patch_GameWindow (game/render thread). */
    public static void beginFrame() {
        if (!active()) {
            tokens.set(Long.MAX_VALUE / 4); // effectively unlimited
            return;
        }
        long refill = (long) (budgetMs * MICROS_PER_MS);
        long cap = refill * 3; // allow a small burst, not an unbounded debt
        tokens.updateAndGet(t -> Math.min(t + refill, cap));
    }

    /**
     * Called before a chunk-mesh rebuild. Returns false when this frame's
     * budget is spent - the caller (Patch_ChunkBuilds) then skips the
     * rebuild entirely; Viewpoint retries it on a later frame.
     */
    public static boolean tryBegin() {
        if (!active()) {
            allowed.incrementAndGet();
            return true;
        }
        if (tokens.get() <= 0L) {
            skipped.incrementAndGet();
            return false;
        }
        allowed.incrementAndGet();
        return true;
    }

    /** Called after the rebuild completed; deducts the measured cost. */
    public static void end(long startNs) {
        long micros = (System.nanoTime() - startNs) / 1_000L;
        if (active()) {
            tokens.updateAndGet(t -> Math.max(0L, t - micros));
        }
        rebuildMs.push(micros / 1000f);
        built.incrementAndGet();
    }

    private static boolean active() {
        return Config.enabled && Config.meshBudgetEnabled;
    }

    // ---- diagnostics for the overlay / bridge ----

    public static int skippedCount() { return skipped.get(); }
    public static int builtCount()   { return built.get(); }
    public static Stats stats()      { return rebuildMs; }
}
