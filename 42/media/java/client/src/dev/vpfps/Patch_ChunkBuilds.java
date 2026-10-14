package dev.vpfps;

import me.zed_0xff.zombie_buddy.Patch;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.concurrent.Callable;

/**
 * The mesh-budget gate on Viewpoint's chunk-mesh rebuild.
 *
 * viewpoint.world.ChunkBuilds.rebuild (alpha-0.1.6) builds the render mesh
 * for one chunk. When many chunks stream in at once (driving through town,
 * spinning the camera) these rebuilds stack into multi-frame stalls - the
 * exact problem PZ_Optimization's bakeScheduler solves for the isometric
 * renderer.
 *
 * This is a delegation-mode patch (isAdvice=false): we decide per call
 * whether the original runs at all. Over this frame's budget -> the chunk
 * is skipped and rebuilds on a later frame; under budget -> run it and
 * deduct the MEASURED cost from the bucket (self-calibrating).
 *
 * Kill switch: meshBudgetEnabled=false makes MeshBudget.tryBegin() always
 * return true, i.e. byte-for-byte stock behaviour.
 */
@Patch(className = "viewpoint.world.ChunkBuilds", methodName = "rebuild", isAdvice = false)
public class Patch_ChunkBuilds {

    @RuntimeType
    public static Object intercept(@SuperCall Callable<Object> original) throws Exception {
        if (!MeshBudget.tryBegin()) {
            return null; // over this frame's budget - Viewpoint retries the chunk later
        }
        long t0 = System.nanoTime();
        try {
            return original.call();
        } finally {
            MeshBudget.end(t0);
        }
    }
}
