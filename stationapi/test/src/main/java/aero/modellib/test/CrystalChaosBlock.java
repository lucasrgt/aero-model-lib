package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Edge-case stress test: simultaneous rotation on every axis (X+Y+Z),
 * non-uniform scale, and a position trajectory that wanders through all
 * three spatial axes — every animation channel is driven by a different
 * frequency to maximize the chance of catching interpolation or
 * pivot-application bugs.
 */
public class CrystalChaosBlock extends TemplateBlockWithEntity {

    public CrystalChaosBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new CrystalChaosBlockEntity();
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
