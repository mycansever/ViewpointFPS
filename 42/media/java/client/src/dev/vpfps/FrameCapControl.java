package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * In-game FPS limit control for Viewpoint, reverse-engineered from
 * Viewpoint.jar: viewpoint.game.FrameCaps manages the frame cap that the
 * vanilla game would otherwise force from its options file.
 *
 *   FrameCaps.setGameCap(int) / setMenuCap(int)  - apply AND persist
 *   FrameCaps.gameCap() / menuCap()              - current values
 *   FrameCaps.UNCAPPED                           - the "no limit" value
 *   FrameCapsLua already exposes the same to Viewpoint's own Lua
 *
 * Our Bridge exposes this to our Lua panel, so the cap can be changed
 * (or removed) in-game instead of by editing files.
 */
public final class FrameCapControl {

    private static final Guard guard = new Guard("frame-cap", 4);

    private static Method setGameCap;
    private static Method setMenuCap;
    private static Method getGameCap;
    private static Method getMenuCap;
    private static int uncappedValue = Integer.MIN_VALUE;
    private static boolean init;

    private FrameCapControl() {}

    private static void init() throws Exception {
        if (init) return;
        init = true;
        Class<?> c = Class.forName("viewpoint.game.FrameCaps", false,
                FrameCapControl.class.getClassLoader());
        setGameCap = c.getMethod("setGameCap", int.class);
        setMenuCap = c.getMethod("setMenuCap", int.class);
        getGameCap = c.getMethod("gameCap");
        getMenuCap = c.getMethod("menuCap");
        try {
            Field f = c.getField("UNCAPPED");
            uncappedValue = f.getInt(null);
        } catch (Throwable t) {
            uncappedValue = Integer.MIN_VALUE;
        }
        Log.info("FrameCaps bound (UNCAPPED=" + uncappedValue + ")");
    }

    public static boolean isAvailable() {
        Object r = guard.run(() -> {
            init();
            return true;
        }, false);
        return Boolean.TRUE.equals(r);
    }

    /** The value FrameCaps uses for "no limit", or Integer.MIN_VALUE when unknown. */
    public static double uncapped() {
        ensureInit();
        return uncappedValue;
    }

    public static double gameCap() {
        return cap("game");
    }

    public static double menuCap() {
        return cap("menu");
    }

    public static boolean setGameCap(double fps) {
        return setCap("game", fps);
    }

    public static boolean setMenuCap(double fps) {
        return setCap("menu", fps);
    }

    /** Set the in-game cap to FrameCaps' UNCAPPED value. */
    public static boolean uncapGame() {
        ensureInit();
        if (uncappedValue == Integer.MIN_VALUE) return false;
        return setCap("game", uncappedValue);
    }

    private static void ensureInit() {
        guard.run(() -> init());
    }

    private static double cap(String which) {
        Object r = guard.run(() -> {
            init();
            Method m = "game".equals(which) ? getGameCap : getMenuCap;
            if (m == null) return -1.0;
            Object v = m.invoke(null);
            return v instanceof Number n ? n.doubleValue() : -1.0;
        }, -1.0);
        return r instanceof Number n ? n.doubleValue() : -1.0;
    }

    private static boolean setCap(String which, double fps) {
        Object r = guard.run(() -> {
            init();
            Method m = "game".equals(which) ? setGameCap : setMenuCap;
            if (m == null) return false;
            m.invoke(null, (int) Math.round(fps));
            return true;
        }, false);
        return Boolean.TRUE.equals(r);
    }
}
