package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Defensive, reflection-based bridge into Project Viewpoint
 * (mod id "Viewpoint", Workshop 3809306528, alpha-0.1.x on Build 42.21).
 *
 * Viewpoint is a closed Java mod that gets updated frequently, so this
 * bridge NEVER hard-references its classes. Instead, at first frame it
 * discovers "knobs" (settable numeric fields) by probing candidate class
 * and field names, and reports exactly what it found:
 *
 *   - scale-like knobs (float/double whose current value is in (0,1])
 *     are safe for the governor to adjust (render scale, shadow scale)
 *   - drawDistance is special: it may be an int tile count, so the knob
 *     remembers its STOCK baseline and the governor writes
 *     baseline x factor (never an absolute guess)
 *   - anything else is reported but left alone
 *
 * If a Viewpoint update renames its fields, the related optimization
 * simply reports "unavailable" and the mod keeps running - nothing
 * crashes, nothing misbehaves.
 */
public final class ViewpointBridge {

    /** A discovered, settable numeric field on a Viewpoint class. */
    public static final class Knob {
        public final String name;
        public final String owner;      // e.g. "viewpoint.FP"
        public final String field;      // field name
        final Field refl;
        final Object instance;          // null for static fields
        final boolean scaleLike;        // (0,1] float -> governor-adjustable
        final boolean intOk;            // int/long allowed (drawDistance only)
        float baseline;                 // stock value at discovery (drawDistance); ratchets up if the user raises the mod's own setting

        private final Guard guard;

        Knob(String name, Field refl, Object instance, boolean intOk) {
            this.name = name;
            this.owner = refl.getDeclaringClass().getName();
            this.field = refl.getName();
            this.refl = refl;
            this.instance = instance;
            this.intOk = intOk;
            this.guard = new Guard("knob:" + name, 3);
            boolean scale = false;
            Class<?> t = refl.getType();
            boolean floaty = t == float.class || t == Float.class
                    || t == double.class || t == Double.class;
            if (floaty) {
                Float v = readRaw();
                scale = v != null && v >= 0.05f && v <= 1.0f;
            }
            this.scaleLike = scale;
            Float v = readRaw();
            this.baseline = v != null ? v : 1.0f;
        }

        private Float readRaw() {
            try {
                Object v = refl.get(instance);
                if (v instanceof Number) return ((Number) v).floatValue();
            } catch (Throwable ignored) {
            }
            return null;
        }

        public Float read() {
            if (guard.isTripped()) return null;
            Float v = readRaw();
            if (v == null) guard.fail(new IllegalStateException("unreadable field"));
            return v;
        }

        /** Absolute write. Int fields only accepted when intOk (drawDistance). */
        public boolean write(float value) {
            if (guard.isTripped()) return false;
            try {
                Class<?> t = refl.getType();
                Object boxed;
                if (t == float.class || t == Float.class)          boxed = value;
                else if (t == double.class || t == Double.class)   boxed = (double) value;
                else if ((t == int.class || t == Integer.class)
                        && intOk)                                  boxed = Math.round(value);
                else if ((t == long.class || t == Long.class)
                        && intOk)                                  boxed = (long) value;
                else                                               return false;
                refl.set(instance, boxed);
                return true;
            } catch (Throwable t) {
                guard.fail(t);
                return false;
            }
        }
    }

    // Candidate owners, in priority order. Names derived from Viewpoint's
    // public stack traces (alpha-0.1.6): viewpoint.FP is the renderer
    // facade; the others are plausible settings holders that get probed
    // defensively - a miss costs nothing.
    private static final String[] OWNER_CLASSES = {
            "viewpoint.FP",
            "viewpoint.Settings",
            "viewpoint.Config",
            "viewpoint.VPSettings",
            "viewpoint.RenderSettings",
            "viewpoint.options.Options",
    };

    private static final String[][] KNOBS = {
            {"renderScale",  "renderScale", "resolutionScale", "renderTargetScale",
                             "internalResolutionScale", "uprenderScale", "dynamicScale"},
            {"shadowScale",  "shadowScale", "shadowResolutionScale", "shadowQuality",
                             "shadowResScale"},
            {"drawDistance", "drawDistance", "renderDistance", "viewDistance",
                             "meshDistance", "chunkRadius"},
    };

    /** Knobs allowed to be int/long fields (we multiply their stock baseline). */
    private static final List<String> INT_OK = List.of("drawDistance");

    private static final List<Knob> bound = new ArrayList<>();
    private static final List<String> foundClasses = new ArrayList<>();
    private static boolean initialized;

