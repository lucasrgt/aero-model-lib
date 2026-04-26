package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Showcase block for the combined ADDITIVE blend + ProceduralPose features.
 * Reuses Crystal.obj/anim, but renders the mesh with additive blending
 * (glow halo) and adds a continuous procedural Y-spin on top of whatever
 * keyframe motion the Crystal clip already carries — so both features
 * are validated visually at once.
 */
public class PlasmaCrystalBlock extends TemplateBlockWithEntity {

    public PlasmaCrystalBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new PlasmaCrystalBlockEntity();
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }
}
