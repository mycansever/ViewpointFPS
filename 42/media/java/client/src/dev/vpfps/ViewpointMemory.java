package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game RAM / VRAM control for Viewpoint, reverse-engineered from
 * Viewpoint.jar: viewpoint.platform.Pool is Viewpoint's memory manager.
 * Every pool (NEAR_MESHES, LIGHT_GRIDS, SHELL, FAR, RELIEF, MODEL_MESHES,
 * CPU, FLOORS, PACKS, IRIS, CORPSES, SOUNDS) carries:
 *
 *   - a LiveSettings$Number `chosen`  (persistent budget in MiB)
 *   - limit(long) / cap() / used()    (live byte limits and usage)
 *
 * We scale every pool's budget INSIDE its own declared min..max range:
 *
 *     target = min + (stock - min) * scale     (1.0 = stock)
 *
 * and apply it immediately through both the persistent setting and the
 * live byte limit. Stock values ratchet upward if the user raises a pool
 * in Viewpoint's own UI. VRAM telemetry (viewpoint.platform.VideoMemory:
 * freeMiB / totalMiB / evictions) feeds the overlay and panel.
 */
public final class ViewpointMemory {

    /** One bound memory pool. */
    public static final class PoolKnob {
        public final String name;
        final Object pool;            // viewpoint.platform.Pool instance
        final Object chosen;          // LiveSettings$Number of the pool
        final Method chosenGet;
        final Method chosenSet;
        final Method limitMethod;
        final float min;
        final float max;
        float stock;

        PoolKnob(String name, Object pool, Object chosen, Method chosenGet, Method chosenSet,
                 Method limitMethod, float min, float max, float stock) {
            this.name = name;
            this.pool = pool;
            this.chosen = chosen;
            this.chosenGet = chosenGet;
            this.chosenSet = chosenSet;
            this.limitMethod = limitMethod;
            this.min = min;
            this.max = max;
            this.stock = stock;
        }

        public Float readMiB() {
            try {
                return (Float) chosenGet.invoke(chosen);
            } catch (Throwable t) {
                return null;
            }
        }

        public Float usedMiB() {
            try {
                Method used = pool.getClass().getMethod("used");
                return used.invoke(pool) instanceof Number n ? n.longValue() / (1024f * 1024f) : null;
            } catch (Throwable t) {
                return null;
            }
        }

        void write(float mib) {
            try {
                chosenSet.invoke(chosen, mib);              // persistent setting
                limitMethod.invoke(pool, (long) (mib * 1024f * 1024f)); // live byte limit
            } catch (Throwable ignored) {
            }
        }
    }

    private static final String[] POOL_FIELDS = {
            "NEAR_MESHES", "LIGHT_GRIDS", "SHELL", "FAR", "RELIEF", "MODEL_MESHES",
            "CPU", "FLOORS", "PACKS", "IRIS", "CORPSES", "SOUNDS",
    };

    private static final List<PoolKnob> pools = new ArrayList<>();
    private static boolean poolInit;
    private static final Guard guard = new Guard("vp-memory", 4);

    // ---- VideoMemory telemetry ----
    private static Method vmemFree;
    private static Method vmemTotal;
    private static Method vmemEvictions;
    private static boolean vmemInit;
    private static final Guard vmemGuard = new Guard("vp-vram", 4);

    private ViewpointMemory() {}

    public static synchronized List<PoolKnob> pools() {
        return pools;
    }

