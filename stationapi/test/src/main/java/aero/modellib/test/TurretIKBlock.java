package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/** Showcase: 3-bone CCD chain aiming the turret tip at an orbiting target. */
public class TurretIKBlock extends TemplateBlockWithEntity {

    public TurretIKBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override protected BlockEntity createBlockEntity() { return new TurretIKBlockEntity(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaque() { return false; }
}
