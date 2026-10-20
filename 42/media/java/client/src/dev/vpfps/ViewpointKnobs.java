package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct bindings to Project Viewpoint's OWN live settings system.
 *
 * REVERSE-ENGINEERED from Viewpoint.jar (alpha-0.1.6, SHA-256 096bd37d...,
 * opened with REA + javap): Viewpoint exposes its tunables as
 * viewpoint.platform.LiveSettings$Number instances held in public/package
 * static final fields, each carrying min / max / fallback and a public
 * get()/set(float) pair. That means we no longer guess field semantics —
 * we scale each knob INSIDE Viewpoint's own declared range:
 *
 *     target = min + (stock - min) * factor      (factor 1.0 = stock)
 *
 * The stock value is captured at bind time and ratchets upward if the user
 * raises the setting in Viewpoint's own settings window, so the governor
 * never fights a manual choice. factor 1.0 always restores stock exactly.
 *
 * Knob groups follow cost attribution:
 *   CPU (game/worker-thread cost): chunk builds, cooking, walks, far jobs,
 *        bake budgets, GPU upload pacing
 *   GPU (render cost): far reach (draw distance), far detail levels,
 *        corpse model budget
 *
 * Every knob is optional: binding happens lazily (Viewpoint initializes
 * its settings during game start), is retried until it succeeds, and a
 * miss simply leaves that knob out. Nothing here can crash Viewpoint.
 */
public final class ViewpointKnobs {

    public static final String GROUP_CPU = "cpu";
    public static final String GROUP_GPU = "gpu";

    /** One bound LiveSettings$Number. */
    public static final class Knob {
        public final String group;
        public final String id;        // e.g. "ChunkBuilds.BUILD_MAX"
        public final String label;     // e.g. "BUILD_MAX"
        final Object setting;          // LiveSettings$Number instance
        final Method get;
        final Method set;
        final float min;
        final float max;
        float stock;

        private final Guard guard;

        Knob(String group, String id, String label, Object setting,
             Method get, Method set, float min, float max, float stock) {
            this.group = group;
            this.id = id;
            this.label = label;
            this.setting = setting;
            this.get = get;
            this.set = set;
            this.min = min;
            this.max = max;
            this.stock = stock;
            this.guard = new Guard("knob:" + id, 3);
        }

        public Float read() {
            if (guard.isTripped()) return null;
            try {
                return (Float) get.invoke(setting);
            } catch (Throwable t) {
                guard.fail(t);
                return null;
            }
        }

        boolean write(float value) {
            if (guard.isTripped()) return false;
            try {
                set.invoke(setting, value);
                return true;
            } catch (Throwable t) {
                guard.fail(t);
                return false;
            }
        }
    }

    /** group, class, field — all verified against the shipped jar. */
    private static final String[][] DEFINITIONS = {
            // ---- CPU side: build / walk / job budgets ----
            {GROUP_CPU, "viewpoint.world.ChunkBuilds",  "BUILD_MAX"},
            {GROUP_CPU, "viewpoint.world.ChunkBuilds",  "MAX_COOKING"},
            {GROUP_CPU, "viewpoint.world.ChunkWalk",    "AT_ONCE"},
            {GROUP_CPU, "viewpoint.far.FarWorld",       "BUDGET"},
            {GROUP_CPU, "viewpoint.far.FarWorld",       "JOBS"},
            {GROUP_CPU, "viewpoint.far.FarShell",       "JOBS"},
            {GROUP_CPU, "viewpoint.render.FloorBakes",  "BUDGET"},
            {GROUP_CPU, "viewpoint.render.ShellGround", "BUDGET"},
            {GROUP_CPU, "viewpoint.render.FarGpu",      "UPLOAD_MB"},
            {GROUP_CPU, "viewpoint.render.FarGpu",      "CELL_UPLOAD_MB"},
            // ---- GPU side: distance / detail / model budget ----
            {GROUP_GPU, "viewpoint.far.FarWorld",       "REACH"},
            {GROUP_GPU, "viewpoint.far.FarWorld",       "DETAIL_1"},
            {GROUP_GPU, "viewpoint.far.FarWorld",       "DETAIL_2"},
            {GROUP_GPU, "viewpoint.far.FarWorld",       "DETAIL_4"},
            {GROUP_GPU, "viewpoint.models.Corpses",     "LIMIT"},
    };

