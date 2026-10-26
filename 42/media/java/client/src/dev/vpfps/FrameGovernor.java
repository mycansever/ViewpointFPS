package dev.vpfps;

/**
 * The adaptive quality governor v3 — now driving Viewpoint's OWN settings.
 *
 * Measured inputs:
 *   - frame-time distribution from Patch_GameWindow (mean / p90 / p99)
 *   - Viewpoint render-pass share from Patch_FP (GPU feed)
 *   - Viewpoint world-capture share from Patch_FPCapture (game thread)
 *   - Viewpoint's own GPU utilization via GpuStats (GpuBusy.percent())
 *
 * Two independent factor ladders, applied through ViewpointKnobs inside
 * Viewpoint's declared setting ranges (factor 1.0 = stock):
 *
 *   cpuFactor -> ChunkBuilds.BUILD_MAX / MAX_COOKING, ChunkWalk.AT_ONCE,
 *                FarWorld.BUDGET / JOBS, FarShell.JOBS, FloorBakes.BUDGET,
 *                ShellGround.BUDGET, FarGpu.UPLOAD_MB / CELL_UPLOAD_MB
 *   gpuFactor -> FarWorld.REACH (draw distance) / DETAIL_1 / DETAIL_2 /
 *                DETAIL_4, Corpses.LIMIT
 *
 * Attribution: GPU-bound (Viewpoint's GpuBusy >= 55% or our render-share
 * >= 45%) lowers the GPU ladder first; game-thread-bound lowers the CPU
 * ladder first. The invisible CPU knobs always go before anything the
 * eye can see. Descending is immediate; climbing needs 3 stable windows.
 * factor 1.0 restores Viewpoint's stock values exactly, and a stock value
 * the user raised in Viewpoint's own UI is never fought.
 */
public final class FrameGovernor {

    private static final int FRAME_HISTORY = 240;
    private static final int DECISION_INTERVAL_MS = 500;
    private static final int COOLDOWN_MS = 1000;
    private static final int STABLE_WINDOWS_TO_CLIMB = 3;
    private static final int WARMUP_FRAMES = 30;
    private static final float RENDER_SHARE_GPU_BOUND = 0.45f;
    private static final float GPU_BUSY_THRESHOLD = 55f;
    private static final int EXPORT_INTERVAL_MS = 5000;

    private static final float[] FACTOR_STEPS = {0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 1.00f};

    private static final Stats frameMs    = new Stats(FRAME_HISTORY);
    private static final Stats vpRenderMs = new Stats(FRAME_HISTORY);
    private static final Stats captureMs  = new Stats(FRAME_HISTORY);

    private static long frameStartNs;
    private static long vpRenderStartNs;
    private static long captureStartNs;
    private static long lastDecisionAt = -1;
    private static long lastChangeAt;
    private static long lastExportAt;
    private static int stableWindows;
    private static int cpuIdx = FACTOR_STEPS.length - 1;
    private static int gpuIdx = FACTOR_STEPS.length - 1;
    private static String lastAction = "idle";
    private static boolean initialized;

    private FrameGovernor() {}

    // ---- hooks (called from the ZombieBuddy patches) ----

    public static void onFrameStart() {
        if (!initialized) {
            initialized = true;
            ViewpointKnobs.ensureBound();
            PZOCompat.init();
            applyAll("initial apply");
        }
        KeyPoller.poll(); // Java-side overlay key (works even when PZO eats the Lua key event)
        frameStartNs = System.nanoTime();
    }

    public static void onFrameEnd() {
        frameMs.push((System.nanoTime() - frameStartNs) / 1_000_000f);
        PresentCounter.sample(); // presented-frame counter (what GPU overlays show)
        if (ViewpointKnobs.ensureBound()) {
            // bind retry until Viewpoint's settings exist; then govern
            if (Config.enabled && Config.governor == Config.GovernorMode.ADAPTIVE) tick();
        }
        maybeExportStats();
    }

    public static void onVpRenderStart() {
        vpRenderStartNs = System.nanoTime();
    }

    public static void onVpRenderEnd() {
        if (vpRenderStartNs != 0L) {
            vpRenderMs.push((System.nanoTime() - vpRenderStartNs) / 1_000_000f);
            vpRenderStartNs = 0L;
        }
    }

    public static void onCaptureStart() {
        captureStartNs = System.nanoTime();
    }

    public static void onCaptureEnd() {
        if (captureStartNs != 0L) {
            captureMs.push((System.nanoTime() - captureStartNs) / 1_000_000f);
            captureStartNs = 0L;
        }
    }

