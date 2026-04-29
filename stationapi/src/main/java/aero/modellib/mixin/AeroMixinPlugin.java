package aero.modellib.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Mixin config plugin — gates conditional mixins and emits a clear
 * startup log line so operators / developers can tell at a glance
 * which optimization paths are active.
 *
 * <h2>Why this exists</h2>
 * <p>v3.0 introduced a mixin
 * ({@link PalettedContainerCacheMixin}) that targets a StationAPI
 * <em>internal</em> class
 * ({@code net.modificationstation.stationapi.impl.world.chunk.PalettedContainer}).
 * Internal classes can be renamed or restructured between StationAPI
 * versions; if that happens, the cache mixin would fail to apply and
 * the only signal would be a stack-trace deep in the loader output.
 *
 * <p>This plugin checks whether the target class is present before
 * Mixin tries to apply, and produces an explicit, grep-able log line
 * either way:
 * <pre>
 * [aero-model-lib] StationAPI PalettedContainer detected — paletted-cache mixin ENABLED (~17% CPU savings on chunk meshing)
 * </pre>
 * or:
 * <pre>
 * [aero-model-lib] StationAPI PalettedContainer NOT FOUND — paletted-cache mixin DISABLED. modellib will run without the v3.0 chunk-mesh optimization. If you have StationAPI loaded, this likely indicates a version-incompatibility — file an issue with your StationAPI version.
 * </pre>
 *
 * <p>Beyond visibility, this also makes the mixin a no-op rather than a
 * load-time failure when StationAPI is absent or refactored — modellib
 * stays loadable in both scenarios.
 */
public class AeroMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = Logger.getLogger("aero-model-lib");
    private static final String PALETTED_CONTAINER_CLASS =
        "net.modificationstation.stationapi.impl.world.chunk.PalettedContainer";
    private static final String PALETTED_CONTAINER_RESOURCE =
        "net/modificationstation/stationapi/impl/world/chunk/PalettedContainer.class";

    private boolean palettedContainerAvailable;

    @Override
    public void onLoad(String mixinPackage) {
        // CRITICAL: do NOT call Class.forName() here — it would load the
        // target class before Mixin gets a chance to apply transformations,
        // causing MixinTargetAlreadyLoadedException at PREPARE phase. We
        // probe via ClassLoader.getResource() instead, which checks the
        // .class file existence on the classpath without triggering JVM
        // class loading.
        boolean found = this.getClass().getClassLoader()
            .getResource(PALETTED_CONTAINER_RESOURCE) != null;
        palettedContainerAvailable = found;
        boolean optedIn = "true".equalsIgnoreCase(System.getProperty("aero.palettedcache"));
        if (!found) {
            LOGGER.warning("[aero-model-lib] StationAPI PalettedContainer NOT FOUND"
                + " (" + PALETTED_CONTAINER_CLASS + ")"
                + " — paletted-cache mixin DISABLED."
                + " modellib runs without the v3.0 chunk-mesh optimization."
                + " If you DO have StationAPI loaded, this likely indicates a"
                + " version mismatch — the internal class may have been renamed or"
                + " moved. File an issue with your StationAPI version.");
        } else if (optedIn) {
            LOGGER.info("[aero-model-lib] StationAPI PalettedContainer detected"
                + " — paletted-cache mixin OPTED IN (-Daero.palettedcache=true)."
                + " Note: bench measurements show this is a net regression in steady"
                + " state (~20% FPS loss). Useful only for world-entry trava A/B testing.");
        } else {
            LOGGER.info("[aero-model-lib] StationAPI PalettedContainer detected"
                + " — paletted-cache mixin DEFAULT-OFF in v3.0 (net regression in"
                + " steady-state benchmark). Opt in with -Daero.palettedcache=true"
                + " for world-entry experimentation.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".PalettedContainerCacheMixin")) {
            return palettedContainerAvailable;
        }
        // All other mixins apply unconditionally — they target vanilla MC
        // classes which are guaranteed to be present at load time.
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                           String mixinClassName, IMixinInfo mixinInfo) {}
}
