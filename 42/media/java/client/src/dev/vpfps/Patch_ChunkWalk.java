package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.concurrent.Callable;

/**
 * Idle throttle gate on Viewpoint's chunk walk.
 *
 * viewpoint.world.ChunkWalk.walk (alpha-0.1.6) re-walks the chunk cache every
 * frame even when the player is standing still - pure wasted game-thread time
 * while looting, fighting in place or crafting. This delegation patch asks
 * ChunkWalkThrottle whether the walk should run at all: full rate while the
 * player moves, rate-limited to chunkWalkThrottleMs while idle, with a hard
 * safety valve that never starves the walk for more than 800 ms.
 *
 * Kill switch: chunkWalkThrottleEnabled=false -> byte-for-byte stock behaviour.
 */
@Patch(className = "viewpoint.world.ChunkWalk", methodName = "walk", isAdvice = false)
public class Patch_ChunkWalk {

    @RuntimeType
    public static Object intercept(@SuperCall Callable<Object> original) throws Exception {
        if (!ChunkWalkThrottle.shouldRun()) {
            return null; // throttled this frame - the next walk picks everything up
        }
        return original.call();
    }
}
