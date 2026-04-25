package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Visual fixture for the new easing curves. The OBJ has 12 cubes laid out
 * 4-wide × 3-deep, each animating Y-position with a different easing — at
 * a glance you can see ease-in-back's overshoot, ease-out-elastic's
 * springy settle, ease-out-bounce's stair-step, etc.
 */
public class EasingShowcaseBlock extends TemplateBlockWithEntity {

    public EasingShowcaseBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new EasingShowcaseBlockEntity();
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