    // ---- adaptive logic ----

    private static void tick() {
        long now = System.currentTimeMillis();
        if (lastDecisionAt >= 0 && now - lastDecisionAt < DECISION_INTERVAL_MS) return;
        lastDecisionAt = now;
        if (frameMs.size() < WARMUP_FRAMES) return;
        if (now - lastChangeAt < COOLDOWN_MS) return;

        float budget = frameBudgetMs();
        float p90 = frameMs.percentile(0.90f);
        float mean = frameMs.mean();

        if (p90 > budget * 1.20f) {
            stableWindows = 0;
            stepDown(budget, p90, gpuBound());
        } else if (mean < budget * 0.80f) {
            if (++stableWindows >= STABLE_WINDOWS_TO_CLIMB) {
                stableWindows = 0;
                stepUp(budget, mean);
            }
        } else {
            stableWindows = 0;
        }
    }

    private static boolean gpuBound() {
        float busy = GpuStats.gpuBusyPercent();
        if (busy >= 0f) return busy >= GPU_BUSY_THRESHOLD;
        return vpShare() >= RENDER_SHARE_GPU_BOUND;
    }

    private static void stepDown(float budget, float p90, boolean gpu) {
        String why = String.format("p90 %.1fms > %.1fms, %s",
                p90, budget * 1.2f, gpu ? "GPU-bound" : "game-thread-bound");
        if (gpu) {
            if (stepIdx(gpuIdx, ViewpointKnobs.GROUP_GPU, Config.gpuAdaptive, why, false)) return;
            if (stepIdx(cpuIdx, ViewpointKnobs.GROUP_CPU, Config.cpuAdaptive, why, true)) return;
        } else {
            if (stepIdx(cpuIdx, ViewpointKnobs.GROUP_CPU, Config.cpuAdaptive, why, false)) return;
            if (stepIdx(gpuIdx, ViewpointKnobs.GROUP_GPU, Config.gpuAdaptive, why, true)) return;
        }
        lastAction = "at quality floor - " + why;
        Log.debug("governor: " + lastAction);
    }

    private static void stepUp(float budget, float mean) {
        String why = String.format("mean %.1fms < %.1fms, stable", mean, budget * 0.8f);
        // restore the side the frame is NOT bound by first (cheapest visually)
        boolean gpu = gpuBound();
        if (!gpu) {
            if (stepIdxUp(gpuIdx, ViewpointKnobs.GROUP_GPU, Config.gpuAdaptive, why)) return;
            if (stepIdxUp(cpuIdx, ViewpointKnobs.GROUP_CPU, Config.cpuAdaptive, why)) return;
        } else {
            if (stepIdxUp(cpuIdx, ViewpointKnobs.GROUP_CPU, Config.cpuAdaptive, why)) return;
            if (stepIdxUp(gpuIdx, ViewpointKnobs.GROUP_GPU, Config.gpuAdaptive, why)) return;
        }
        lastAction = "at quality ceiling - " + why;
        Log.debug("governor: " + lastAction);
    }

    private static boolean stepIdx(int idx, String group, boolean adaptive, String why, boolean fallback) {
        if (!adaptive) return false;
        int minIdx = minIdx(group);
        if (idx <= minIdx) return false;
        idx--;
        if (ViewpointKnobs.GROUP_GPU.equals(group)) gpuIdx = idx; else cpuIdx = idx;
        apply(group, FACTOR_STEPS[idx], why + (fallback ? " (other side at floor)" : ""));
        return true;
    }

    private static boolean stepIdxUp(int idx, String group, boolean adaptive, String why) {
        if (!adaptive) return false;
        if (idx >= FACTOR_STEPS.length - 1) return false;
        idx++;
        if (ViewpointKnobs.GROUP_GPU.equals(group)) gpuIdx = idx; else cpuIdx = idx;
        apply(group, FACTOR_STEPS[idx], why);
        return true;
    }

    private static void apply(String group, float factor, String why) {
        int applied = ViewpointKnobs.applyFactor(group, factor);
        lastAction = String.format("%s x%.2f (%d knobs) - %s",
                group, factor, applied, why);
        lastChangeAt = System.currentTimeMillis();
        Log.info("governor: " + lastAction);
    }

