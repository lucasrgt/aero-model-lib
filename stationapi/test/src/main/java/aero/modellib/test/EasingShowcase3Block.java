package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

/** Easing showcase part 3 — circ / bounce / extras + linear/step/catmullrom baselines. */
public class EasingShowcase3Block extends TemplateBlockWithEntity {
    public EasingShowcase3Block(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }
    @Override protected BlockEntity createBlockEntity() { return new EasingShowcase3BlockEntity(); }
    @Override public int getRenderType()                { return -1; }
    @Override public boolean isOpaque()                 { return false; }
}
