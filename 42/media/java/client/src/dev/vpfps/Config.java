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
 * Hot-reloads within ~5 s while the game runs. Every value is clamped on
 * load; a typo can never produce a broken renderer.
 *
 * v3.0: the governor now drives Viewpoint's OWN settings through factor
 * ladders (cpuFactor / gpuFactor inside Viewpoint's declared ranges),
 * plus memory-pool scaling and in-game frame-cap control.
 */
public final class Config {

    public enum GovernorMode { ADAPTIVE, FIXED, OFF }
    public enum PzoDefer { AUTO, ON, OFF }

    static final Path FILE =
            Path.of(System.getProperty("user.home", "."), "Zomboid", "vpfps.properties");

    // ---- settings (volatile: watcher thread writes, frame loop reads) ----
    public static volatile boolean enabled = true;
    public static volatile GovernorMode governor = GovernorMode.ADAPTIVE;
    public static volatile int targetFps = 60;
    public static volatile boolean cpuAdaptive = true;     // CPU ladder on/off
    public static volatile boolean gpuAdaptive = true;     // GPU ladder on/off
    public static volatile float cpuFactorMin = 0.30f;     // CPU ladder floor
    public static volatile float gpuFactorMin = 0.30f;     // GPU ladder floor
    public static volatile float cpuFactorFixed = 1.0f;    // fixed-mode values
    public static volatile float gpuFactorFixed = 1.0f;
    public static volatile float memoryScale = 1.0f;       // pool budget scale
    public static volatile boolean overlay = true;
    public static volatile boolean overlayDetail = true;
    public static volatile int overlayKey = 67;           // LWJGL F9; Java-polled (see KeyPoller)
    public static volatile boolean statsExport = false;
    public static volatile PzoDefer deferToPZOpt = PzoDefer.AUTO;
    public static volatile boolean autoProfile = true;
    public static volatile boolean debug = false;

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
        enabled        = bool(p, "enabled", true);
        governor       = mode(p.getProperty("governor"), GovernorMode.ADAPTIVE);
        targetFps      = (int) clampD(num(p, "targetFps", 60), 15, 480);
        cpuAdaptive    = bool(p, "cpuAdaptive", true);
        gpuAdaptive    = bool(p, "gpuAdaptive", true);
        cpuFactorMin   = (float) clampD(num(p, "cpuFactorMin", 0.30), 0.10, 1.0);
        gpuFactorMin   = (float) clampD(num(p, "gpuFactorMin", 0.30), 0.10, 1.0);
        cpuFactorFixed = (float) clampD(num(p, "cpuFactorFixed", 1.0), 0.10, 1.0);
        gpuFactorFixed = (float) clampD(num(p, "gpuFactorFixed", 1.0), 0.10, 1.0);
        memoryScale    = (float) clampD(num(p, "memoryScale", 1.0), 0.30, 2.0);
        overlay        = bool(p, "overlay", true);
        overlayDetail  = bool(p, "overlayDetail", true);
        overlayKey     = (int) clampD(num(p, "overlayKey", 67), 1, 400);
        statsExport    = bool(p, "statsExport", false);
        deferToPZOpt   = pzoDefer(p.getProperty("deferToPZOpt"), PzoDefer.AUTO);
        autoProfile    = bool(p, "autoProfile", true);
        debug          = bool(p, "debug", false);

        // first-run heuristic: weak CPUs start the CPU ladder lower
        if (firstRun && autoProfile && !profiled) {
            profiled = true;
            int cores = Runtime.getRuntime().availableProcessors();
            if (cores <= 4) {
                cpuFactorMin = 0.30f;
                targetFps = Math.min(targetFps, 45);
            } else if (cores >= 12) {
                cpuFactorMin = 0.50f;
            }
            Log.info("autoProfile: " + cores + " logical cores -> cpuFactorMin="
                    + trim(cpuFactorMin) + ", targetFps=" + targetFps);
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
                ViewpointMemory.applyScale(Config.memoryScale);
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
                + " cpu[" + (cpuAdaptive ? "adaptive" : "off") + " >=" + trim(cpuFactorMin) + "]"
                + " gpu[" + (gpuAdaptive ? "adaptive" : "off") + " >=" + trim(gpuFactorMin) + "]"
                + " memoryScale=" + trim(memoryScale)
                + " overlay=" + overlay
                + " statsExport=" + statsExport
                + " enabled=" + enabled;
    }

    private static TreeMap<String, String> snapshot() {
        TreeMap<String, String> m = new TreeMap<>();
        m.put("enabled", String.valueOf(enabled));
        m.put("governor", governor.name().toLowerCase());
        m.put("targetFps", String.valueOf(targetFps));
        m.put("cpuAdaptive", String.valueOf(cpuAdaptive));
        m.put("gpuAdaptive", String.valueOf(gpuAdaptive));
        m.put("cpuFactorMin", trim(cpuFactorMin));
        m.put("gpuFactorMin", trim(gpuFactorMin));
        m.put("cpuFactorFixed", trim(cpuFactorFixed));
        m.put("gpuFactorFixed", trim(gpuFactorFixed));
        m.put("memoryScale", trim(memoryScale));
        m.put("overlay", String.valueOf(overlay));
        m.put("overlayDetail", String.valueOf(overlayDetail));
        m.put("overlayKey", String.valueOf(overlayKey));
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
