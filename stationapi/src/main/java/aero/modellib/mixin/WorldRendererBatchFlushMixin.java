package aero.modellib.mixin;

import aero.modellib.Aero_AnimatedBatcher;
import aero.modellib.Aero_BECellRenderer;
import aero.modellib.Aero_ChunkCompileBudget;
import aero.modellib.Aero_FrameSpikeLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import aero.modellib.util.Aero_SoundCoalesce;

/**
 * Drains the {@link Aero_AnimatedBatcher} queue at the tail of vanilla's
 * {@code WorldRenderer.renderEntities}.
 *
 * <p>{@code renderEntities} is the pass that iterates loaded entities + block
 * entities and dispatches them to their renderers. modellib's BERs queue
 * their renders into {@code Aero_AnimatedBatcher} instead of running
 * immediately; this mixin's TAIL inject runs once after the vanilla
 * iteration completes, drains the queue, and emits batched draws.
 *
 * <p>Render-pass ordering: BEs typically render in the entity pass before
 * the translucent terrain pass. Flushing at the END of {@code renderEntities}
 * keeps batched draws in the same depth-buffer state as the per-instance
 * draws would have been — visually identical to the non-batched path.
 *
 * <p>Failsafe: if this mixin fails to apply (signature drift,
 * conflicting Mixin priority, etc), the batcher's queued items
 * accumulate forever. We compensate by also flushing in
 * {@code GameRendererMixin} at frame start (the next world-render
 * cycle), so a missing flush at most defers the renders by one frame
 * rather than leaking memory.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererBatchFlushMixin {

    @Inject(
        method = "renderEntities(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Culler;F)V",
        at = @At("HEAD"),
        require = 0,
        expect = 0
    )
    private void aeroModelLib_beginRenderEntitiesTiming(
            net.minecraft.util.math.Vec3d cameraPos,
            net.minecraft.client.render.Culler culler,
            float partialTick,
            CallbackInfo ci) {
        Aero_FrameSpikeLogger.beginRenderEntities();
    }

    @Inject(
        method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        expect = 0
    )
    private void aeroModelLib_beginChunkCompileTiming(
            net.minecraft.entity.LivingEntity entity,
            boolean flag,
            CallbackInfoReturnable<Boolean> cir) {
        if (Aero_ChunkCompileBudget.shouldSkip(flag)) {
            Aero_FrameSpikeLogger.skipChunkCompile();
            cir.setReturnValue(Boolean.FALSE);
            return;
        }
        Aero_FrameSpikeLogger.beginChunkCompile();
    }

    @Inject(
        method = "compileChunks(Lnet/minecraft/entity/LivingEntity;Z)Z",
        at = @At("TAIL"),
        require = 0,
        expect = 0
    )
    private void aeroModelLib_endChunkCompileTiming(
            net.minecraft.entity.LivingEntity entity,
            boolean flag,
            CallbackInfoReturnable<Boolean> cir) {
        Aero_FrameSpikeLogger.endChunkCompile();
    }

    @Inject(
        method = "renderChunks(IIID)I",
        at = @At("HEAD"),
        require = 0,
        expect = 0
    )
    private void aeroModelLib_beginRenderChunksTiming(
            int start,
            int end,
            int pass,
            double tickDelta,
            CallbackInfoReturnable<Integer> cir) {
        Aero_FrameSpikeLogger.beginRenderChunks();
    }

    @Inject(
        method = "renderChunks(IIID)I",
        at = @At("TAIL"),
        require = 0,
        expect = 0
    )
    private void aeroModelLib_endRenderChunksTiming(
            int start,
            int end,
            int pass,
            double tickDelta,
            CallbackInfoReturnable<Integer> cir) {
        Aero_FrameSpikeLogger.endRenderChunks();
    }

    @Inject(
        method = "renderEntities(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/Culler;F)V",
        at = @At("TAIL"),
        require = 0,
        expect = 0
    )
    private void aeroModelLib_flushAnimatedBatch(
            net.minecraft.util.math.Vec3d cameraPos,
            net.minecraft.client.render.Culler culler,
            float partialTick,
            CallbackInfo ci) {
        Aero_FrameSpikeLogger.endRenderEntitiesBeforeAeroFlush();
        long spikeStartNs = Aero_FrameSpikeLogger.beginWorldFlush();
        try {
            Aero_AnimatedBatcher.flush();
            Aero_BECellRenderer.flush(cameraPos.x, cameraPos.y, cameraPos.z);
            // Sound coalesce drains here too — cameraPos is already in
            // world coords (from PlayerEntity.getCameraPos), so we pass it
            // straight to the coalescer for distance-to-camera selection.
            // World handle comes from Minecraft#world; if absent (very early
            // init) the dispatcher silently no-ops because the queue is empty.
            Object game = FabricLoader.getInstance().getGameInstance();
            if (game instanceof Minecraft) {
                World w = ((Minecraft) game).world;
                if (w != null) {
                    Aero_SoundCoalesce.flush(
                        cameraPos.x, cameraPos.y, cameraPos.z,
                        new SoundDispatcher(w));
                }
            }
        } finally {
            Aero_FrameSpikeLogger.endWorldFlush(spikeStartNs);
        }
    }

    /**
     * Small adapter that holds a {@link World} reference for the duration
     * of one flush. Allocated once per flush — could be pooled but the
     * cost is one short-lived object per ~16 ms render frame.
     */
    private static final class SoundDispatcher implements Aero_SoundCoalesce.Dispatcher {
        private final World world;
        SoundDispatcher(World world) { this.world = world; }
        @Override
        public void play(double x, double y, double z, String name, float volume, float pitch) {
            world.playSound(x, y, z, name, volume, pitch);
        }
    }
}
