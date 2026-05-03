package aero.modellib.mixin;

import aero.modellib.Aero_FrameSpikeLogger;
import aero.modellib.Aero_FramePacer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import aero.modellib.render.Aero_AnimationTickBudget;

/**
 * Frame-stage hooks that drive the lib's production opt-in pacing /
 * tick-budget systems. Each inject does one of two things:
 *
 * <ul>
 *   <li><b>tick HEAD</b>: open a client-tick window for
 *       {@link Aero_AnimationTickBudget} so the budget can attribute
 *       per-tick animation work. Inert when the budget is off.</li>
 *   <li><b>Display.update HEAD</b>: hand control to
 *       {@link Aero_FramePacer#beforeDisplayUpdate()} so it can issue
 *       {@code Display.sync(targetFps)} immediately before the swap.
 *       Inert when pacing is off.</li>
 *   <li><b>Display.update TAIL</b>: stop the swap timer so the FramePacer's
 *       adaptive backoff has a fresh {@code displayUpdateMs} reading.
 *       Inert when both pacing and the (internal) timing collector
 *       {@link Aero_FrameSpikeLogger} are off.</li>
 * </ul>
 *
 * <p>The previous tick-TAIL and renderProfilerChart hooks were diagnostic
 * only (they fed the spike log). They moved to the test mod when the
 * library stopped shipping pure benchmark mixins.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameStageMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void aeroModelLib_beginClientTick(CallbackInfo ci) {
        Aero_AnimationTickBudget.beginClientTick();
        Aero_FrameSpikeLogger.beginClientTick();
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
}
