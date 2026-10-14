package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Measures Viewpoint's own render pass (viewpoint.FP.renderWorld, called
 * from viewpoint.Hooks.skipIsoCellRender on alpha-0.1.6) and lets the
 * governor know how much of each frame the 3D renderer eats. That number
 * is what steers the GPU-bound vs game-thread-bound decision - the same
 * attribution idea PZ_Optimization uses with its GPU/game-thread busy
 * split, applied to Viewpoint's pipeline.
 */
@Patch(className = "viewpoint.FP", methodName = "renderWorld")
public class Patch_FP {

    @Patch.OnEnter
    public static void enter() {
        FrameGovernor.onVpRenderStart();
    }

    @Patch.OnExit
    public static void exit() {
        FrameGovernor.onVpRenderEnd();
    }
}