    private ViewpointBridge() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        for (String cn : OWNER_CLASSES) {
            if (classFor(cn) != null) {
                foundClasses.add(cn);
                Log.info("viewpoint class found: " + cn);
            }
        }
        if (foundClasses.isEmpty()) {
            Log.warn("Project Viewpoint classes not found - is the mod enabled and "
                    + "loaded through ZombieBuddy? The mesh budget, walk throttle and "
                    + "frame stats still work; render-scale/shadow/draw-distance knobs do not.");
        }

        for (String[] knob : KNOBS) {
            boolean intOk = INT_OK.contains(knob[0]);
            Knob k = discover(knob[0], knob, intOk);
            if (k != null) {
                bound.add(k);
                Log.info("knob '" + k.name + "' -> " + k.owner + "." + k.field
                        + (k.scaleLike ? " (governor-adjustable)"
                        : (intOk ? " (baseline " + trim(k.baseline) + ", factor-adjustable)" : " (reported only)")));
            } else {
                Log.info("knob '" + knob[0] + "' not found - related optimization inactive");
            }
        }
        Log.info("capabilities: " + capabilities());
    }

    public static synchronized boolean isPresent() {
        return !foundClasses.isEmpty();
    }

    public static synchronized boolean hasKnob(String name) {
        return find(name) != null;
    }

    /** Governor-safe write: only scale-like knobs are touched. */
    public static synchronized boolean writeKnob(String name, float value) {
        Knob k = find(name);
        if (k == null || !k.scaleLike) return false;
        return k.write(value);
    }

    /**
     * Draw-distance factor write: multiplies the STOCK baseline. The baseline
     * ratchets upward if the user raises Viewpoint's own setting beyond it,
     * so a user change is never fought by the governor.
     */
    public static synchronized boolean applyDrawDistanceFactor(float factor) {
        Knob k = find("drawDistance");
        if (k == null || !k.intOk) return false;
        Float current = k.read();
        if (current != null && current > k.baseline) {
            k.baseline = current; // the user raised Viewpoint's own setting - never fight it
        }
        return k.write(k.baseline * factor);
    }

    public static synchronized Float readKnob(String name) {
        Knob k = find(name);
        return k != null ? k.read() : null;
    }

    /** One-line capability report, e.g. "renderScale=FP.renderScale drawDistance=-". */
    public static synchronized String capabilities() {
        StringBuilder sb = new StringBuilder();
        for (String[] knob : KNOBS) {
            Knob k = find(knob[0]);
            if (sb.length() > 0) sb.append("  ");
            sb.append(knob[0]).append('=');
            sb.append(k != null
                    ? (k.owner.substring(k.owner.lastIndexOf('.') + 1) + "." + k.field
                        + (k.scaleLike || k.intOk ? "*" : ""))
                    : "-");
        }
        return sb.toString();
    }

    public static synchronized String capabilitiesShort() {
        StringBuilder sb = new StringBuilder();
        for (String[] knob : KNOBS) {
            Knob k = find(knob[0]);
            if (k == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(knob[0]).append('=').append(k.owner, k.owner.lastIndexOf('.') + 1, k.owner.length())
              .append('.').append(k.field);
        }
        return sb.toString();
    }

    // ---- discovery ----

    private static Knob discover(String name, String[] fieldNames, boolean intOk) {
        for (String cn : OWNER_CLASSES) {
            Class<?> c = classFor(cn);
            if (c == null) continue;
            for (String fn : fieldNames) {
                Field f = findField(c, fn);
                if (f == null) continue;
                Class<?> t = f.getType();
                boolean floaty = t == float.class || t == Float.class
                        || t == double.class || t == Double.class;
                boolean inty = (t == int.class || t == Integer.class
                        || t == long.class || t == Long.class) && intOk;
                if (!floaty && !inty) continue;
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                Object inst = Modifier.isStatic(f.getModifiers()) ? null : singletonOf(c);
                if (!Modifier.isStatic(f.getModifiers()) && inst == null) continue;
                return new Knob(name, f, inst, intOk);
            }
        }
        return null;
    }

    private static Knob find(String name) {
        for (Knob k : bound) {
            if (k.name.equals(name)) return k;
        }
        return null;
    }

    private static Class<?> classFor(String cn) {
        try {
            // initialize=false: never trigger Viewpoint's static init from here
            return Class.forName(cn, false, ViewpointBridge.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                return k.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        try {
            return c.getField(name);
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }

    /** Locate a singleton instance for instance fields: static field of the same type. */
    private static Object singletonOf(Class<?> c) {
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!c.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(null);
                if (v != null) return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String trim(float v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.format("%.2f", v);
    }
}