    private static final List<Knob> bound = new ArrayList<>();
    private static boolean fullyBound;
    private static int bindAttempts;

    private ViewpointKnobs() {}

    /**
     * Try to bind every knob. Viewpoint creates its LiveSettings during
     * game start, so this is retried (cheap reflection) until everything
     * resolves or the attempt cap is hit.
     */
    public static synchronized boolean ensureBound() {
        if (fullyBound || bindAttempts >= 40) return fullyBound;
        bindAttempts++;

        for (String[] def : DEFINITIONS) {
            if (find(def[1] + "." + def[2]) != null) continue; // already bound
            Knob k = bindOne(def[0], def[1], def[2]);
            if (k != null) {
                bound.add(k);
                Log.info("knob bound: [" + k.group + "] " + k.id
                        + "  range " + trim(k.min) + ".." + trim(k.max)
                        + "  stock " + trim(k.stock));
            }
        }
        if (bound.size() == DEFINITIONS.length) {
            fullyBound = true;
            Log.info("all " + bound.size() + " Viewpoint knobs bound");
        } else if (bindAttempts == 1 || bindAttempts % 10 == 0) {
            Log.info("knobs bound so far: " + bound.size() + "/" + DEFINITIONS.length
                    + " (Viewpoint still initializing - will retry)");
        }
        return fullyBound;
    }

    private static Knob bindOne(String group, String className, String fieldName) {
        try {
            Class<?> c = Class.forName(className, false, ViewpointKnobs.class.getClassLoader());
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object setting = f.get(null);
            if (setting == null) return null; // owner class loaded but settings not yet created

            Method get = setting.getClass().getMethod("get");
            Method set = setting.getClass().getMethod("set", float.class);

            Field minF = setting.getClass().getDeclaredField("min");
            Field maxF = setting.getClass().getDeclaredField("max");
            minF.setAccessible(true);
            maxF.setAccessible(true);
            float min = minF.getFloat(setting);
            float max = maxF.getFloat(setting);

            float stock = (Float) get.invoke(setting);
            if (min >= max) return null; // degenerate range - leave alone

            String id = c.getSimpleName() + "." + fieldName;
            return new Knob(group, id, fieldName, setting, get, set, min, max, stock);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Scale every knob of a group inside Viewpoint's declared range.
     * factor 1.0 restores stock; lower leans the knobs toward their min.
     */
    public static synchronized int applyFactor(String group, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        int applied = 0;
        for (Knob k : bound) {
            if (!k.group.equals(group)) continue;
            Float current = k.read();
            if (current != null && current > k.stock) {
                k.stock = current; // user raised it in Viewpoint's own UI - never fight that
            }
            float target = k.min + (k.stock - k.min) * f;
            if (k.write(target)) applied++;
        }
        return applied;
    }

    /** Restore every knob of a group to its stock value. */
    public static synchronized int restore(String group) {
        return applyFactor(group, 1.0f);
    }

    public static synchronized int boundCount()      { return bound.size(); }
    public static synchronized int totalCount()      { return DEFINITIONS.length; }
    public static synchronized boolean isBound()     { return !bound.isEmpty(); }

    public static synchronized int boundCount(String group) {
        int n = 0;
        for (Knob k : bound) if (k.group.equals(group)) n++;
        return n;
    }

    /** Short status for the overlay / panel, e.g. "cpu 10 gpu 5". */
    public static synchronized String statusShort() {
        return "cpu " + boundCount(GROUP_CPU) + " gpu " + boundCount(GROUP_GPU)
                + " bound (" + bound.size() + "/" + DEFINITIONS.length + ")";
    }

    /** Multi-line dump of every knob with its live value, for the panel. */
    public static synchronized String describe() {
        StringBuilder sb = new StringBuilder();
        for (Knob k : bound) {
            if (sb.length() > 0) sb.append('\n');
            Float v = k.read();
            sb.append('[').append(k.group).append("] ").append(k.id)
              .append(": ").append(v != null ? trim(v) : "?")
              .append(" (stock ").append(trim(k.stock))
              .append(", range ").append(trim(k.min)).append('-').append(trim(k.max)).append(')');
        }
        return sb.toString();
    }

    private static Knob find(String id) {
        for (Knob k : bound) {
            if (k.id.equals(id)) return k;
        }
        return null;
    }

    private static String trim(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
