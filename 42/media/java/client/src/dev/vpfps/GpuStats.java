package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reads Viewpoint's own GPU telemetry, discovered by reverse-engineering
 * Viewpoint.jar:
 *
 *   viewpoint.platform.GpuBusy.percent()  - static, package-private
 *        returns recent GPU utilization 0..100
 *   viewpoint.platform.Profile            - per-pass GPU/CPU timers
 *        NAMES (String[]) + recentGpuMs (double[]) index-paired
 *
 * Everything is reflection + Guard: if a Viewpoint update moves these,
 * the overlay simply loses those lines and the governor falls back to
 * our own FP render-time attribution.
 */
public final class GpuStats {

    private static final Guard busyGuard = new Guard("gpu-busy", 4);
    private static final Guard passGuard = new Guard("gpu-passes", 4);

    private static Method percentMethod;
    private static String[] passNames;
    private static double[] recentGpuMs;
    private static boolean busyInit;
    private static boolean passInit;

    private GpuStats() {}

    /** GPU utilization percent (0..100) from Viewpoint's own sampler, or -1. */
    public static float gpuBusyPercent() {
        return (Float) busyGuard.run(() -> {
            if (!busyInit) {
                busyInit = true;
                Class<?> c = Class.forName("viewpoint.platform.GpuBusy", false,
                        GpuStats.class.getClassLoader());
                percentMethod = c.getDeclaredMethod("percent");
                percentMethod.setAccessible(true);
            }
            if (percentMethod == null) return -1f;
            Object v = percentMethod.invoke(null);
            return v instanceof Number ? ((Number) v).floatValue() : -1f;
        }, -1f);
    }

    /**
     * Top-N render passes by recent GPU time, e.g. "FAR 3.2ms SHADOW 1.8ms".
     * Empty string when unavailable.
     */
    public static String topPasses(int n) {
        Object r = passGuard.run(() -> {
            if (!passInit) {
                passInit = true;
                Class<?> c = Class.forName("viewpoint.platform.Profile", false,
                        GpuStats.class.getClassLoader());
                Field namesF = c.getDeclaredField("NAMES");
                namesF.setAccessible(true);
                Field msF = c.getDeclaredField("recentGpuMs");
                msF.setAccessible(true);
                passNames = (String[]) namesF.get(null);
                recentGpuMs = (double[]) msF.get(null);
            }
            if (passNames == null || recentGpuMs == null) return "";
            int len = Math.min(passNames.length, recentGpuMs.length);

            // simple selection of the top n by value
            Integer[] idx = new Integer[len];
            for (int i = 0; i < len; i++) idx[i] = i;
            java.util.Arrays.sort(idx, (a, b) -> Double.compare(recentGpuMs[b], recentGpuMs[a]));

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(n, len); i++) {
                double ms = recentGpuMs[idx[i]];
                if (ms <= 0.01) break;
                if (sb.length() > 0) sb.append("  ");
                sb.append(passNames[idx[i]]).append(' ').append(String.format("%.1f", ms)).append("ms");
            }
            return sb.toString();
        }, "");
        return r instanceof String ? (String) r : "";
    }

    public static boolean isAvailable() {
        return gpuBusyPercent() >= 0f;
    }
}