    /** Apply the configured (non-adaptive) state - boot, config reload, Lua change. */
    public static void applyAll(String reason) {
        if (!Config.enabled) {
            lastAction = "disabled - restoring Viewpoint stock values";
            ViewpointKnobs.restore(ViewpointKnobs.GROUP_CPU);
            ViewpointKnobs.restore(ViewpointKnobs.GROUP_GPU);
            return;
        }
        switch (Config.governor) {
            case OFF:
                ViewpointKnobs.restore(ViewpointKnobs.GROUP_CPU);
                ViewpointKnobs.restore(ViewpointKnobs.GROUP_GPU);
                lastAction = "governor off - Viewpoint stock values (" + reason + ")";
                break;
            case FIXED:
                cpuIdx = nearestIdx(Config.cpuFactorFixed);
                gpuIdx = nearestIdx(Config.gpuFactorFixed);
                ViewpointKnobs.applyFactor(ViewpointKnobs.GROUP_CPU, Config.cpuFactorFixed);
                ViewpointKnobs.applyFactor(ViewpointKnobs.GROUP_GPU, Config.gpuFactorFixed);
                lastAction = String.format("fixed mode - cpu x%.2f gpu x%.2f (%s)",
                        Config.cpuFactorFixed, Config.gpuFactorFixed, reason);
                break;
            case ADAPTIVE:
            default:
                clampIdx();
                // start from stock, adapt from there
                ViewpointKnobs.restore(ViewpointKnobs.GROUP_CPU);
                ViewpointKnobs.restore(ViewpointKnobs.GROUP_GPU);
                lastAction = "adaptive mode armed (" + reason + ")";
                break;
        }
    }

    // ---- stats export ----

    private static void maybeExportStats() {
        if (!Config.statsExport) return;
        long now = System.currentTimeMillis();
        if (now - lastExportAt < EXPORT_INTERVAL_MS) return;
        lastExportAt = now;
        StatsExporter.writeRow(fps(), frameMean(), frameMs.percentile(0.90f), frameP99(),
                GpuStats.gpuBusyPercent(), vpRenderMs.mean(), captureMs.mean(),
                cpuFactor(), gpuFactor());
    }

    // ---- queries for Bridge / Overlay ----

    public static float fps() {
        float m = frameMs.mean();
        return m > 0.01f ? 1000f / m : 0f;
    }

    public static float fpsOnePercentLow() {
        float p = frameP99();
        return p > 0.01f ? 1000f / p : 0f;
    }

    public static float frameMean()    { return frameMs.mean(); }
    public static float frameP99()     { return frameMs.percentile(0.99f); }
    public static float vpRenderMean() { return vpRenderMs.mean(); }
    public static float captureMean()  { return captureMs.mean(); }

    public static float vpShare() {
        float f = frameMs.mean();
        if (f < 0.01f) return 0f;
        return Math.max(0f, Math.min(1f, vpRenderMs.mean() / f));
    }

    public static float cpuFactor() { return FACTOR_STEPS[cpuIdx]; }
    public static float gpuFactor() { return FACTOR_STEPS[gpuIdx]; }
    public static String lastAction() { return lastAction; }

    /** Presented-frame rate (what GPU overlays show); -1 until a window completes. */
    public static float presentedFps() {
        return PresentCounter.presentedFps();
    }

    public static String status() {
        return String.format(
                "fps %.1f | frame %.1fms | p99 %.1fms | gpu-busy %.0f%% | vp-render %.1fms | capture %.1fms | cpu x%.2f gpu x%.2f | %s",
                fps(), frameMean(), frameP99(), GpuStats.gpuBusyPercent(),
                vpRenderMs.mean(), captureMs.mean(), cpuFactor(), gpuFactor(), lastAction);
    }

    // ---- helpers ----

    private static float frameBudgetMs() {
        return 1000f / Math.max(15, Config.targetFps);
    }

    private static int minIdx(String group) {
        float floor = ViewpointKnobs.GROUP_GPU.equals(group)
                ? Config.gpuFactorMin : Config.cpuFactorMin;
        for (int i = 0; i < FACTOR_STEPS.length; i++) {
            if (FACTOR_STEPS[i] >= floor - 0.001f) return i;
        }
        return FACTOR_STEPS.length - 1;
    }

    private static void clampIdx() {
        cpuIdx = Math.max(minIdx(ViewpointKnobs.GROUP_CPU), Math.min(FACTOR_STEPS.length - 1, cpuIdx));
        gpuIdx = Math.max(minIdx(ViewpointKnobs.GROUP_GPU), Math.min(FACTOR_STEPS.length - 1, gpuIdx));
    }

    private static int nearestIdx(float v) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < FACTOR_STEPS.length; i++) {
            float d = Math.abs(FACTOR_STEPS[i] - v);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
