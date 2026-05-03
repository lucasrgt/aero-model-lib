package aero.modellib.test.mixin;

import aero.modellib.Aero_FrameSpikeLogger;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Test-mod-only mixin that provides two benchmark conveniences:
 * <ul>
 *   <li>{@code -Daero.benchmark.skipNonForcedSaves=true} cancels vanilla's
 *       periodic autosave so dev playtest sessions don't get the recurring
 *       30-70 ms hitch from chunk-batch writes. Ctrl+S and the save-on-exit
 *       path stay alive (they pass {@code force=true}).</li>
 *   <li>{@code -Daero.benchmark.skipAllSaves=true} cancels every save call,
 *       even forced ones. Use only when you accept losing world progress —
 *       e.g. throwaway benchmark worlds.</li>
 * </ul>
 *
 * <p>Lives in the test mod (not in {@code aero-model-lib}) because cancelling
 * vanilla world saves has no business in a published model-rendering library.
 * The benchmark feature is intentionally only available when running this
 * test mod, which is what the lib uses to produce its own perf numbers.
 *
 * <p>The save-timing calls into {@link Aero_FrameSpikeLogger} stay (the
 * logger lives in the lib for the moment so the production opt-in budget /
 * pacer / governor can consume timing data through it).
 */
@Mixin(World.class)
public abstract class WorldSaveSpikeMixin {

    private static final boolean AERO_BENCHMARK_SKIP_NON_FORCED_SAVES =
        Boolean.getBoolean("aero.benchmark.skipNonForcedSaves");
    private static final boolean AERO_BENCHMARK_SKIP_ALL_SAVES =
        Boolean.getBoolean("aero.benchmark.skipAllSaves");

    @Inject(
        method = "saveWithLoadingDisplay(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aeroTest_beginWorldSave(boolean force,
                                         LoadingDisplay loadingDisplay,
                                         CallbackInfo ci) {
        if (AERO_BENCHMARK_SKIP_ALL_SAVES
            || (AERO_BENCHMARK_SKIP_NON_FORCED_SAVES && !force)) {
            Aero_FrameSpikeLogger.skipWorldSave();
            ci.cancel();
            return;
        }
        Aero_FrameSpikeLogger.beginWorldSave();
    }

    @Inject(
        method = "saveWithLoadingDisplay(ZLnet/minecraft/client/gui/screen/LoadingDisplay;)V",
        at = @At("TAIL")
    )
    private void aeroTest_endWorldSave(boolean force,
                                       LoadingDisplay loadingDisplay,
                                       CallbackInfo ci) {
        Aero_FrameSpikeLogger.endWorldSave();
    }
}
