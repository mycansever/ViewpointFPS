package dev.vpfps;

import java.lang.reflect.Field;

/**
 * Presented-frame counter - the number the user's GPU overlay (NVIDIA
 * App/ShadowPlay) shows, as opposed to the game-step rate.
 *
 * WHY: our FrameGovernor measures GameWindow.frameStep cadence (the game
 * step, what PZ_Optimization's overlay also shows), which legitimately
 * reads lower than the frames actually presented to the display when the
 * render thread presents more often than the game steps. Reverse-engineered
 * from Viewpoint.jar: viewpoint.platform.FrameTimes keeps a volatile swap
 * counter (`count`, incremented in swapped()) - counting its delta per
 * second yields the present rate, the same thing external GPU overlays
 * count.
 *
 * Sampled once per frame; the window is ~1 s so the value is stable.
 */
public final class PresentCounter {

    private static final Guard guard = new Guard("present-counter", 3);

    private static Field countField;
    private static boolean init;
    private static long lastCount = -1;
    private static long lastSampleNs;
    private static float presentedFps = -1f;

    private PresentCounter() {}

    /** Called once per frame from FrameGovernor. Returns the latest window's presented fps, or -1. */
    static float sample() {
        return (Float) guard.run(() -> {
            if (!init) {
                init = true;
                Class<?> c = Class.forName("viewpoint.platform.FrameTimes", false,
                        PresentCounter.class.getClassLoader());
                countField = c.getDeclaredField("count");
                countField.setAccessible(true);
            }
            if (countField == null) return -1f;

            long count = countField.getLong(null);
            long now = System.nanoTime();
            if (lastCount < 0) {
                lastCount = count;
                lastSampleNs = now;
                return -1f;
            }
            long dt = now - lastSampleNs;
            if (dt >= 1_000_000_000L) {
                presentedFps = (count - lastCount) * 1_000_000_000f / dt;
                lastCount = count;
                lastSampleNs = now;
            }
            return presentedFps;
        }, -1f);
    }

    public static float presentedFps() {
        return presentedFps;
    }
}
