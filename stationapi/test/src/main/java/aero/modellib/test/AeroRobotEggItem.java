package aero.modellib.test;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.modificationstation.stationapi.api.template.item.TemplateItem;
import net.modificationstation.stationapi.api.util.Identifier;

/**
 * Right-click spawn egg for {@link AeroRobotEntity}. Useful when the
 * randomly-generated chunk probes are far from the player — drops a fresh
 * robot one block off whichever face was clicked.
 */
public class AeroRobotEggItem extends TemplateItem {

    public AeroRobotEggItem(Identifier id) {
        super(id);
        setMaxCount(64);
        setTranslationKey(id);
    }

    @Override
    public boolean useOnBlock(ItemStack stack, PlayerEntity player, World world,
                              int x, int y, int z, int facing) {
        if (world.isRemote) return true;

        // Place feet flush with the block's top face for the most common
        // case (clicking on top of the floor); other faces just offset one
        // block in that direction. facing IDs: 0=-y, 1=+y, 2=-z, 3=+z, 4=-x,
        // 5=+x. Without this the robot used to spawn at y+1.0 above the
        // top face which left it floating one block in the air, which
        // looked like the entity had lost gravity.
        double sx = x + 0.5, sy = y + 1.0, sz = z + 0.5;
        switch (facing) {
            case 0: sy = y - 1.5; break;          // bottom face — spawn below
            case 1: sy = y + 1.0; break;          // top face — feet on the block
            case 2: sz = z - 0.5; sy = y; break;  // side faces — flush with block
            case 3: sz = z + 1.5; sy = y; break;
            case 4: sx = x - 0.5; sy = y; break;
            case 5: sx = x + 1.5; sy = y; break;
        }

        AeroRobotEntity robot = new AeroRobotEntity(world);
        robot.setPositionAndAngles(sx, sy, sz, player.yaw, 0f);
        world.spawnEntity(robot);

        // Beta 1.7.3 has no creative mode — keep the egg infinite for testing
        // so the player can spam-spawn without waiting for new ones.
        return true;
    }
}
