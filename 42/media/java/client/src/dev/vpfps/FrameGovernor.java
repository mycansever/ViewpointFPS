package dev.vpfps;

/**
 * The adaptive quality governor - the brain of the mod.
 *
 * Every 500 ms it looks at the frame-time distribution measured by
 * Patch_GameWindow (and Viewpoint's own render time measured by Patch_FP)
 * and walks an independent quality ladder per subsystem:
 *
 *   render scale   0.50 .. 1.00   (GPU cost, Viewpoint render-scale knob)
 *   mesh budget    0.5 .. 8.0 ms  (game/worker-thread cost)
 *   shadow scale   0.50 .. 1.00   (GPU cost, Viewpoint shadow knob)
 *
 * WHAT it lowers first is decided by cost attribution - the same idea as
 * PZ_Optimization's GPU/game-thread busy split:
 *
 *   - if Viewpoint's render pass eats >= 45% of the frame, the bottleneck
 *     is the GPU -> lower render scale first, then shadows
 *   - otherwise the bottleneck is the game thread -> shrink the mesh
 *     budget first, then touch the GPU ladders
 *
 * Descending is immediate (frame times over budget annoy instantly);
 * climbing back up needs 3 consecutive stable windows (a oscillating
 * governor is worse than none). All steps clamp to the user's
 * [renderScaleMin, renderScaleMax] window.
 */
public final class FrameGovernor {

    private static final int FRAME_HISTORY = 240;
    private static final int DECISION_INTERVAL_MS = 500;
    private static final int COOLDOWN_MS = 1000;
    private static final int STABLE_WINDOWS_TO_CLIMB = 3;
    private static final int WARMUP_FRAMES = 30;
    private static final float GPU_BOUND_SHARE = 0.45f;

    private static final float[] RENDER_SCALE_STEPS = {0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 1.00f};
    private static final float[] MESH_BUDGET_STEPS  = {0.5f, 1.0f, 2.0f, 4.0f, 8.0f};
    private static final float[] SHADOW_STEPS       = {0.50f, 0.75f, 1.00f};

    private static final Stats frameMs    = new Stats(FRAME_HISTORY);
    private static final Stats vpRenderMs = new Stats(FRAME_HISTORY);

    private static long frameStartNs;
    private static long vpRenderStartNs;
    private static long lastDecisionAt = -1;
    private static long lastChangeAt;
    private static int stableWindows;
    private static int scaleIdx = RENDER_SCALE_STEPS.length - 1;
    private static int meshIdx;
    private static int shadowIdx = SHADOW_STEPS.length - 1;
    private static String lastAction = "idle";
    private static boolean initialized;

    static {
        meshIdx = nearestIndex(MESH_BUDGET_STEPS, Config.meshBudgetMs);
    }

    private FrameGovernor() {}

    // ---- hooks (called from the ZombieBuddy patches) ----

    public static void onFrameStart() {
        if (!initialized) {
            initialized = true;
            ViewpointBridge.init();
            applyAll("initial apply");
        }
        frameStartNs = System.nanoTime();
        MeshBudget.beginFrame();
    }

