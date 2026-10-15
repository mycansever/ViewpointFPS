package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Idle throttle for Viewpoint's chunk walk (viewpoint.world.ChunkWalk.walk).
 *
 * Viewpoint re-walks its chunk cache every frame - even when the player is
 * standing perfectly still and nothing in the world changed. While moving,
 * that walk is necessary; while stationary (looting, fighting in place,
 * reading, crafting) it is pure wasted game-thread time.
 *
 * This throttle lets the walk run at full rate whenever the player moved
 * more than 0.25 tiles since the last walk, and rate-limits it to one walk
 * per chunkWalkThrottleMs (default 250 ms) otherwise. A hard safety valve
 * forces a walk at least every 800 ms no matter what, so a cached-but-dirty
 * state can never starve.
 *
 * Kill switch: chunkWalkThrottleEnabled=false -> always run (stock).
 */
public final class ChunkWalkThrottle {

    private static final long SAFETY_INTERVAL_MS = 800;

    private static final Guard guard = new Guard("chunkwalk-throttle", 6);

    private static long lastRunNs;
    private static float lastX = Float.NaN;
    private static float lastY = Float.NaN;

    private static Method getInstance;
    private static Field instanceField;
    private static Method getX;
    private static Method getY;
    private static boolean reflectInit;

    private ChunkWalkThrottle() {}

    /** Called from Patch_ChunkWalk before each walk. */
    static boolean shouldRun() {
        if (!Config.enabled || !Config.chunkWalkThrottleEnabled) return true;

        long now = System.nanoTime();
        long sinceLastMs = (now - lastRunNs) / 1_000_000L;

        // hard safety valve: never starve the walk, whatever the movement heuristics say
        if (sinceLastMs > SAFETY_INTERVAL_MS) {
            lastRunNs = now;
            return true;
        }

        float[] pos = playerPos();
        if (pos == null) return true; // cannot read the player -> do not throttle

        boolean moved = Float.isNaN(lastX)
                || Math.abs(pos[0] - lastX) > 0.25f
                || Math.abs(pos[1] - lastY) > 0.25f;

        if (moved || sinceLastMs >= Config.chunkWalkThrottleMs) {
            lastRunNs = now;
            lastX = pos[0];
            lastY = pos[1];
            return true;
        }
        return false;
    }

    /** IsoPlayer position via reflection; null when unavailable (menu, error). */
    private static float[] playerPos() {
        return (float[]) guard.run(() -> {
            if (!reflectInit) initReflect();
            Object player = null;
            if (getInstance != null) {
                player = getInstance.invoke(null);
            } else if (instanceField != null) {
                player = instanceField.get(null);
            }
            if (player == null) return null;
            return new float[]{(Float) getX.invoke(player), (Float) getY.invoke(player)};
        }, null);
    }

    private static void initReflect() throws Exception {
        reflectInit = true;
        Class<?> c = Class.forName("zombie.iso.IsoPlayer", false,
                ChunkWalkThrottle.class.getClassLoader());
        try {
            getInstance = c.getMethod("getInstance");
        } catch (NoSuchMethodException e) {
            try {
                instanceField = c.getDeclaredField("instance");
                instanceField.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
            }
        }
        getX = c.getMethod("getX");
        getY = c.getMethod("getY");
    }
}
