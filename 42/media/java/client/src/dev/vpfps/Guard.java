package dev.vpfps;

/**
 * Fail-soft circuit breaker. Every risky operation in this mod (reflection
 * into Viewpoint, text drawing, Lua exposure, ...) runs through a Guard.
 * After maxFailures consecutive failures the feature is disabled for the
 * rest of the session and one line is logged. A failing optimization must
 * never take the game down with it.
 */
public final class Guard {

    public interface Body     { void run() throws Exception; }
    public interface Supplier { Object get() throws Exception; }

    private final String name;
    private final int maxFailures;
    private int failures;
    private boolean tripped;
    private String lastError = "";

    public Guard(String name, int maxFailures) {
        this.name = name;
        this.maxFailures = Math.max(1, maxFailures);
    }

    public synchronized boolean isTripped() { return tripped; }

    public synchronized void run(Body body) {
        if (tripped) return;
        try {
            body.run();
        } catch (Throwable t) {
            fail(t);
        }
    }

    public synchronized Object run(Supplier body, Object fallback) {
        if (tripped) return fallback;
        try {
            return body.get();
        } catch (Throwable t) {
            fail(t);
            return fallback;
        }
    }

    public synchronized void fail(Throwable t) {
        failures++;
        lastError = t.getClass().getSimpleName()
                + (t.getMessage() != null ? (": " + t.getMessage()) : "");
        Log.warn("guard '" + name + "' failure #" + failures + " - " + lastError);
        if (failures >= maxFailures) {
            tripped = true;
            Log.warn("guard '" + name + "' TRIPPED - feature disabled until the next launch");
        }
    }

    public synchronized void reset() {
        failures = 0;
        tripped = false;
        lastError = "";
    }

    public synchronized String describe() {
        if (tripped) return name + ": DISABLED (" + lastError + ")";
        return name + ": ok" + (failures > 0 ? " (" + failures + " non-fatal)" : "");
    }
}
