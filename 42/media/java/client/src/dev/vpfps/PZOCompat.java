package dev.vpfps;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detection and compatibility policy for xD3I's PZ_Optimization
 * (https://github.com/xD3I/PZ_Optimization, Workshop 3805285544).
 *
 * IMPORTANT — licensing: PZ_Optimization's repository carries NO open-source
 * license (verified: no LICENSE file), so its code is all-rights-reserved.
 * This mod therefore contains ZERO code from it; the techniques we use
 * (per-frame mesh/bake budgets, measurement-driven quality ladders,
 * GPU/game-thread attribution) are ideas, reimplemented independently.
 *
 * Technical compatibility: PZ_Optimization ships drop-in .class files that
 * shadow vanilla game classes, while our ZombieBuddy advice hooks are applied
 * on top of whatever class actually loads - including PZO's overridden
 * versions. The two therefore stack cleanly. The only real collision is the
 * performance overlay: PZO has its own F9 overlay, so when it is detected
 * (and deferToPZOpt is auto/on) we turn OUR overlay off by default and let
 * PZO's take the screen.
 */
public final class PZOCompat {

    private static Boolean detected;
    private static boolean policyApplied;

    private static final Path PZOPT_CACHE_DIR =
            Path.of(System.getProperty("user.home", "."), "Zomboid", "pzopt");

    /** Marker class probes — best effort, harmless misses. */
    private static final String[] PROBE_CLASSES = {
            "pzopt.Main", "pzopt.Opts", "pzopt.PZOpt", "pzopt.Properties",
    };

    private PZOCompat() {}

    public static synchronized boolean isDetected() {
        if (detected == null) detected = detect();
        return detected;
    }

    private static boolean detect() {
        try {
            if (Files.exists(PZOPT_CACHE_DIR)) return true;
        } catch (Throwable ignored) {
        }
        for (String cn : PROBE_CLASSES) {
            try {
                Class.forName(cn, false, PZOCompat.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Called once at first frame. Logs the compatibility state and applies the
     * overlay policy (our overlay steps aside when PZO is present).
     */
    public static synchronized void init() {
        if (policyApplied) return;
        policyApplied = true;

        boolean pzo = isDetected();
        if (pzo) {
            Log.info("PZ_Optimization detected - compatible. Its class overrides and our "
                    + "ZombieBuddy hooks stack cleanly; no code is shared (PZO carries no "
                    + "open license). Our mesh budget and Viewpoint-side features stay active.");
            boolean defer = Config.deferToPZOpt != Config.PzoDefer.OFF;
            if (defer && Config.overlay) {
                Config.overlay = false;
                Log.info("deferToPZOpt: our overlay is OFF while PZ_Optimization is installed "
                        + "(it has its own F9 overlay). Set overlay=true to force ours on.");
            }
        } else {
            Log.info("PZ_Optimization not detected - running standalone");
        }
    }

    public static String status() {
        return isDetected() ? "PZ_Optimization: detected" : "PZ_Optimization: absent";
    }
}
