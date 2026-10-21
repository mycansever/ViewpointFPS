package dev.vpfps;

import me.zed_0xff.zombie_buddy.Exposer;

/**
 * Lua-facing facade, exposed to Lua as the global "VPFPSBridge" through
 * ZombieBuddy's official mechanism: @Exposer.LuaClass. ZombieBuddy scans
 * the mod's javaPkgName package (dev.vpfps), registers the class with the
 * game's Lua engine, and static methods become callable as
 * VPFPSBridge.method(...) - the documented ZombieBuddy modding pattern
 * (same as zed_0xff's own ZBetterWorkshopUpload).
 *
 * Lua-facing contract:
 *   - only double / boolean / String parameters and returns (Kahlua maps
 *     Lua numbers to Double)
 *   - every method swallows its exceptions; worst case a safe default
 *
 * v3.0 surface: governor control, Viewpoint's own settings via factor
 * ladders, memory-pool scaling, in-game frame-cap control, live stats.
 */
@Exposer.LuaClass(name = "VPFPSBridge")
public final class Bridge {

    private Bridge() {}

    // ---- identity / diagnostics ----

    public static String ping() {
        return "vpfps-" + Main.VERSION;
    }

    public static String getVersion() {
        return Main.VERSION;
    }

    public static String getCapabilities() {
        return ViewpointKnobs.statusShort() + " | " + ViewpointMemory.statusShort()
                + " | frameCap " + (FrameCapControl.isAvailable() ? "available" : "unavailable");
    }

    public static String getStatus() {
        return FrameGovernor.status();
    }

    public static String getKnobDump() {
        return ViewpointKnobs.describe();
    }

    public static String getMemoryDump() {
        return ViewpointMemory.describe();
    }

    public static String getCompatibility() {
        return PZOCompat.status();
    }

    // ---- live stats (for the panel header) ----

    public static double getFps()               { return FrameGovernor.fps(); }
    public static double getFpsOnePercentLow()  { return FrameGovernor.fpsOnePercentLow(); }
    public static double getFrameTimeMs()       { return FrameGovernor.frameMean(); }
    public static double getFrameTimeP99Ms()    { return FrameGovernor.frameP99(); }
    public static double getGpuBusyPercent()    { return GpuStats.gpuBusyPercent(); }
    public static double getVpRenderShare()     { return FrameGovernor.vpShare(); }
    public static double getCpuFactor()         { return FrameGovernor.cpuFactor(); }
    public static double getGpuFactor()         { return FrameGovernor.gpuFactor(); }
    public static double getVramFreeMiB()       { return ViewpointMemory.vramFreeMiB(); }
    public static double getVramTotalMiB()      { return ViewpointMemory.vramTotalMiB(); }
    public static double getVramEvictions()     { return ViewpointMemory.vramEvictions(); }

    // ---- governor settings ----

    public static void setEnabled(boolean v) {
        Config.enabled = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isEnabled() {
        return Config.enabled;
    }

    public static void setGovernor(String mode) {
        Config.governor = "adaptive".equalsIgnoreCase(mode) ? Config.GovernorMode.ADAPTIVE
                : "off".equalsIgnoreCase(mode) ? Config.GovernorMode.OFF
                : Config.GovernorMode.FIXED;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static String getGovernor() {
        return Config.governor.name().toLowerCase();
    }

    public static void setTargetFps(double v) {
        Config.targetFps = (int) Math.max(15, Math.min(480, Math.round(v)));
        Config.save();
    }

    public static double getTargetFps() {
        return Config.targetFps;
    }

    public static void setCpuAdaptive(boolean v) {
        Config.cpuAdaptive = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isCpuAdaptive() {
        return Config.cpuAdaptive;
    }

    public static void setGpuAdaptive(boolean v) {
        Config.gpuAdaptive = v;
        Config.save();
        FrameGovernor.applyAll("lua");
    }

    public static boolean isGpuAdaptive() {
        return Config.gpuAdaptive;
    }

    public static void setCpuFactorMin(double v) {
        Config.cpuFactorMin = (float) Math.max(0.10, Math.min(1.0, v));
        Config.save();
    }

    public static double getCpuFactorMin() {
        return Config.cpuFactorMin;
    }

    public static void setGpuFactorMin(double v) {
        Config.gpuFactorMin = (float) Math.max(0.10, Math.min(1.0, v));
        Config.save();
    }

    public static double getGpuFactorMin() {
        return Config.gpuFactorMin;
    }

    public static void setCpuFactorFixed(double v) {
        Config.cpuFactorFixed = (float) Math.max(0.10, Math.min(1.0, v));
        Config.save();
        if (Config.governor == Config.GovernorMode.FIXED) FrameGovernor.applyAll("lua");
    }

    public static double getCpuFactorFixed() {
        return Config.cpuFactorFixed;
    }

    public static void setGpuFactorFixed(double v) {
        Config.gpuFactorFixed = (float) Math.max(0.10, Math.min(1.0, v));
        Config.save();
        if (Config.governor == Config.GovernorMode.FIXED) FrameGovernor.applyAll("lua");
    }

    public static double getGpuFactorFixed() {
        return Config.gpuFactorFixed;
    }

    /** Restore every Viewpoint setting we touched to its stock value. */
    public static void restoreStock() {
        ViewpointKnobs.restore(ViewpointKnobs.GROUP_CPU);
        ViewpointKnobs.restore(ViewpointKnobs.GROUP_GPU);
        ViewpointMemory.restore();
    }

    // ---- memory pools (RAM / VRAM) ----

    public static void setMemoryScale(double v) {
        Config.memoryScale = (float) Math.max(0.30, Math.min(2.0, v));
        Config.save();
        ViewpointMemory.applyScale(Config.memoryScale);
    }

    public static double getMemoryScale() {
        return Config.memoryScale;
    }

    // ---- in-game frame cap (Viewpoint's FrameCaps) ----

    public static boolean isFrameCapAvailable() {
        return FrameCapControl.isAvailable();
    }

    public static double getGameCap() {
        return FrameCapControl.gameCap();
    }

    public static double getMenuCap() {
        return FrameCapControl.menuCap();
    }

    public static boolean setGameCap(double fps) {
        return FrameCapControl.setGameCap(fps);
    }

    public static boolean setMenuCap(double fps) {
        return FrameCapControl.setMenuCap(fps);
    }

    public static boolean uncapGame() {
        return FrameCapControl.uncapGame();
    }

    public static double getUncappedValue() {
        return FrameCapControl.uncapped();
    }

    // ---- overlay / misc ----

    public static void setOverlayEnabled(boolean v) {
        Config.overlay = v;
        Config.save();
    }

    public static boolean isOverlayEnabled() {
        return Config.overlay;
    }

    public static void setOverlayDetail(boolean v) {
        Config.overlayDetail = v;
        Config.save();
    }

    public static boolean isOverlayDetail() {
        return Config.overlayDetail;
    }

    public static void setStatsExport(boolean v) {
        Config.statsExport = v;
        Config.save();
    }

    public static boolean isStatsExport() {
        return Config.statsExport;
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
