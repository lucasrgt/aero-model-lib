package aero.modellib.mixin;

import aero.modellib.Aero_FrameSpikeLogger;
import aero.modellib.Aero_FramePacer;
import aero.modellib.Aero_AnimationTickBudget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftFrameStageMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void aeroModelLib_beginClientTick(CallbackInfo ci) {
        Aero_AnimationTickBudget.beginClientTick();
        Aero_FrameSpikeLogger.beginClientTick();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void aeroModelLib_endClientTick(CallbackInfo ci) {
        Aero_FrameSpikeLogger.endClientTick();
    }

    @Inject(
        method = "run()V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/Display;update()V"
        )
    )
    private void aeroModelLib_beginDisplayUpdate(CallbackInfo ci) {
        Aero_FramePacer.beforeDisplayUpdate();
        Aero_FrameSpikeLogger.beginDisplayUpdate();
    }

    @Inject(
        method = "run()V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/Display;update()V",
            shift = At.Shift.AFTER
        )
    )
    private void aeroModelLib_endDisplayUpdate(CallbackInfo ci) {
        Aero_FrameSpikeLogger.endDisplayUpdate();
    }

    @Inject(method = "renderProfilerChart(J)V", at = @At("HEAD"))
    private void aeroModelLib_beginProfilerChart(long frameTime, CallbackInfo ci) {
        Aero_FrameSpikeLogger.beginProfilerChart();
    }

    @Inject(method = "renderProfilerChart(J)V", at = @At("TAIL"))
    private void aeroModelLib_endProfilerChart(long frameTime, CallbackInfo ci) {
        Aero_FrameSpikeLogger.endProfilerChart();
    }
}
