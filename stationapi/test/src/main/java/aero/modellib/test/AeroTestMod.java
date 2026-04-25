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
    public static EasingShowcaseBlock easingShowcaseBlock;
    public static EasingShowcase2Block easingShowcase2Block;
    public static EasingShowcase3Block easingShowcase3Block;
    public static AeroRobotEggItem robotEgg;

    private static final int DEMO_BLOCK_SPACING_CHUNKS = 2;
    private static final int DEMO_ENTITY_SPACING_CHUNKS = 4;
    static final double DEMO_ANIMATED_LOD_DISTANCE_BLOCKS = 48d;

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
        easingShowcaseBlock  = new EasingShowcaseBlock(Identifier.of(NAMESPACE, "easing_showcase"));
        easingShowcase2Block = new EasingShowcase2Block(Identifier.of(NAMESPACE, "easing_showcase_2"));
        easingShowcase3Block = new EasingShowcase3Block(Identifier.of(NAMESPACE, "easing_showcase_3"));
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
        event.register.accept(EasingShowcaseBlockEntity.class,  NAMESPACE + ":easing_showcase");
        event.register.accept(EasingShowcase2BlockEntity.class, NAMESPACE + ":easing_showcase_2");
        event.register.accept(EasingShowcase3BlockEntity.class, NAMESPACE + ":easing_showcase_3");
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
        if (!isChunkMultiple(event, DEMO_BLOCK_SPACING_CHUNKS)) return;

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

        // Easing showcases — three blocks lined up so all 30+ curves can
        // be inspected at once. Block 1 covers sine/quad/back/elastic/
        // bounce; block 2 covers cubic/quart/quint/expo; block 3 covers
        // circ + bounce in/inout + linear/step/catmullrom baselines.
        placeBEAbove(event, 0, 12, easingShowcaseBlock.id,  new EasingShowcaseBlockEntity());
        placeBEAbove(event, 0, 8,  easingShowcase2Block.id, new EasingShowcase2BlockEntity());
        placeBEAbove(event, 0, 4,  easingShowcase3Block.id, new EasingShowcase3BlockEntity());

        // Entity renderer smoke test: one animated model entity every 4x4
        // chunks. Drop it 2 blocks above the column top so it lands on the
        // surface even in mountainous spawns.
        if (!event.world.isRemote && isChunkMultiple(event, DEMO_ENTITY_SPACING_CHUNKS)) {
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

    private static boolean isChunkMultiple(WorldGenEvent.ChunkDecoration event, int spacing) {
        int chunkX = event.x >> 4;
        int chunkZ = event.z >> 4;
        return Math.floorMod(chunkX, spacing) == 0
            && Math.floorMod(chunkZ, spacing) == 0;
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
