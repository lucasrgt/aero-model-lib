package aero.modellib.mixin;

import aero.modellib.Aero_MeshChunkBaker;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the chunk-build dispatch for blocks registered via
 * {@link Aero_MeshChunkBaker}. Runs at HEAD with cancellable=true so we
 * short-circuit before vanilla / Arsenic dispatch.
 *
 * <p><strong>Why higher priority than Arsenic.</strong> Arsenic's mixin on
 * the same method (default priority 1000) wraps every block in its
 * {@code BakedModel} pipeline, which is the wrong path for our pre-baked
 * float-triangle data. Setting our priority to 1500 makes the JVM
 * apply our HEAD inject FIRST; once we set the return value, Arsenic's
 * inject is bypassed for the same call.
 *
 * <p>For unregistered blocks (the vast majority — vanilla terrain, mod
 * blocks not using chunk-bake), this method returns immediately without
 * touching the callback, so Arsenic + vanilla render behave normally.
 */
@Mixin(value = BlockRenderManager.class, priority = 500)
public abstract class BlockRenderManagerChunkBakeMixin {

    @Shadow private BlockView blockView;

    @Inject(
        method = "render(Lnet/minecraft/block/Block;III)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void aeroModelLib_renderChunkBaked(Block block, int x, int y, int z,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (block == null) return;
        Aero_MeshChunkBaker.BakedEntry entry = Aero_MeshChunkBaker.get(block.id);
        if (entry == null) return;
        boolean rendered = Aero_MeshChunkBaker.emit(entry, blockView, x, y, z);
        cir.setReturnValue(rendered);
    }
}
