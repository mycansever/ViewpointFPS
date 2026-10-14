package dev.vpfps;

import java.lang.reflect.Method;

/**
 * Viewpoint FPS Optimization v1.0.0 - entry point, loaded by ZombieBuddy
 * (javaPkgName=dev.vpfps, jar at media/java/client/build/libs/vpfps.jar).
 *
 * Boot sequence (all fail-soft, no exception escapes main):
 *   1. load the configuration (Zomboid/vpfps.properties)
 *   2. start the hot-reload watcher thread
 *   3. expose dev.vpfps.Bridge to Lua as the global "VPFPSBridge"
 *
 * The heavy lifting happens later, driven by the @Patch hooks
 * (Patch_GameWindow, Patch_FP, Patch_ChunkBuilds, Patch_IngameState):
 * they call into FrameGovernor / MeshBudget / ViewpointBridge on the
 * live frame loop.
 */
public final class Main {

    public static final String VERSION = "1.0.0";
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

            exposeLuaBridge();
        } catch (Throwable t) {
            Log.error("boot failed: " + t);
        }
    }

    /**
     * Expose dev.vpfps.Bridge to the game's Lua as global "VPFPSBridge"
     * using Project Zomboid's own ClassExposer. If the Lua environment is
     * not up yet (ZombieBuddy loads Java mods early) the call may fail -
     * that is fine, the Java side runs standalone and the properties file
     * remains the control surface.
     */
    private static void exposeLuaBridge() {
        Guard guard = new Guard("lua-expose", 1);
        guard.run(() -> {
            Class<?> luaManager = Class.forName("zombie.LuaManager");
            Object exposer = luaManager.getField("exposer").get(null);
            Method expose = exposer.getClass()
                    .getMethod("ExposeToLua", Class.class, String.class);
            Object ok = expose.invoke(exposer, Bridge.class, "VPFPSBridge");
            Log.info("Lua bridge exposed as VPFPSBridge (result=" + ok + ")");
        });
    }
}
