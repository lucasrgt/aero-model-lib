package aero.modellib.mixin;

import aero.modellib.Aero_RenderDistance;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderWorld(FI)V", at = @At("HEAD"))
    private void aeroModelLib_beginRenderFrame(float tickDelta, int eye, CallbackInfo ci) {
        Aero_RenderDistance.beginRenderFrame();
    }
}
