package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/** Showcase: combined uv_offset + uv_scale animates a magic-circle pulse. */
public class SpellCircleBlock extends TemplateBlockWithEntity {

    public SpellCircleBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override protected BlockEntity createBlockEntity() { return new SpellCircleBlockEntity(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaque() { return false; }
}
