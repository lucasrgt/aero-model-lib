package aero.modellib.mixin;

import aero.modellib.Aero_PalettedContainerCacheScope;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the opt-in PalettedContainer cache only around vanilla chunk rebuilds.
 */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderPalettedCacheScopeMixin {

    @Inject(method = "rebuild", at = @At("HEAD"), require = 0, expect = 0)
    private void aero_modellib_beginPalettedCacheScope(CallbackInfo ci) {
        Aero_PalettedContainerCacheScope.beginChunkBuild();
    }

    @Inject(method = "rebuild", at = @At("RETURN"), require = 0, expect = 0)
    private void aero_modellib_endPalettedCacheScope(CallbackInfo ci) {
        Aero_PalettedContainerCacheScope.endChunkBuild();
    }
}
