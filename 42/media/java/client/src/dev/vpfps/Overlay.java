package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * In-game performance overlay, drawn through Project Zomboid's own
 * TextManager (same channel the ZBHelloWorld sample uses).
 *
 * TextManager.DrawString overloads vary between builds (int/float/double
 * coordinates), so the method is resolved ONCE by reflection at first
 * draw and the arguments are converted to whatever signature was found.
 * If nothing usable exists, the overlay trips its guard and disappears -
 * it can never take the renderer down with it.
 *
 * Toggle: F9 (default, rebindable via ModOptions) or overlay=false.
 */
public final class Overlay {

    private static final int X = 10;
    private static final int Y = 36;
    private static final int LINE_H = 15;

    private static final Guard guard = new Guard("overlay", 5);

    private static boolean initDone;
    private static Object textManager;
    private static Method drawString;
    private static Class<?>[] sig;
    private static int strIdx = -1;
    private static Object fontSmall;
    private static Object fontMedium;

    private Overlay() {}

    /** Called from Patch_IngameState at the end of every IngameState.render. */
    public static void render() {
        if (!Config.enabled || !Config.overlay) return;
        guard.run(() -> {
            if (!initDone) init();
            if (drawString == null) return;

            int y = Y;
            y = line(fontMedium, y, "VPFPS " + Main.VERSION + "  |  "
                            + Config.governor + "  |  target " + Config.targetFps + " fps"
                            + (PZOCompat.isDetected() ? "  |  PZO: on" : ""),
                    1.0f, 0.9f, 0.4f);

            float fps = FrameGovernor.fps();
            float t = Config.targetFps;
            float r, g, b;
            if (fps >= t * 0.95f)      { r = 0.35f; g = 0.95f; b = 0.45f; }
            else if (fps >= t * 0.70f) { r = 0.95f; g = 0.80f; b = 0.25f; }
            else                       { r = 0.95f; g = 0.35f; b = 0.30f; }
            y = line(fontSmall, y, String.format(
                    "FPS %.1f (1%% low %.1f)   frame %.1f ms   p99 %.1f ms",
                    fps, FrameGovernor.fpsOnePercentLow(),
                    FrameGovernor.frameMean(), FrameGovernor.frameP99()), r, g, b);

            if (Config.overlayDetail) {
                y = line(fontSmall, y, String.format(
                        "VP render %.1f ms (%.0f%%)   capture %.1f ms   mesh %.1f ms avg   %d skipped",
                        FrameGovernor.vpRenderMean(), FrameGovernor.vpShare() * 100f,
                        FrameGovernor.captureMean(),
                        MeshBudget.stats().mean(), MeshBudget.skippedCount()),
                        0.80f, 0.85f, 0.90f);

                y = line(fontSmall, y, String.format(
                        "scale %.2f   dist %.2fx   shadow %.2f   mesh budget %.1f ms   walk %d ms",
                        FrameGovernor.renderScale(), FrameGovernor.drawDistanceFactor(),
                        FrameGovernor.shadowScale(), MeshBudget.budget(),
                        Config.chunkWalkThrottleMs), 0.80f, 0.85f, 0.90f);
            }

            y = line(fontSmall, y, FrameGovernor.lastAction(), 0.60f, 0.65f, 0.70f);

            String knobs = ViewpointBridge.capabilitiesShort();
            if (!knobs.isEmpty()) {
                line(fontSmall, y, "knobs: " + knobs, 0.60f, 0.65f, 0.70f);
            }
        });
    }

    // ---- reflective text drawing ----

    private static void init() throws Exception {
        initDone = true;
        Class<?> tmClass = Class.forName("zombie.ui.TextManager");
        Field inst = tmClass.getField("instance");
        textManager = inst.get(null);

        Class<?> fontClass = Class.forName("zombie.ui.UIFont");
        for (Object c : fontClass.getEnumConstants()) {
            String n = ((Enum<?>) c).name();
            if ("Small".equals(n))  fontSmall = c;
            if ("Medium".equals(n)) fontMedium = c;
        }
        if (fontSmall == null) fontSmall = fontMedium;

        for (Method m : tmClass.getMethods()) {
            if (!m.getName().equals("DrawString")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 8 || p[0] != fontClass) continue;
            int si = -1;
            for (int i = 1; i < 4; i++) {
                if (p[i] == String.class) { si = i; break; }
            }
            if (si != 1 && si != 3) continue; // (UIFont,x,y,S,rgba) or (UIFont,S,x,y,rgba)
            drawString = m;
            sig = p;
            strIdx = si;
            break;
        }
        if (drawString == null) {
            throw new IllegalStateException("no usable TextManager.DrawString overload found");
        }
        Log.info("overlay bound to " + drawString);
    }

    private static int line(Object font, int y, String text, float r, float g, float b)
            throws Exception {
        int numericSeen = 0;
        Object[] args = new Object[sig.length];
        for (int i = 0; i < sig.length; i++) {
            if (i == 0) {
                args[i] = font;
            } else if (i == strIdx) {
                args[i] = text;
            } else {
                float v;
                if (i < strIdx) {
                    v = (numericSeen++ == 0) ? X : y;
                } else {
                    switch (i - strIdx - 1) {
                        case 0:  v = r;     break;
                        case 1:  v = g;     break;
                        case 2:  v = b;     break;
                        default: v = 1.0f;  break;
                    }
                }
                args[i] = convert(sig[i], v);
            }
        }
        drawString.invoke(textManager, args);
        return y + LINE_H;
    }

    private static Object convert(Class<?> t, float v) {
        if (t == float.class || t == Float.class)   return v;
        if (t == double.class || t == Double.class) return (double) v;
        if (t == int.class || t == Integer.class)   return (int) Math.round(v);
        if (t == long.class || t == Long.class)     return (long) v;
        return null;
    }
}
