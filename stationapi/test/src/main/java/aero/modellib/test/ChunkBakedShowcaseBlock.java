package aero.modellib.test;

import aero.modellib.Aero_MeshChunkBaker;
import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.client.texture.atlas.Atlas;
import net.modificationstation.stationapi.api.template.block.TemplateBlock;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Showcase for the v0.2.5 chunk-baked rendering path. Pure block — no
 * BlockEntity. The mesh is registered with {@link Aero_MeshChunkBaker} at
 * construction time, and {@link aero.modellib.mixin.BlockRenderManagerChunkBakeMixin}
 * intercepts the chunk-build dispatch to emit triangles directly into the
 * active chunk Tessellator.
 *
 * <p>Compare the cost-per-block in the stress test world:
 * <ul>
 *   <li><strong>BE renderer path</strong> (motors, crystals, etc.) — every
 *       visible block runs through {@code BlockEntityRenderDispatcher}
 *       every frame. Each block costs a frustum cull, a matrix push, a
 *       translate, 4× display-list call (or 4× tess.draw for animated),
 *       and a matrix pop.</li>
 *   <li><strong>Chunk-baked path</strong> (this block) — geometry is
 *       emitted once into the chunk vertex buffer at chunk rebuild and
 *       replayed for free until the chunk is dirtied. Per-frame cost: 0.</li>
 * </ul>
 *
 * <p>Texture: vanilla cobblestone. Atlas UV bounds are resolved lazily on
 * first render via the StationAPI sprite lookup (vanilla's hardcoded
 * "tile = textureId / 16" math is wrong for StationAPI's dynamically-packed
 * atlas — sprites land at runtime-determined positions).
 */
public class ChunkBakedShowcaseBlock extends TemplateBlock {

    public static final Aero_MeshModel MODEL =
        Aero_ObjLoader.load("/models/ChunkBakedShowcase.obj");

    public ChunkBakedShowcaseBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);

        // Lazy UV lookup: at first render, ask the atlas for cobblestone's
        // actual sprite bounds. Cached after the first call.
        final int textureIndex = Block.COBBLESTONE.textureId;
        Aero_MeshChunkBaker.register(this.id, MODEL, () -> {
            Atlas.Sprite sprite = this.getAtlas().getTexture(textureIndex);
            return new float[] {
                (float) sprite.getStartU(), (float) sprite.getEndU(),
                (float) sprite.getStartV(), (float) sprite.getEndV()
            };
        });
    }

    /**
     * Render type 0 keeps the block in the chunk-build dispatch — that's
     * what triggers our HEAD-inject mixin. Vanilla never actually walks
     * past the inject (we cancel and return), so the value just has to
     * be {@code != -1}.
     */
    @Override
    public int getRenderType() {
        return 0;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public boolean isFullCube() {
        return false;
    }
}
