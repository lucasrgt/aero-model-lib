package aero.modellib.mixin;

import net.modificationstation.stationapi.impl.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-instance 4-entry FIFO cache around
 * {@link PalettedContainer#get(int)}. Targets the {@code 34%} of
 * stress-test CPU time the JFR profile attributed to that single
 * method — chunk meshing reads each block plus its 6 neighbours plus
 * AO neighbours in a tight loop with very high spatial locality, so a
 * small ring-buffer cache hits ≥30% of the calls.
 *
 * <h2>How it composes</h2>
 * <p>{@code PalettedContainer.get(int)} unpacks bits from the storage
 * (a {@code long[]}) and translates the palette index through a
 * {@code Palette<T>} (often a {@code LithiumHashPalette} backed by a
 * HashMap). Both halves are short but called millions of times per
 * chunk rebuild. Every cache hit skips both halves; misses fall through
 * to the original method body and the result is stored.
 *
 * <h2>Invalidation</h2>
 * <p>The container's {@code data} field is volatile and gets replaced
 * on palette resize, chunk repacking, and (indirectly) on
 * {@code PalettedContainer.set}. We compare the cached {@code data}
 * reference on every HEAD invocation; mismatch → flush and miss. No
 * explicit set-hook needed, because writes that mutate the container
 * either swap {@code data} (palette grew) or change the underlying
 * storage int (which our cache values still reference correctly until
 * the next swap — but readers will get a stale BlockState for ≤ a
 * frame, an acceptable price for the chunk mesh path).
 *
 * <p><strong>Caveat</strong>: in the rare case where {@code set(index, value)}
 * mutates the storage in-place (no Data swap, no palette grow), our
 * cache will return the OLD value for that index until evicted by FIFO
 * pressure (4 misses) or a Data swap. For chunk meshing this can't
 * matter — the chunk has already been rebuilt with the new state by the
 * time it's queried. For runtime block updates during gameplay, a stale
 * read could mis-render one frame. Toggle off via
 * {@code -Daero.palettedcache=false} if downstream finds a regression.
 *
 * <h2>Threading</h2>
 * <p>Beta 1.7.3 chunk meshing is single-threaded; the cache fields are
 * non-volatile because the worst case under contention is "wrong
 * BlockState briefly returned from a different thread's call site",
 * which the next miss recovers from. No torn-write hazards because
 * the cache fields are independent (never read together as a structure).
 */
@Mixin(value = PalettedContainer.class, remap = false)
public abstract class PalettedContainerCacheMixin<T> {

    @Shadow private volatile PalettedContainer.Data<T> data;

    /**
     * <strong>Default OFF</strong> in v3.0. Bench measurements showed the
     * @Inject HEAD/RETURN overhead per call (~30 ns × millions of calls in
     * steady state) outweighed the savings from cache hits — net regression
     * of ~20% FPS in the stress test (160 → 120-130 FPS sustained).
     * The cache <em>was</em> intended to help world-entry chunk-meshing
     * (where the call rate spikes for a few seconds), but the steady-state
     * cost is too high to justify a default-on optimization. Opt in with
     * {@code -Daero.palettedcache=true} to A/B test on your scene.
     *
     * <p>Future direction: cache at the CALLER level (chunk-meshing
     * entry point) instead of the {@code get(int)} call site, so the
     * overhead is paid once per chunk-build instead of once per face-cull
     * lookup. Tracked as v3.x candidate.
     */
    @Unique
    private static final boolean AERO_CACHE_ENABLED =
        "true".equalsIgnoreCase(System.getProperty("aero.palettedcache"));

    @Unique private int aero$idx0 = -1, aero$idx1 = -1, aero$idx2 = -1, aero$idx3 = -1;
    @Unique private Object aero$val0, aero$val1, aero$val2, aero$val3;
    @Unique private PalettedContainer.Data<T> aero$cachedData;
    @Unique private int aero$evictPtr;

    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void aero_modellib_cachedGet(int index, CallbackInfoReturnable<T> cir) {
        if (!AERO_CACHE_ENABLED) return;
        PalettedContainer.Data<T> currentData = this.data;
        if (currentData != aero$cachedData) {
            // Storage swap — palette resized, repacked, or replaced. Flush.
            aero$cachedData = currentData;
            aero$idx0 = aero$idx1 = aero$idx2 = aero$idx3 = -1;
            return;
        }
        // Linear probe — branch predictor learns the locality pattern.
        if (index == aero$idx0) { @SuppressWarnings("unchecked") T v = (T) aero$val0; cir.setReturnValue(v); return; }
        if (index == aero$idx1) { @SuppressWarnings("unchecked") T v = (T) aero$val1; cir.setReturnValue(v); return; }
        if (index == aero$idx2) { @SuppressWarnings("unchecked") T v = (T) aero$val2; cir.setReturnValue(v); return; }
        if (index == aero$idx3) { @SuppressWarnings("unchecked") T v = (T) aero$val3; cir.setReturnValue(v); return; }
    }

    @Inject(method = "get(I)Ljava/lang/Object;", at = @At("RETURN"), require = 0, expect = 0)
    private void aero_modellib_storeMiss(int index, CallbackInfoReturnable<T> cir) {
        if (!AERO_CACHE_ENABLED) return;
        // Only fires on cache miss — HEAD inject's setReturnValue cancels
        // the original method body, so RETURN won't fire on hit.
        T value = cir.getReturnValue();
        switch (aero$evictPtr & 3) {
            case 0: aero$idx0 = index; aero$val0 = value; break;
            case 1: aero$idx1 = index; aero$val1 = value; break;
            case 2: aero$idx2 = index; aero$val2 = value; break;
            case 3: aero$idx3 = index; aero$val3 = value; break;
        }
        aero$evictPtr++;
    }
}
