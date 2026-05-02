package aero.modellib.mixin;

import aero.modellib.Aero_RenderDistance;
import aero.modellib.Aero_ChunkCompileBudget;
import aero.modellib.Aero_FrameSpikeLogger;
import aero.modellib.Aero_FramePacer;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "onFrameUpdate(F)V", at = @At("HEAD"))
    private void aeroModelLib_beginClientFrame(float tickDelta, CallbackInfo ci) {
        Aero_FramePacer.beforeFrame();
        Aero_ChunkCompileBudget.beginFrame();
        Aero_FrameSpikeLogger.beginFrame();
    }

    @Inject(method = "onFrameUpdate(F)V", at = @At("TAIL"))
    private void aeroModelLib_endClientFrame(float tickDelta, CallbackInfo ci) {
        Aero_FrameSpikeLogger.endGameRendererUpdate();
    }

    @Inject(method = "renderWorld(FI)V", at = @At("HEAD"))
    private void aeroModelLib_beginRenderFrame(float tickDelta, int eye, CallbackInfo ci) {
        Aero_FrameSpikeLogger.beginRenderWorld();
        long prepStartNs = Aero_FrameSpikeLogger.beginAeroRenderPrep();
        try {
            Aero_RenderDistance.beginRenderFrame();
        } finally {
            Aero_FrameSpikeLogger.endAeroRenderPrep(prepStartNs);
        }
    }

    @Inject(method = "renderWorld(FI)V", at = @At("TAIL"))
    private void aeroModelLib_endRenderFrame(float tickDelta, int eye, CallbackInfo ci) {
        Aero_FrameSpikeLogger.endRenderWorld();
    }
}
