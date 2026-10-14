package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;

/**
 * Draws the performance overlay after the in-game state has rendered.
 * zombie.gameStates.IngameState.render is on Build 42.21's stack traces;
 * the overlay itself is fully fail-soft (see Overlay).
 */
@Patch(className = "zombie.gameStates.IngameState", methodName = "render")
public class Patch_IngameState {

    @Patch.OnExit
    public static void exit() {
        Overlay.render();
    }
}
