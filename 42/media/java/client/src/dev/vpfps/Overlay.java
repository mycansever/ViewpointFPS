package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * In-game performance overlay, drawn through Project Zomboid's TextManager
 * (reflection-bound at first draw; Guard keeps it fail-soft).
 *
 * v3 lines:
 *   VPFPS 3.0.0 | ADAPTIVE | target 60 | GPU 73% | PZO: on
 *   FPS 47.3 (1% low 31.2)  frame 21.1 ms  p99 34.8 ms
 *   VP render 14.2 ms (67%)  capture 4.1 ms  |  FAR 3.2ms SHADOW 1.8ms
 *   cpu x0.70  gpu x0.85  |  mem x1.00  VRAM 4210/8192 MiB
 *   last governor action
 *   knob status
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

            float gpuBusy = GpuStats.gpuBusyPercent();
            y = line(fontMedium, y, "VPFPS " + Main.VERSION + "  |  "
                            + Config.governor + "  |  target " + Config.targetFps + " fps"
                            + (gpuBusy >= 0 ? "  |  GPU " + String.format("%.0f%%", gpuBusy) : "")
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
                String passes = GpuStats.topPasses(3);
                y = line(fontSmall, y, String.format(
                        "VP render %.1f ms (%.0f%%)   capture %.1f ms%s",
                        FrameGovernor.vpRenderMean(), FrameGovernor.vpShare() * 100f,
                        FrameGovernor.captureMean(),
                        passes.isEmpty() ? "" : "   |   " + passes),
                        0.80f, 0.85f, 0.90f);

                float vrFree = ViewpointMemory.vramFreeMiB();
                float vrTotal = ViewpointMemory.vramTotalMiB();
                String vram = vrFree >= 0
                        ? String.format("VRAM %.0f/%.0f MiB", vrTotal - vrFree, vrTotal) : "";
                y = line(fontSmall, y, String.format(
                        "cpu x%.2f   gpu x%.2f   |   mem x%.2f%s   |   %s",
                        FrameGovernor.cpuFactor(), FrameGovernor.gpuFactor(),
                        Config.memoryScale, vram.isEmpty() ? "" : "   " + vram,
                        ViewpointKnobs.statusShort()), 0.80f, 0.85f, 0.90f);
            }

            y = line(fontSmall, y, FrameGovernor.lastAction(), 0.60f, 0.65f, 0.70f);
            line(fontSmall, y, "F8 panel  |  " + ViewpointMemory.statusShort(), 0.60f, 0.65f, 0.70f);
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
            if (si != 1 && si != 3) continue;
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
