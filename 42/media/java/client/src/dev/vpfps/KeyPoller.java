package dev.vpfps;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Java-side keyboard polling for the overlay toggle key.
 *
 * WHY: with PZ_Optimization installed, its own Java-side F9 overlay handler
 * consumes the key before the game's Lua key events fire - so a Lua
 * OnKeyStartPressed listener never sees F9 (the F8 panel works because PZO
 * does not eat that key). Polling the keyboard state directly from the
 * frame loop (GameWindow.frameStep runs on the input-owning thread) works
 * regardless of who consumes events.
 *
 * Implementation: org.lwjglx.input.Keyboard.isKeyDown(int) (B42's LWJGL
 * compatibility layer) polled once per frame with edge detection, plus a
 * text-entry guard through zombie.core.input.Input.isDoingTextEntry() so
 * typing F9 in a text field never toggles the overlay.
 *
 * The key code comes from Config.overlayKey (default 67 = F9), which the
 * ModOptions keybind pushes to the Java side through the bridge.
 */
public final class KeyPoller {

    private static final Guard guard = new Guard("key-poller", 5);

    private static Method isKeyDown;
    private static Object inputInstance;
    private static Method isDoingTextEntry;
    private static boolean init;

    private static boolean lastDown;

    private KeyPoller() {}

    /** Called once per frame from FrameGovernor.onFrameStart. */
    static void poll() {
        guard.run(KeyPoller::pollOnce);
    }

    private static void pollOnce() throws Exception {
        if (!init) {
            init = true;
            Class<?> kb = Class.forName("org.lwjglx.input.Keyboard", false,
                    KeyPoller.class.getClassLoader());
            isKeyDown = kb.getMethod("isKeyDown", int.class);
            try {
                Class<?> input = Class.forName("zombie.core.input.Input", false,
                        KeyPoller.class.getClassLoader());
                Field instance = input.getDeclaredField("instance");
                instance.setAccessible(true);
                inputInstance = instance.get(null);
                isDoingTextEntry = input.getMethod("isDoingTextEntry");
            } catch (Throwable ignored) {
                // text-entry guard is optional
            }
            Log.info("key poller bound (overlayKey=" + Config.overlayKey + ")");
        }

        // never toggle while the user is typing in a text field
        if (isDoingTextEntry != null && inputInstance != null) {
            Object typing = isDoingTextEntry.invoke(inputInstance);
            if (Boolean.TRUE.equals(typing)) {
                lastDown = false;
                return;
            }
        }

        boolean down = (Boolean) isKeyDown.invoke(null, Config.overlayKey);
        if (down && !lastDown) {
            // rising edge: toggle the overlay (Java side, no Lua involved)
            Config.overlay = !Config.overlay;
            Config.save();
            Log.info("overlay toggled to " + Config.overlay + " (key "
                    + Config.overlayKey + ", Java-polled)");
        }
        lastDown = down;
    }
}
