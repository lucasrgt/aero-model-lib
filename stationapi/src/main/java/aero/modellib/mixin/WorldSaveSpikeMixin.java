package aero.modellib.mixin;

import aero.modellib.Aero_FrameSpikeLogger;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    private void aeroModelLib_beginWorldSave(boolean force,
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
    private void aeroModelLib_endWorldSave(boolean force,
                                           LoadingDisplay loadingDisplay,
                                           CallbackInfo ci) {
        Aero_FrameSpikeLogger.endWorldSave();
    }
}
