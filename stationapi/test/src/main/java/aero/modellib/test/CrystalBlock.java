package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Test block exercising rotation + scale + position channels at once.
 * Drives the {@code all_axes} clip on a single octahedron bone.
 */
public class CrystalBlock extends TemplateBlockWithEntity {

    public CrystalBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new CrystalBlockEntity();
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
