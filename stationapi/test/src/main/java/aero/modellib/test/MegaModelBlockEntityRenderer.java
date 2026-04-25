package aero.modellib.test;

import aero.modellib.Aero_MeshModel;
import aero.modellib.Aero_MeshRenderer;
import aero.modellib.Aero_ObjLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;

/**
 * Static-mesh renderer for {@link MegaModelBlockEntity}. Calls the smooth-
 * lighting overload of {@link Aero_MeshRenderer#renderModel} so each face
 * picks up the correct world brightness (day/night + nearby torches).
 */
public class MegaModelBlockEntityRenderer extends BlockEntityRenderer {

    public static final Aero_MeshModel MODEL = Aero_ObjLoader.load("/models/MegaCrusher.obj");

    @Override
    public void render(BlockEntity be, double x, double y, double z, float partialTick) {
        bindTexture("/models/retronism_megacrusher.png");
        // Static path uses the smooth-lighting overload (per-vertex brightness
        // bilinearly sampled from neighbour columns). Animated counterpart
        // uses the flat-brightness path because the mesh moves frame-to-frame
        // and per-vertex sampling becomes meaningless.
        // Sample lighting from the air block above (be.y+1) — querying the
        // BE's own coords returns 0 because light stored INSIDE a block
        // is always zero.
        Aero_MeshRenderer.renderModel(MODEL, x, y, z, 0f, be.world, be.x, be.y + 1, be.z);

        // The MegaCrusher OBJ has all geometry in named groups; the static
        // groups[] array is empty, so renderModel alone would draw nothing.
        // Iterate the named groups at rest pose with the same brightness.
        float brightness = be.world.method_1782(be.x, be.y + 1, be.z);
        Aero_MeshModel.NamedGroup[] entries = MODEL.getNamedGroupArray();
        for (int i = 0; i < entries.length; i++) {
            org.lwjgl.opengl.GL11.glPushMatrix();
            org.lwjgl.opengl.GL11.glTranslated(x, y, z);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            Aero_MeshRenderer.renderGroup(MODEL, entries[i].name, brightness);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
            org.lwjgl.opengl.GL11.glPopMatrix();
        }
    }
}
