package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/** Showcase: per-bone uv_offset channel scrolls texture across a flat plate. */
public class ConveyorBlock extends TemplateBlockWithEntity {

    public ConveyorBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override protected BlockEntity createBlockEntity() { return new ConveyorBlockEntity(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaque() { return false; }
}
