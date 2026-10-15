package dev.vpfps;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Live-tunable configuration, persisted at  <user>\Zomboid\vpfps.properties.
 *
 * The file hot-reloads within ~5 s while the game runs (a daemon thread
 * watches its mtime), so it doubles as a control surface when the Lua
 * bridge is unavailable. All values are clamped to sane ranges on load -
 * a typo in the file can never produce a broken renderer.
 *
 * v2.0: hardware auto-profiling (first run only), draw-distance ladder,
 * chunk-walk idle throttle, stats export and the PZ_Optimization defer
 * policy.
 */
public final class Config {

    public enum GovernorMode { ADAPTIVE, FIXED, OFF }
    public enum PzoDefer { AUTO, ON, OFF }

    static final Path FILE =
            Path.of(System.getProperty("user.home", "."), "Zomboid", "vpfps.properties");

    // ---- settings (volatile: written by the watcher thread, read on the frame loop) ----
    public static volatile boolean enabled = true;
    public static volatile GovernorMode governor = GovernorMode.ADAPTIVE;
    public static volatile int targetFps = 60;
    public static volatile boolean dynamicResolution = true;
    public static volatile float renderScaleFixed = 1.0f;
    public static volatile float renderScaleMin = 0.50f;
    public static volatile float renderScaleMax = 1.00f;
    public static volatile float shadowScaleFixed = 1.0f;
    public static volatile boolean meshBudgetEnabled = true;
    public static volatile float meshBudgetMs = 2.0f;
    public static volatile boolean overlay = true;
    public static volatile boolean overlayDetail = true;
    public static volatile boolean debug = false;

    // ---- v2.0 ----
    public static volatile boolean drawDistanceAdaptive = true;
    public static volatile float drawDistanceMin = 0.70f;   // governor floor, factor of stock
    public static volatile boolean chunkWalkThrottleEnabled = true;
    public static volatile int chunkWalkThrottleMs = 250;   // idle walk interval
    public static volatile boolean statsExport = false;      // Zomboid/vpfps-stats.csv
    public static volatile PzoDefer deferToPZOpt = PzoDefer.AUTO;
    public static volatile boolean autoProfile = true;       // first-run hardware heuristic

    private static long lastLoaded = -1L;
    private static boolean profiled = false;

    private Config() {}

    public static synchronized void load() {
        boolean firstRun = !Files.exists(FILE);
        Properties p = new Properties();
        if (!firstRun) {
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
                lastLoaded = Files.getLastModifiedTime(FILE).toMillis();
            } catch (Throwable t) {
                Log.warn("could not read " + FILE + " (" + t + ") - defaults in use");
            }
        }
        enabled           = bool(p, "enabled", true);
        governor          = mode(p.getProperty("governor"), GovernorMode.ADAPTIVE);
        targetFps         = (int) clampD(num(p, "targetFps", 60), 15, 480);
        dynamicResolution = bool(p, "dynamicResolution", true);
        renderScaleFixed  = (float) clampD(num(p, "renderScaleFixed", 1.0), 0.25, 1.0);
        renderScaleMin    = (float) clampD(num(p, "renderScaleMin", 0.50), 0.25, 1.0);
        renderScaleMax    = (float) clampD(num(p, "renderScaleMax", 1.00), 0.25, 1.0);
        shadowScaleFixed  = (float) clampD(num(p, "shadowScaleFixed", 1.0), 0.25, 1.0);
        meshBudgetEnabled = bool(p, "meshBudgetEnabled", true);
        meshBudgetMs      = (float) clampD(num(p, "meshBudgetMs", 2.0), 0.1, 16.0);
        overlay           = bool(p, "overlay", true);
        overlayDetail     = bool(p, "overlayDetail", true);
        debug             = bool(p, "debug", false);

        drawDistanceAdaptive     = bool(p, "drawDistanceAdaptive", true);
        drawDistanceMin          = (float) clampD(num(p, "drawDistanceMin", 0.70), 0.40, 1.0);
        chunkWalkThrottleEnabled = bool(p, "chunkWalkThrottleEnabled", true);
        chunkWalkThrottleMs      = (int) clampD(num(p, "chunkWalkThrottleMs", 250), 50, 2000);
        statsExport              = bool(p, "statsExport", false);
        deferToPZOpt             = pzoDefer(p.getProperty("deferToPZOpt"), PzoDefer.AUTO);
        autoProfile              = bool(p, "autoProfile", true);
        if (renderScaleMin > renderScaleMax) renderScaleMin = renderScaleMax;

