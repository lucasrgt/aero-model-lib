package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/** Showcase: format_version 1.1 morph_targets blend per-vertex by weight. */
public class MorphCrystalBlock extends TemplateBlockWithEntity {

    public MorphCrystalBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override protected BlockEntity createBlockEntity() { return new MorphCrystalBlockEntity(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaque() { return false; }
}
