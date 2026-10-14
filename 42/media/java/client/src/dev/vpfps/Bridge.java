package dev.vpfps;

/**
 * Static facade exposed to Lua as the global "VPFPSBridge"
 * (via PZ's ClassExposer, see Main.exposeLuaBridge).
 *
 * Lua-facing contract:
 *   - only double / boolean / String parameters and returns (Kahlua maps
 *     Lua numbers to Double - float/int params would break the call)
 *   - every method swallows its exceptions: a Lua call can never crash
 *     the game, worst case it returns a safe default
 *
 * The Lua side (42/media/lua/client/VPFPS/) uses this for the ModOptions
 * integration and the keybind; the Java side runs standalone if the
 * bridge is unavailable.
 */
public final class Bridge {

    private Bridge() {}

    // ---- identity / diagnostics ----

    public static String ping() {
        return "vpfps-" + Main.VERSION;
    }

    public static boolean isViewpointPresent() {
        return ViewpointBridge.isPresent();
    }

    public static String getCapabilities() {
        return ViewpointBridge.capabilities();
    }

    public static String getStatus() {
        return FrameGovernor.status();
    }

    public static double getFps() {
        return FrameGovernor.fps();
    }

    public static double getFrameTimeMs() {
        return FrameGovernor.frameMean();
    }

    public static double getFrameTimeP99Ms() {
        return FrameGovernor.frameP99();
    }

    public static double getVpRenderShare() {
        return FrameGovernor.vpShare();
    }

    public static double getRenderScale() {
        return FrameGovernor.renderScale();
    }

    // ---- live settings (all persisted to vpfps.properties) ----

    public static void setEnabled(boolean v) {
        Config.enabled = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isEnabled() {
        return Config.enabled;
    }

    public static void setAdaptive(boolean v) {
        Config.governor = v ? Config.GovernorMode.ADAPTIVE : Config.GovernorMode.FIXED;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isAdaptive() {
        return Config.governor == Config.GovernorMode.ADAPTIVE;
    }

    public static void setDynamicResolution(boolean v) {
        Config.dynamicResolution = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isDynamicResolution() {
        return Config.dynamicResolution;
    }

    public static void setMeshBudgetEnabled(boolean v) {
        Config.meshBudgetEnabled = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isMeshBudgetEnabled() {
        return Config.meshBudgetEnabled;
    }

    public static void setTargetFps(double v) {
        Config.targetFps = (int) Math.max(15, Math.min(480, Math.round(v)));
        Config.save();
    }

    public static double getTargetFps() {
        return Config.targetFps;
    }

    public static void setRenderScaleFixed(double v) {
        Config.renderScaleFixed = (float) Math.max(0.25, Math.min(1.0, v));
        Config.save();
        if (!isAdaptive()) FrameGovernor.applyAll("lua");
    }

    public static double getRenderScaleFixed() {
        return Config.renderScaleFixed;
    }

    public static void setRenderScaleMin(double v) {
        Config.renderScaleMin = (float) Math.max(0.25, Math.min(1.0, v));
        if (Config.renderScaleMin > Config.renderScaleMax) {
            Config.renderScaleMin = Config.renderScaleMax;
        }
        Config.save();
    }

    public static double getRenderScaleMin() {
        return Config.renderScaleMin;
    }

    public static void setMeshBudgetMs(double v) {
        Config.meshBudgetMs = (float) Math.max(0.1, Math.min(16.0, v));
        Config.save();
        if (!isAdaptive()) MeshBudget.setBudgetMs(Config.meshBudgetMs);
    }

    public static double getMeshBudgetMs() {
        return Config.meshBudgetMs;
    }

    public static void setOverlayEnabled(boolean v) {
        Config.overlay = v;
        Config.save();
    }

    public static boolean isOverlayEnabled() {
        return Config.overlay;
    }

    public static void setDebug(boolean v) {
        Config.debug = v;
        Config.save();
    }

    public static boolean isDebug() {
        return Config.debug;
    }

    public static void saveConfig() {
        Config.save();
    }
}