    public static void onFrameEnd() {
        frameMs.push((System.nanoTime() - frameStartNs) / 1_000_000f);
        if (Config.enabled && Config.governor == Config.GovernorMode.ADAPTIVE) tick();
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
            stepDown(budget, p90, vpShare());
        } else if (mean < budget * 0.80f) {
            if (++stableWindows >= STABLE_WINDOWS_TO_CLIMB) {
                stableWindows = 0;
                stepUp(budget, mean);
            }
        } else {
            stableWindows = 0;
        }
    }

    private static void stepDown(float budget, float p90, float share) {
        String why = String.format("p90 %.1fms > %.1fms, %s",
                p90, budget * 1.2f, share >= GPU_BOUND_SHARE ? "GPU-bound" : "game-thread-bound");
        if (share >= GPU_BOUND_SHARE) {
            if (scaleIdx > minScaleIdx())   { scaleIdx--;   applyRenderScale(why); return; }
            if (shadowIdx > 0)              { shadowIdx--; applyShadow(why);      return; }
        } else {
            if (meshIdx > 0)                { meshIdx--;   applyMesh(why);        return; }
            if (scaleIdx > minScaleIdx())   { scaleIdx--;   applyRenderScale(why); return; }
        }
        lastAction = "at quality floor - " + why;
        Log.debug("governor: " + lastAction);
    }

    private static void stepUp(float budget, float mean) {
        String why = String.format("mean %.1fms < %.1fms, stable", mean, budget * 0.8f);
        // climb the cheap things first: mesh budget, shadows, then resolution
        if (meshIdx < MESH_BUDGET_STEPS.length - 1)   { meshIdx++;   applyMesh(why);   return; }
        if (shadowIdx < SHADOW_STEPS.length - 1)      { shadowIdx++; applyShadow(why); return; }
        if (scaleIdx < maxScaleIdx())                 { scaleIdx++;  applyRenderScale(why); return; }
        lastAction = "at quality ceiling - " + why;
        Log.debug("governor: " + lastAction);
    }

    private static void applyRenderScale(String why) {
        float v = RENDER_SCALE_STEPS[scaleIdx];
        if (!Config.dynamicResolution) {
            lastAction = "dynamic resolution off - scale pinned at "
                    + String.format("%.2f", Config.renderScaleFixed);
            return;
        }
        if (ViewpointBridge.writeKnob("renderScale", v)) {
            lastAction = String.format("render scale %.2f (%s)", v, why);
            lastChangeAt = System.currentTimeMillis();
            Log.info("governor: " + lastAction);
        } else if (!ViewpointBridge.hasKnob("renderScale")) {
            lastAction = "render-scale knob unavailable - mesh/shadow only (" + why + ")";
        }
    }

    private static void applyShadow(String why) {
        float v = SHADOW_STEPS[shadowIdx];
        if (ViewpointBridge.writeKnob("shadowScale", v)) {
            lastAction = String.format("shadow scale %.2f (%s)", v, why);
            lastChangeAt = System.currentTimeMillis();
            Log.info("governor: " + lastAction);
        } else if (!ViewpointBridge.hasKnob("shadowScale")) {
            lastAction = "shadow knob unavailable (" + why + ")";
        }
    }

    private static void applyMesh(String why) {
        float v = MESH_BUDGET_STEPS[meshIdx];
        MeshBudget.setBudgetMs(v);
        Config.meshBudgetMs = v;
        lastAction = String.format("mesh budget %.1fms (%s)", v, why);
        lastChangeAt = System.currentTimeMillis();
        Log.info("governor: " + lastAction);
    }

    /** Apply the configured (non-adaptive) state - boot, config reload, Lua change. */
    public static void applyAll(String reason) {
        if (!Config.enabled) {
            lastAction = "disabled - all hooks pass through";
            MeshBudget.setBudgetMs(Float.MAX_VALUE / 4f);
            return;
        }
        MeshBudget.setBudgetMs(Config.meshBudgetMs);
        switch (Config.governor) {
            case OFF:
            case FIXED:
                ViewpointBridge.writeKnob("renderScale", Config.renderScaleFixed);
                ViewpointBridge.writeKnob("shadowScale", Config.shadowScaleFixed);
                scaleIdx = nearestIndex(RENDER_SCALE_STEPS, Config.renderScaleFixed);
                meshIdx = nearestIndex(MESH_BUDGET_STEPS, Config.meshBudgetMs);
                lastAction = (Config.governor == Config.GovernorMode.OFF
                        ? "governor off" : "fixed mode")
                        + " - scale " + String.format("%.2f", Config.renderScaleFixed)
                        + " (" + reason + ")";
                break;
            case ADAPTIVE:
            default:
                meshIdx = nearestIndex(MESH_BUDGET_STEPS, Config.meshBudgetMs);
                clampScaleIdx();
                applyRenderScale("adaptive mode (" + reason + ")");
                break;
        }
    }

    // ---- queries for Bridge / Overlay ----

    public static float fps() {
        float m = frameMs.mean();
        return m > 0.01f ? 1000f / m : 0f;
    }

    public static float frameMean()   { return frameMs.mean(); }
    public static float frameP99()    { return frameMs.percentile(0.99f); }
    public static float vpRenderMean(){ return vpRenderMs.mean(); }

    /** Viewpoint render pass share of the total frame (0..1). */
    public static float vpShare() {
        float f = frameMs.mean();
        if (f < 0.01f) return 0f;
        return Math.max(0f, Math.min(1f, vpRenderMs.mean() / f));
    }

    public static float renderScale() { return RENDER_SCALE_STEPS[scaleIdx]; }
    public static float shadowScale() { return SHADOW_STEPS[shadowIdx]; }
    public static String lastAction() { return lastAction; }

    public static String status() {
        return String.format(
                "fps %.1f | frame %.1fms | p99 %.1fms | vp-render %.1fms (%.0f%%) | %s",
                fps(), frameMean(), frameP99(), vpRenderMean(), vpShare() * 100f, lastAction);
    }

    // ---- helpers ----

    private static float frameBudgetMs() {
        return 1000f / Math.max(15, Config.targetFps);
    }

    private static int minScaleIdx() {
        for (int i = 0; i < RENDER_SCALE_STEPS.length; i++) {
            if (RENDER_SCALE_STEPS[i] >= Config.renderScaleMin - 0.001f) return i;
        }
        return RENDER_SCALE_STEPS.length - 1;
    }

    private static int maxScaleIdx() {
        int idx = RENDER_SCALE_STEPS.length - 1;
        for (int i = 0; i < RENDER_SCALE_STEPS.length; i++) {
            if (RENDER_SCALE_STEPS[i] <= Config.renderScaleMax + 0.001f) idx = i;
        }
        return Math.max(idx, minScaleIdx());
    }

    private static void clampScaleIdx() {
        scaleIdx = Math.max(minScaleIdx(), Math.min(maxScaleIdx(), scaleIdx));
    }

    private static int nearestIndex(float[] steps, float v) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < steps.length; i++) {
            float d = Math.abs(steps[i] - v);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }
}
