package aero.modellib.test;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.event.block.entity.BlockEntityRegisterEvent;
import net.modificationstation.stationapi.api.event.entity.EntityRegister;
import net.modificationstation.stationapi.api.event.registry.BlockRegistryEvent;
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
    private static void registerEntities(EntityRegister event) {
        event.register(AeroTestEntity.class, NAMESPACE + ":entity_model_probe", 190);
    }

    @EventListener
    private static void trackEntities(TrackEntityEvent event) {
        if (event.entityToTrack instanceof AeroTestEntity) {
            event.track(96, 3, true);
        }
    }

    @EventListener
    private static void populateChunk(WorldGenEvent.ChunkDecoration event) {
        // Auto-place both test blocks in every chunk so testers can find
        // them without depending on AMI's click-to-give. Raw setBlock does
        // not auto-create the BlockEntity (only player placement does), so
        // attach one manually — without it, the BlockEntityRenderer never
        // runs and the OBJ mesh is invisible.
        int sx = event.x + 8, sz = event.z + 8;
        event.world.setBlock(sx,     70, sz,     megaModelBlock.id);
        event.world.setBlockEntity(sx, 70, sz,     new MegaModelBlockEntity());

        int ax = event.x + 4, az = event.z + 8;
        event.world.setBlock(ax,     70, az,     animatedMegaModelBlock.id);
        event.world.setBlockEntity(ax, 70, az,     new AnimatedMegaModelBlockEntity());

        int mx = event.x + 12, mz = event.z + 8;
        event.world.setBlock(mx,     70, mz,     motorBlock.id);
        event.world.setBlockEntity(mx, 70, mz,    new MotorBlockEntity());

        int px = event.x + 12, pz = event.z + 12;
        event.world.setBlock(px,     70, pz,     pumpBlock.id);
        event.world.setBlockEntity(px, 70, pz,    new PumpBlockEntity());

        // Crystals placed high above terrain so the scale/position animations
        // can extend past the block boundary without intersecting water,
        // grass, snow, etc. — those neighbour blocks were Z-fighting with
        // the crystal faces and looked like an X-ray bug at lower heights.
        int cx = event.x + 8, cz = event.z + 12;
        event.world.setBlock(cx,     90, cz,     crystalBlock.id);
        event.world.setBlockEntity(cx, 90, cz,    new CrystalBlockEntity());

        int kx = event.x + 4, kz = event.z + 12;
        event.world.setBlock(kx,     90, kz,     crystalChaosBlock.id);
        event.world.setBlockEntity(kx, 90, kz,    new CrystalChaosBlockEntity());

        // Entity renderer smoke test: one animated model entity every 4x4
        // chunks, so the first generated area has a visible probe without
        // flooding the world with high-poly meshes.
        if (!event.world.isRemote && (event.x & 63) == 0 && (event.z & 63) == 0) {
            AeroTestEntity probe = new AeroTestEntity(event.world);
            probe.setPositionAndAngles(event.x + 8.5, 72.0, event.z + 4.5, 0f, 0f);
            event.world.spawnEntity(probe);
        }
    }
}
