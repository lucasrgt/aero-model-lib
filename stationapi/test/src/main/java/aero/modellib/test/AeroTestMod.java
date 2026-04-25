package aero.modellib.test;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.event.block.entity.BlockEntityRegisterEvent;
import net.modificationstation.stationapi.api.event.entity.EntityRegister;
import net.modificationstation.stationapi.api.event.registry.BlockRegistryEvent;
import net.modificationstation.stationapi.api.event.registry.ItemRegistryEvent;
import net.modificationstation.stationapi.api.server.event.entity.TrackEntityEvent;
import net.modificationstation.stationapi.api.event.world.gen.WorldGenEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;
import net.modificationstation.stationapi.api.util.Identifier;
import net.modificationstation.stationapi.api.util.Namespace;

import java.lang.invoke.MethodHandles;

/**
 * Common-bus entrypoint: registers the test block + its BlockEntity class.
 * Wired in fabric.mod.json under {@code stationapi:event_bus} (not
 * {@code _client} — those events fire on both sides).
 */
public class AeroTestMod {

    public static final Namespace NAMESPACE = Namespace.resolve();

    public static MegaModelBlock megaModelBlock;
    public static AnimatedMegaModelBlock animatedMegaModelBlock;
    public static MotorBlock motorBlock;
    public static PumpBlock pumpBlock;
    public static CrystalBlock crystalBlock;
    public static CrystalChaosBlock crystalChaosBlock;
    public static AeroRobotEggItem robotEgg;

    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    @EventListener
    private static void registerBlocks(BlockRegistryEvent event) {
        megaModelBlock = new MegaModelBlock(Identifier.of(NAMESPACE, "mega_model"));
        animatedMegaModelBlock = new AnimatedMegaModelBlock(Identifier.of(NAMESPACE, "mega_model_animated"));
        motorBlock = new MotorBlock(Identifier.of(NAMESPACE, "motor"));
        pumpBlock = new PumpBlock(Identifier.of(NAMESPACE, "pump"));
        crystalBlock = new CrystalBlock(Identifier.of(NAMESPACE, "crystal"));
        crystalChaosBlock = new CrystalChaosBlock(Identifier.of(NAMESPACE, "crystal_chaos"));
    }

    @EventListener
    private static void registerBlockEntities(BlockEntityRegisterEvent event) {
        // Field-vs-method name collision in BlockEntityRegisterEvent (both
        // are called "register"). Calling the BiConsumer field directly
        // sidesteps the ambiguity.
        event.register.accept(MegaModelBlockEntity.class, NAMESPACE + ":mega_model");
        event.register.accept(AnimatedMegaModelBlockEntity.class, NAMESPACE + ":mega_model_animated");
        event.register.accept(MotorBlockEntity.class, NAMESPACE + ":motor");
        event.register.accept(PumpBlockEntity.class, NAMESPACE + ":pump");
        event.register.accept(CrystalBlockEntity.class, NAMESPACE + ":crystal");
        event.register.accept(CrystalChaosBlockEntity.class, NAMESPACE + ":crystal_chaos");
    }

    @EventListener
    private static void registerItems(ItemRegistryEvent event) {
        robotEgg = new AeroRobotEggItem(Identifier.of(NAMESPACE, "robot_egg"));
    }

    @EventListener
    private static void registerEntities(EntityRegister event) {
        event.register(AeroTestEntity.class,  NAMESPACE + ":entity_model_probe", 190);
        event.register(AeroRobotEntity.class, NAMESPACE + ":robot",              191);
    }

    @EventListener
    private static void trackEntities(TrackEntityEvent event) {
        if (event.entityToTrack instanceof AeroTestEntity
                || event.entityToTrack instanceof AeroRobotEntity) {
            event.track(128, 3, true);
        }
    }

    @EventListener
    private static void populateChunk(WorldGenEvent.ChunkDecoration event) {
        // Each test block sits on top of the real terrain column in this
        // chunk — placing them at a fixed y=70 buried them inside stone in
        // hilly chunks (light = 0, model rendered as a pure-black silhouette).
        placeBE(event, 8, 8, megaModelBlock.id,         new MegaModelBlockEntity());
        placeBE(event, 4, 8, animatedMegaModelBlock.id, new AnimatedMegaModelBlockEntity());
        placeBE(event, 12, 8, motorBlock.id,            new MotorBlockEntity());
        placeBE(event, 12, 12, pumpBlock.id,            new PumpBlockEntity());

        // Crystals get one extra block of clearance over the surface so the
        // scale/position animation can extend past the block bounds without
        // clipping into grass/water/snow neighbours.
        placeBEAbove(event, 8, 12, crystalBlock.id,        new CrystalBlockEntity());
        placeBEAbove(event, 4, 12, crystalChaosBlock.id,   new CrystalChaosBlockEntity());

        // Entity renderer smoke test: one animated model entity every 4x4
        // chunks. Drop it 2 blocks above the column top so it lands on the
        // surface even in mountainous spawns.
        if (!event.world.isRemote && (event.x & 63) == 0 && (event.z & 63) == 0) {
            int ex = event.x + 8;
            int ez = event.z + 4;
            int ey = event.world.getTopSolidBlockY(ex, ez) + 2;
            AeroTestEntity probe = new AeroTestEntity(event.world);
            probe.setPositionAndAngles(ex + 0.5, ey, ez + 0.5, 0f, 0f);
            event.world.spawnEntity(probe);

            // Robot mob right next to the probe so both renderers are
            // visible from the same vantage point.
            int rx = event.x + 12;
            int rz = event.z + 4;
            int ry = event.world.getTopSolidBlockY(rx, rz) + 2;
            AeroRobotEntity robot = new AeroRobotEntity(event.world);
            robot.setPositionAndAngles(rx + 0.5, ry, rz + 0.5, 0f, 0f);
            event.world.spawnEntity(robot);
        }
    }

    /** Places a block + its BlockEntity on top of the chunk column. */
    private static void placeBE(WorldGenEvent.ChunkDecoration event,
                                int dx, int dz, int blockId,
                                net.minecraft.block.entity.BlockEntity be) {
        int x = event.x + dx;
        int z = event.z + dz;
        int y = event.world.getTopSolidBlockY(x, z);
        event.world.setBlock(x, y, z, blockId);
        event.world.setBlockEntity(x, y, z, be);
    }

    /** Like {@link #placeBE}, but with one extra block of air below. */
    private static void placeBEAbove(WorldGenEvent.ChunkDecoration event,
                                      int dx, int dz, int blockId,
                                      net.minecraft.block.entity.BlockEntity be) {
        int x = event.x + dx;
        int z = event.z + dz;
        int y = event.world.getTopSolidBlockY(x, z) + 1;
        event.world.setBlock(x, y, z, blockId);
        event.world.setBlockEntity(x, y, z, be);
    }
}
