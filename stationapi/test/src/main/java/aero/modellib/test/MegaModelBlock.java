package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Test block: just a holder for the BlockEntity that hosts the renderer.
 * Has no inventory, no behaviour — places a blank cube in world; the
 * actual visual is drawn by {@link MegaModelBlockEntityRenderer}.
 */
public class MegaModelBlock extends TemplateBlockWithEntity {

    public MegaModelBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        // StationAPI overload that wires the translation key to the block's
        // namespace+path automatically; the lang file uses "tile.@.<path>.name"
        // where "@" is replaced with the namespace at runtime.
        setTranslationKey(id);
    }

    @Override
    protected BlockEntity createBlockEntity() {
        return new MegaModelBlockEntity();
    }

    // -1 tells the standard BlockRenderer to skip the cube body — the
    // BlockEntityRenderer is the only thing we want visible in the world.
    @Override
    public int getRenderType() {
        return -1;
    }

    // isOpaque=false stops vanilla MC from treating the block as a solid
    // light-blocker. Without this, sky light is occluded above the block
    // and neighbour blocks render their hidden faces (looks like X-ray
    // shadows around the model).
    @Override
    public boolean isOpaque() {
        return false;
    }
}
