package aero.modellib.test;

import net.mine_diver.unsafeevents.listener.EventListener;
import net.modificationstation.stationapi.api.client.event.block.entity.BlockEntityRendererRegisterEvent;
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
    }
}