        // first-run hardware heuristic: seed the mesh budget from the CPU count
        if (firstRun && autoProfile && !profiled) {
            profiled = true;
            int cores = Runtime.getRuntime().availableProcessors();
            if (cores <= 4) {
                meshBudgetMs = 1.0f;
                chunkWalkThrottleMs = 400;
            } else if (cores >= 12) {
                meshBudgetMs = 3.0f;
            }
            Log.info("autoProfile: " + cores + " logical cores -> meshBudget="
                    + trim(meshBudgetMs) + "ms, chunkWalkThrottle=" + chunkWalkThrottleMs + "ms");
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp)) {
                w.write("# Viewpoint FPS Optimization - live-tunable, edits hot-reload within ~5 s");
                w.newLine();
                for (java.util.Map.Entry<String, String> e : snapshot().entrySet()) {
                    w.write(e.getKey() + "=" + e.getValue());
                    w.newLine();
                }
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            lastLoaded = Files.getLastModifiedTime(FILE).toMillis();
        } catch (Throwable t) {
            Log.warn("could not save config: " + t);
        }
    }

    /** Called from the watcher thread; reloads when the file changed on disk. */
    public static synchronized void maybeReload() {
        try {
            if (!Files.exists(FILE)) return;
            long mtime = Files.getLastModifiedTime(FILE).toMillis();
            if (mtime != lastLoaded) {
                Log.info("config change detected - reloading");
                load();
                FrameGovernor.applyAll("config reload");
            }
        } catch (Throwable ignored) {
        }
    }

    static void watchLoop() {
        // noinspection InfiniteLoopStatement - daemon thread, dies with the JVM
        while (true) {
            maybeReload();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public static String describe() {
        return "governor=" + governor.name().toLowerCase()
                + " targetFps=" + targetFps
                + " scale=[" + trim(renderScaleMin) + ".." + trim(renderScaleMax) + "]"
                + " meshBudget=" + trim(meshBudgetMs) + "ms"
                + " walkThrottle=" + chunkWalkThrottleMs + "ms"
                + " drawDistMin=" + trim(drawDistanceMin)
                + " overlay=" + overlay
                + " statsExport=" + statsExport
                + " enabled=" + enabled;
    }

    private static TreeMap<String, String> snapshot() {
        TreeMap<String, String> m = new TreeMap<>();
        m.put("enabled", String.valueOf(enabled));
        m.put("governor", governor.name().toLowerCase());
        m.put("targetFps", String.valueOf(targetFps));
        m.put("dynamicResolution", String.valueOf(dynamicResolution));
        m.put("renderScaleFixed", trim(renderScaleFixed));
        m.put("renderScaleMin", trim(renderScaleMin));
        m.put("renderScaleMax", trim(renderScaleMax));
        m.put("shadowScaleFixed", trim(shadowScaleFixed));
        m.put("meshBudgetEnabled", String.valueOf(meshBudgetEnabled));
        m.put("meshBudgetMs", trim(meshBudgetMs));
        m.put("overlay", String.valueOf(overlay));
        m.put("overlayDetail", String.valueOf(overlayDetail));
        m.put("drawDistanceAdaptive", String.valueOf(drawDistanceAdaptive));
        m.put("drawDistanceMin", trim(drawDistanceMin));
        m.put("chunkWalkThrottleEnabled", String.valueOf(chunkWalkThrottleEnabled));
        m.put("chunkWalkThrottleMs", String.valueOf(chunkWalkThrottleMs));
        m.put("statsExport", String.valueOf(statsExport));
        m.put("deferToPZOpt", deferToPZOpt.name().toLowerCase());
        m.put("autoProfile", String.valueOf(autoProfile));
        m.put("debug", String.valueOf(debug));
        return m;
    }

    private static String trim(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.trim().toLowerCase();
        if ("true".equals(v) || "yes".equals(v) || "1".equals(v) || "on".equals(v)) return true;
        if ("false".equals(v) || "no".equals(v) || "0".equals(v) || "off".equals(v)) return false;
        return def;
    }

    private static double num(Properties p, String key, double def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static GovernorMode mode(String v, GovernorMode def) {
        if (v == null) return def;
        try {
            return GovernorMode.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static PzoDefer pzoDefer(String v, PzoDefer def) {
        if (v == null) return def;
        try {
            return PzoDefer.valueOf(v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
