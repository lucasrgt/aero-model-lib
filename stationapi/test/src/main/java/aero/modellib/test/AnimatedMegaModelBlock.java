package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Animated counterpart of {@link MegaModelBlock} — same OBJ mesh,
 * but the renderer drives keyframe animation via Aero_AnimationState.
 */
public class AnimatedMegaModelBlock extends TemplateBlockWithEntity {

    public AnimatedMegaModelBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new AnimatedMegaModelBlockEntity();
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
