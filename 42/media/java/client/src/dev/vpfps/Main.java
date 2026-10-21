package dev.vpfps;

import java.lang.reflect.Method;

/**
 * Viewpoint FPS Optimization - entry point, loaded by ZombieBuddy
 * (javaPkgName=dev.vpfps, jar at media/java/client/build/libs/vpfps.jar).
 *
 * Boot sequence (all fail-soft, no exception escapes main):
 *   1. load the configuration (Zomboid/vpfps.properties)
 *   2. start the hot-reload watcher thread
 *   3. (fallback) register Bridge with ZombieBuddy's Exposer
 *
 * The Lua bridge is exposed through the OFFICIAL ZombieBuddy mechanism:
 * Bridge is annotated @Exposer.LuaClass(name = "VPFPSBridge"), which
 * ZombieBuddy discovers while scanning this mod's package and registers
 * with the game's Lua engine (B42's zombie.Lua.LuaManager). The manual
 * call below is only a belt-and-braces fallback for older ZombieBuddy
 * builds without annotation discovery - Exposer.exposeClass() dedupes
 * through its g_exposed_classes registry, so double registration is
 * harmless.
 *
 * The heavy lifting happens later, driven by the @Patch hooks
 * (Patch_GameWindow, Patch_FP, Patch_FPCapture, Patch_IngameState):
 * they call into FrameGovernor / ViewpointKnobs on the live frame loop.
 */
public final class Main {

    public static final String VERSION = "3.0.2";
    public static final String TARGET_GAME = "Project Zomboid Build 42.21";
    public static final String TARGET_VIEWPOINT = "Project Viewpoint (Workshop 3809306528)";

    private Main() {}

    public static void main(String[] args) {
        try {
            Log.info("Viewpoint FPS Optimization v" + VERSION + " booting");
            Log.info("target: " + TARGET_GAME + " + " + TARGET_VIEWPOINT);

            Config.load();
            Log.info("config: " + Config.describe());

            Thread watcher = new Thread(Config::watchLoop, "VPFPS-ConfigWatch");
            watcher.setDaemon(true);
            watcher.start();

            registerWithExposer();
        } catch (Throwable t) {
            Log.error("boot failed: " + t);
        }
    }

    /**
     * Belt-and-braces fallback: ask ZombieBuddy's own Exposer to expose
     * Bridge as the Lua global "VPFPSBridge". With @Exposer.LuaClass on
     * Bridge this is normally already done by annotation discovery; the
     * manual registration dedupes and covers older ZombieBuddy builds.
     * Never throws - a failure just means the annotation path must carry it.
     */
    private static void registerWithExposer() {
        Guard guard = new Guard("lua-expose", 1);
        guard.run(() -> {
            Class<?> exposer = Class.forName("me.zed_0xff.zombie_buddy.Exposer");
            Method expose = exposer.getMethod("exposeClass", Class.class, String.class);
            expose.invoke(null, Bridge.class, "VPFPSBridge");
            Log.info("Bridge registered with ZombieBuddy Exposer as VPFPSBridge");
        });
    }
}
