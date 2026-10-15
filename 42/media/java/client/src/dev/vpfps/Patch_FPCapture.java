package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Measures Viewpoint's world-capture pass (viewpoint.FP.captureWorld, the
 * snapshot that runs the chunk walk / mesh preparation on the game thread).
 * Combined with Patch_FP's renderWorld measurement this splits each frame
 * into: world capture (game thread) vs 3D render (GPU feed) vs everything
 * else (vanilla game logic) - the attribution the governor decides on.
 */
@Patch(className = "viewpoint.FP", methodName = "captureWorld")
public class Patch_FPCapture {

    @Patch.OnEnter
    public static void enter() {
        FrameGovernor.onCaptureStart();
    }

    @Patch.OnExit
    public static void exit() {
        FrameGovernor.onCaptureEnd();
    }
}
