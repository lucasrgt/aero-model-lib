package aero.modellib.test;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.client.event.block.entity.BlockEntityRendererRegisterEvent;
import net.modificationstation.stationapi.api.client.event.render.entity.EntityRendererRegisterEvent;
import net.modificationstation.stationapi.api.mod.entrypoint.EntrypointManager;

import java.lang.invoke.MethodHandles;

/**
 * Client-bus entrypoint: registers the BlockEntity renderer.
 * Wired in fabric.mod.json under {@code stationapi:event_bus_client}.
 */
public class AeroTestModClient {

    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    @EventListener
    private static void registerBlockEntityRenderers(BlockEntityRendererRegisterEvent event) {
        event.renderers.put(MegaModelBlockEntity.class, new MegaModelBlockEntityRenderer());
        event.renderers.put(AnimatedMegaModelBlockEntity.class, new AnimatedMegaModelBlockEntityRenderer());
        event.renderers.put(MotorBlockEntity.class, new MotorBlockEntityRenderer());
        event.renderers.put(PumpBlockEntity.class, new PumpBlockEntityRenderer());
        event.renderers.put(CrystalBlockEntity.class, new CrystalBlockEntityRenderer());
        event.renderers.put(CrystalChaosBlockEntity.class, new CrystalChaosBlockEntityRenderer());
        event.renderers.put(EasingShowcaseBlockEntity.class,  new EasingShowcaseBlockEntityRenderer());
        event.renderers.put(EasingShowcase2BlockEntity.class, new EasingShowcase2BlockEntityRenderer());
        event.renderers.put(EasingShowcase3BlockEntity.class, new EasingShowcase3BlockEntityRenderer());
        event.renderers.put(PlasmaCrystalBlockEntity.class,   new PlasmaCrystalBlockEntityRenderer());
        event.renderers.put(ConveyorBlockEntity.class,        new ConveyorBlockEntityRenderer());
        event.renderers.put(SpellCircleBlockEntity.class,     new SpellCircleBlockEntityRenderer());
        event.renderers.put(TurretIKBlockEntity.class,        new TurretIKBlockEntityRenderer());
        event.renderers.put(MorphCrystalBlockEntity.class,    new MorphCrystalBlockEntityRenderer());
        event.renderers.put(GraphPoweredBlockEntity.class,    new GraphPoweredBlockEntityRenderer());
    }

    @EventListener
    private static void registerEntityRenderers(EntityRendererRegisterEvent event) {
        event.renderers.put(AeroTestEntity.class,  new AeroTestEntityRenderer());
        event.renderers.put(AeroRobotEntity.class, new AeroRobotEntityRenderer());
    }
}