    /** Bind every pool; safe to call repeatedly (no-ops once done). */
    public static synchronized boolean ensureBound() {
        if (poolInit) return !pools.isEmpty();
        poolInit = true;
        try {
            Class<?> c = Class.forName("viewpoint.platform.Pool", false,
                    ViewpointMemory.class.getClassLoader());
            for (String fieldName : POOL_FIELDS) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object pool = f.get(null);
                    if (pool == null) continue;

                    Field nameF = c.getDeclaredField("name");
                    nameF.setAccessible(true);
                    String name = (String) nameF.get(pool);
                    if (name == null) name = fieldName;

                    Field chosenF = c.getDeclaredField("chosen");
                    chosenF.setAccessible(true);
                    Object chosen = chosenF.get(pool);
                    if (chosen == null) continue;

                    Method chosenGet = chosen.getClass().getMethod("get");
                    Method chosenSet = chosen.getClass().getMethod("set", float.class);
                    Method limitMethod = c.getMethod("limit", long.class);

                    Field minF = chosen.getClass().getDeclaredField("min");
                    Field maxF = chosen.getClass().getDeclaredField("max");
                    minF.setAccessible(true);
                    maxF.setAccessible(true);
                    float min = minF.getFloat(chosen);
                    float max = maxF.getFloat(chosen);
                    if (min >= max) continue;

                    float stock = (Float) chosenGet.invoke(chosen);
                    pools.add(new PoolKnob(name, pool, chosen, chosenGet, chosenSet,
                            limitMethod, min, max, stock));
                    Log.info("memory pool bound: " + name + "  range " + trim(min) + ".."
                            + trim(max) + " MiB  stock " + trim(stock));
                } catch (Throwable ignored) {
                }
            }
            Log.info("memory pools bound: " + pools.size() + "/" + POOL_FIELDS.length);
        } catch (Throwable t) {
            Log.warn("Pool class not found: " + t);
        }
        return !pools.isEmpty();
    }

    /**
     * Scale every pool budget inside its declared range.
     * scale 1.0 = Viewpoint stock, lower saves memory, higher buys headroom.
     */
    public static synchronized int applyScale(double scale) {
        double s = Math.max(0.1, Math.min(2.0, scale));
        int applied = 0;
        for (PoolKnob p : pools) {
            Float current = p.readMiB();
            if (current != null && current > p.stock) p.stock = current;
            float target = p.min + (float) ((p.stock - p.min) * s);
            p.write(target);
            applied++;
        }
        return applied;
    }

    public static synchronized int restore() {
        return applyScale(1.0);
    }

    // ---- VRAM telemetry (Viewpoint's own sampler) ----

    public static float vramFreeMiB() {
        return vram("free");
    }

    public static float vramTotalMiB() {
        return vram("total");
    }

    public static int vramEvictions() {
        Object r = vmemGuard.run(() -> {
            initVmem();
            if (vmemEvictions == null) return -1;
            return vmemEvictions.invoke(null);
        }, -1);
        return r instanceof Number n ? n.intValue() : -1;
    }

    private static float vram(String which) {
        Object r = vmemGuard.run(() -> {
            initVmem();
            Method m = "free".equals(which) ? vmemFree : vmemTotal;
            if (m == null) return -1f;
            return m.invoke(null);
        }, -1f);
        return r instanceof Number n ? n.floatValue() : -1f;
    }

    private static void initVmem() throws Exception {
        if (vmemInit) return;
        vmemInit = true;
        Class<?> c = Class.forName("viewpoint.platform.VideoMemory", false,
                ViewpointMemory.class.getClassLoader());
        vmemFree = c.getDeclaredMethod("freeMiB");
        vmemFree.setAccessible(true);
        vmemTotal = c.getDeclaredMethod("totalMiB");
        vmemTotal.setAccessible(true);
        vmemEvictions = c.getDeclaredMethod("evictions");
        vmemEvictions.setAccessible(true);
    }

    /** Multi-line pool dump for the panel. */
    public static synchronized String describe() {
        StringBuilder sb = new StringBuilder();
        for (PoolKnob p : pools) {
            if (sb.length() > 0) sb.append('\n');
            Float v = p.readMiB();
            Float used = p.usedMiB();
            sb.append(p.name).append(": ").append(v != null ? trim(v) : "?").append(" MiB")
              .append(used != null ? " (used " + trim(used) + ")" : "")
              .append(" [stock ").append(trim(p.stock)).append(']');
        }
        return sb.toString();
    }

    public static synchronized String statusShort() {
        float free = vramFreeMiB();
        float total = vramTotalMiB();
        String vram = free >= 0 ? String.format("VRAM %.0f/%.0f MiB", total - free, total) : "VRAM ?";
        return vram + " | pools " + pools.size() + " bound";
    }

    private static String trim(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.0f", v);
    }
}
