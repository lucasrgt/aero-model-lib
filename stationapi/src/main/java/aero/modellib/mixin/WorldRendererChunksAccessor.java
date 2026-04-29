package aero.modellib.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code WorldRenderer.chunks[]} field so
 * {@link aero.modellib.Aero_ChunkVisibility} can iterate it once per
 * frame to snapshot the {@code inFrustum} flags.
 *
 * <p>Pure accessor mixin — no behavior change. If the field is renamed
 * in a future StationAPI / Yarn update, this mixin's {@code @Accessor}
 * binding silently fails ({@code expect=0, require=0}); the snapshot
 * call falls back to "no visibility data, render everything" so the
 * downstream cull becomes a no-op rather than a black-world regression.
 */
@Mixin(WorldRenderer.class)
public interface WorldRendererChunksAccessor {

    @Accessor("chunks")
    ChunkBuilder[] aero_modellib_getChunks();
}
