package aero.modellib.test;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.modificationstation.stationapi.api.template.block.TemplateBlockWithEntity;
import net.modificationstation.stationapi.api.util.Identifier;

import aero.modellib.animation.graph.Aero_AnimationGraph;

/** Showcase: Aero_AnimationGraph Blend1D between slow/fast spin clips. */
public class GraphPoweredBlock extends TemplateBlockWithEntity {

    public GraphPoweredBlock(Identifier id) {
        super(id, Material.STONE);
        setHardness(1.0f);
        setTranslationKey(id);
    }

    @Override protected BlockEntity createBlockEntity() { return new GraphPoweredBlockEntity(); }
    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaque() { return false; }
}
