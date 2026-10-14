package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Frame-loop heartbeat. zombie.GameWindow.frameStep exists on Build 42.21
 * (it is on Viewpoint's own stack traces), and one call == one presented
 * frame. Advice-mode hooks (inlined, near-zero overhead):
 *
 *   OnEnter -> FrameGovernor.onFrameStart()  (first-frame init, budget refill)
 *   OnExit  -> FrameGovernor.onFrameEnd()    (frame-time measurement, adaptive tick)
 */
@Patch(className = "zombie.GameWindow", methodName = "frameStep")
public class Patch_GameWindow {

    @Patch.OnEnter
    public static void enter() {
        FrameGovernor.onFrameStart();
    }

    @Patch.OnExit
    public static void exit() {
        FrameGovernor.onFrameEnd();
    }
}
