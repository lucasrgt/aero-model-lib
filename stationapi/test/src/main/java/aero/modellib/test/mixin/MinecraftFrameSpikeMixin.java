package aero.modellib.test.mixin;

import aero.modellib.Aero_FrameSpikeLogger;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic-only frame timings that used to live in
 * {@code aero.modellib.mixin.MinecraftFrameStageMixin} but are not needed
 * by any production opt-in feature. They populate
 * {@link Aero_FrameSpikeLogger}'s {@code lastClientTickNs} and
 * {@code lastProfilerChartNs} so the spike log line carries accurate
 * per-stage breakdowns when {@code -Daero.spikelog=true}. Lives in the
 * test mod because cancelling/instrumenting these vanilla methods has no
 * place in a published model-rendering library.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameSpikeMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void aeroTest_endClientTick(CallbackInfo ci) {
        Aero_FrameSpikeLogger.endClientTick();
    }

    @Inject(method = "renderProfilerChart(J)V", at = @At("HEAD"))
    private void aeroTest_beginProfilerChart(long frameTime, CallbackInfo ci) {
        Aero_FrameSpikeLogger.beginProfilerChart();
    }

    @Inject(method = "renderProfilerChart(J)V", at = @At("TAIL"))
    private void aeroTest_endProfilerChart(long frameTime, CallbackInfo ci) {
        Aero_FrameSpikeLogger.endProfilerChart();
    }
}
