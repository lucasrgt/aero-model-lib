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

import aero.modellib.Aero_AnimationState;

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
    public static PlasmaCrystalBlock plasmaCrystalBlock;
    public static ConveyorBlock conveyorBlock;
    public static SpellCircleBlock spellCircleBlock;
    public static TurretIKBlock turretIkBlock;
    public static MorphCrystalBlock morphCrystalBlock;
    public static GraphPoweredBlock graphPoweredBlock;
    public static ChunkBakedShowcaseBlock chunkBakedShowcaseBlock;
    public static AeroRobotEggItem robotEgg;

    /**
     * Stress mode — turn on with `-Daero.stresstest=true` (test build's
     * `runClientStress` task wires this). When enabled:
     *   • Each *qualifying* chunk also receives a 3×3 grid of motors on top
     *     of its showcase placement (~9 extra motors → 23 BEs per qualifying
     *     chunk vs the normal 14).
     *   • Animated LOD distance is bumped to keep them animated even far away,
     *     so a single profile sample exercises the maximum render path.
     * Chunk *spacing* and entity spacing stay at the defaults — placing a
     * showcase in every chunk plus a 4×4 motor grid recurses through neighbor
     * worldgen and overflows the stack at chunk-boundary block updates.
     * Off by default so normal `runClient` keeps the original showcase density.
     */
    /**
     * Empty-mod mode — turn on with `-Daero.testmod.disabled=true` (test
     * build's `runClientEmpty` task wires this). When enabled, ALL event
     * listeners early-return: no blocks/BEs/items registered, no worldgen
     * placements. Lets the user benchmark StationAPI + the runtime mod
     * stack (AMI, glass-networking, GlassConfigAPI) in isolation,
     * comparing against the test-mod-loaded numbers to see what overhead
     * is attributable to modellib's content vs the surrounding ecosystem.
     *
     * <p>modellib's own mixins still apply at class-load (we can't conditionally
     * skip mixin application without a custom mixin plugin) but they no-op
     * because no chunk-baked blocks register and no animated BERs queue.
     * Effective overhead: a few HEAD-inject method dispatches per
     * PalettedContainer.get call (~ns scale).
     */
    static final boolean TEST_MOD_DISABLED = Boolean.getBoolean("aero.testmod.disabled");

    static final boolean STRESS_TEST = Boolean.getBoolean("aero.stresstest")
        && !TEST_MOD_DISABLED;

    /**
     * Factory mode — turn on with `-Daero.factory=true`. Builds an 8-floor
     * Satisfactory-style factory at every qualifying chunk: each floor is
     * a 16×16 cobblestone slab with 2 rows × 8 motors (16 machines per
     * floor, 128 total), separated vertically by 4 blocks (3 air + 1
     * ceiling slab). Occupies y={spawn}+0 to y={spawn}+32.
     *
     * <p>Why this matters: the cobblestone ceilings between floors create
     * natural occlusion. When the player stands on floor 1, the chunk
     * column above is full of opaque ceilings → vanilla's chunk frustum
     * + HOQ should mark those chunks as not-visible → modellib's chunk
     * visibility check skips ALL the motors on floors 3-8. Only motors
     * on floors 1-2 visible → 32 motors instead of 128. Big BE-cost cut.
     *
     * <p>This is the right benchmark for tech-mod gameplay (multi-floor
     * factory bases) — far closer to real-world load than the surface
     * stress test. The factory also exercises the animated BE batcher
     * (all motors share one model) at the largest scale we have.
     */
    static final boolean FACTORY_TEST = Boolean.getBoolean("aero.factory")
        && !TEST_MOD_DISABLED;

    /**
     * Diagnostic — when {@code -Daero.factoryEmpty=true} is set with
     * {@code -Daero.factory=true}, the factory still builds its 8-floor
     * cobblestone tower but skips ALL animated BE placements + chunk-baked
     * showcases. Lets the player measure FPS / chunk-update count of just
     * the factory STRUCTURE (cobblestone slabs + ceilings) to isolate
     * vanilla lighting cascades from BE rendering cost.
     */
    static final boolean FACTORY_EMPTY = Boolean.getBoolean("aero.factoryEmpty")
        && FACTORY_TEST;

    /**
     * MEGA mode — turn on with `-Daero.mega=true`. Like the factory but
     * an order of magnitude more brutal: 16 floors of 3×3 stacks of
     * animated machines, four cluster types per floor cycling between
     * motor / animated-mega-crusher / pump / conveyor. Each floor packs
     * 4 × 3×3 = 36 animated BEs; total per chunk = 16 × 36 = 576
     * animated BEs (vs factory's 16/chunk).
     *
     * <p>This is the absolute worst case for the animated batcher: many
     * instances of the same model, stacked vertically inside a single
     * chunk so the chunk-visibility cull can't help. The batcher should
     * coalesce all 144 motors per chunk into one Tessellator cycle per
     * bone — if the FPS holds, the batcher works at scale; if it
     * collapses, the batcher's per-flush cost is the next bottleneck.
     *
     * <p>Mutually exclusive with FACTORY/REALISTIC/STRESS — MEGA wins.
     */
    static final boolean MEGA_TEST = Boolean.getBoolean("aero.mega")
        && !TEST_MOD_DISABLED;

    /**
     * Legacy MEGA terrain used full 16x16 cobblestone slabs on all 16 floors.
     * That is useful when intentionally stress-testing vanilla terrain chunk
     * display lists, but it pollutes the BlockEntity renderer benchmark with
     * huge {@code WorldRenderer.renderChunks} / driver stalls. Keep it opt-in
     * so the default MEGA mode isolates modellib's BE path.
     */
    static final boolean MEGA_SOLID_TERRAIN =
        Boolean.getBoolean("aero.mega.solidTerrain");

    /**
     * Dense benchmark scenes should isolate model rendering, not particle /
     * audio / event-system pressure. Keep keyframe side effects disabled by
     * default in all perf runs. Re-enable with
     * {@code -Daero.test.events.sideEffects=true} when validating event
     * routing, or the legacy {@code -Daero.mega.sideEffects=true} in MEGA.
     */
    static final boolean MEGA_SIDE_EFFECTS =
        Boolean.getBoolean("aero.mega.sideEffects");
    static final boolean TEST_EVENT_SIDE_EFFECTS =
        Boolean.getBoolean("aero.test.events.sideEffects")
        || (MEGA_TEST && MEGA_SIDE_EFFECTS);

    /**
     * Dense benchmark scenes should not make every identical machine wrap its
     * animation on the same tick. Real bases are placed/loaded over time, so
     * default MEGA to deterministic per-position phase spreading. Disable with
     * {@code -Daero.mega.phaseSpread=false} when testing synchronized loops.
     */
    static final boolean MEGA_PHASE_SPREAD =
        !"false".equalsIgnoreCase(System.getProperty("aero.mega.phaseSpread", "true"));

    /**
     * Realistic mode — turn on with `-Daero.realistic=true` (test build's
     * `runClientRealistic` task wires this). Simulates a typical tech-mod
     * player base: each qualifying chunk gets ~5 BEs (mix of static
     * chunk-baked + animated) instead of 14-23, and chunk spacing is
     * widened to 6 chunks so bases are sparse like real gameplay.
     *
     * <p>Calibrates against retronism / IC2-Heavy-Machinery / beta-
     * energistics typical loadouts: a few generators, a couple
     * processors, an ME controller — not 23 motors per chunk. This is
     * the FPS number that reflects what end users actually experience.
     *
     * <p>Mutually exclusive with {@link #STRESS_TEST}: if both are set,
     * stress wins (it's the "absolute worst case" mode).
     */
    static final boolean REALISTIC_TEST = Boolean.getBoolean("aero.realistic")
        && !STRESS_TEST;

    private static final int DEMO_BLOCK_SPACING_CHUNKS = MEGA_TEST ? 12
        : FACTORY_TEST ? 8
        : REALISTIC_TEST ? 6
        : 2;
    private static final int DEMO_ENTITY_SPACING_CHUNKS = STRESS_TEST ? 2
        : MEGA_TEST ? 24
        : FACTORY_TEST ? 16
        : REALISTIC_TEST ? 12
        : 4;

    /**
     * Animated-LOD threshold (blocks). Beyond this, BEs render via the
     * display-list at-rest path instead of Tessellator-driven renderAnimated
     * — ~10× cheaper per frame. Resolved per-call so the value tracks the
     * player's render-distance setting:
     * <ul>
     *   <li>{@code -Daero.animatedLOD=N}: explicit override</li>
     *   <li>else, stress mode: scaled via
     *       {@link aero.modellib.Aero_AnimationTickLOD#recommendedAnimatedDistance}</li>
     *   <li>else: 48 blocks (the historical default for showcase density)</li>
     * </ul>
     */
    public static double demoAnimatedLodDistance() {
        String override = System.getProperty("aero.animatedLOD");
        if (override != null) return Double.parseDouble(override);
        if (STRESS_TEST) {
            return aero.modellib.Aero_AnimationTickLOD.recommendedAnimatedDistance(
                aero.modellib.Aero_RenderDistance.currentViewDistance());
        }
        return 48d;
    }

    static void seedMegaLoopPhase(Aero_AnimationState state, int stateId, int x, int y, int z) {
        if (!MEGA_TEST || !MEGA_PHASE_SPREAD || state == null) return;
        state.setState(stateId);
        state.setLoopPhase(hashToPhase(x, y, z, stateId));
    }

    private static float hashToPhase(int x, int y, int z, int salt) {
        int h = x * 73428767 ^ y * 912931 ^ z * 42317861 ^ salt * 1103515245;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return (h & 0x00FFFFFF) * (1.0f / 16777216.0f);
    }

    static {
        EntrypointManager.registerLookup(MethodHandles.lookup());
    }

    @EventListener
    private static void registerBlocks(BlockRegistryEvent event) {
        if (TEST_MOD_DISABLED) return;
        megaModelBlock = new MegaModelBlock(Identifier.of(NAMESPACE, "mega_model"));
        animatedMegaModelBlock = new AnimatedMegaModelBlock(Identifier.of(NAMESPACE, "mega_model_animated"));
        motorBlock = new MotorBlock(Identifier.of(NAMESPACE, "motor"));
        pumpBlock = new PumpBlock(Identifier.of(NAMESPACE, "pump"));
        crystalBlock = new CrystalBlock(Identifier.of(NAMESPACE, "crystal"));
        crystalChaosBlock = new CrystalChaosBlock(Identifier.of(NAMESPACE, "crystal_chaos"));
        easingShowcaseBlock  = new EasingShowcaseBlock(Identifier.of(NAMESPACE, "easing_showcase"));
        easingShowcase2Block = new EasingShowcase2Block(Identifier.of(NAMESPACE, "easing_showcase_2"));
        easingShowcase3Block = new EasingShowcase3Block(Identifier.of(NAMESPACE, "easing_showcase_3"));
        plasmaCrystalBlock   = new PlasmaCrystalBlock(Identifier.of(NAMESPACE, "plasma_crystal"));
        conveyorBlock        = new ConveyorBlock(Identifier.of(NAMESPACE, "conveyor"));
        spellCircleBlock     = new SpellCircleBlock(Identifier.of(NAMESPACE, "spell_circle"));
        turretIkBlock        = new TurretIKBlock(Identifier.of(NAMESPACE, "turret_ik"));
        morphCrystalBlock    = new MorphCrystalBlock(Identifier.of(NAMESPACE, "morph_crystal"));
        graphPoweredBlock    = new GraphPoweredBlock(Identifier.of(NAMESPACE, "graph_powered"));
        chunkBakedShowcaseBlock = new ChunkBakedShowcaseBlock(Identifier.of(NAMESPACE, "chunk_baked_showcase"));
    }

    @EventListener
    private static void registerBlockEntities(BlockEntityRegisterEvent event) {
        if (TEST_MOD_DISABLED) return;
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
        event.register.accept(PlasmaCrystalBlockEntity.class,   NAMESPACE + ":plasma_crystal");
        event.register.accept(ConveyorBlockEntity.class,        NAMESPACE + ":conveyor");
        event.register.accept(SpellCircleBlockEntity.class,     NAMESPACE + ":spell_circle");
        event.register.accept(TurretIKBlockEntity.class,        NAMESPACE + ":turret_ik");
        event.register.accept(MorphCrystalBlockEntity.class,    NAMESPACE + ":morph_crystal");
        event.register.accept(GraphPoweredBlockEntity.class,    NAMESPACE + ":graph_powered");
    }

    @EventListener
    private static void registerItems(ItemRegistryEvent event) {
        if (TEST_MOD_DISABLED) return;
        robotEgg = new AeroRobotEggItem(Identifier.of(NAMESPACE, "robot_egg"));
    }

    @EventListener
    private static void registerEntities(EntityRegister event) {
        if (TEST_MOD_DISABLED) return;
        event.register(AeroTestEntity.class,  NAMESPACE + ":entity_model_probe", 190);
        event.register(AeroRobotEntity.class, NAMESPACE + ":robot",              191);
    }

    @EventListener
    private static void trackEntities(TrackEntityEvent event) {
        if (TEST_MOD_DISABLED) return;
        if (event.entityToTrack instanceof AeroTestEntity
                || event.entityToTrack instanceof AeroRobotEntity) {
            event.track(128, 3, true);
        }
    }

    @EventListener
    private static void populateChunk(WorldGenEvent.ChunkDecoration event) {
        if (TEST_MOD_DISABLED) return;
        if (!isChunkMultiple(event, DEMO_BLOCK_SPACING_CHUNKS)) return;

        // Realistic mode short-circuits the demo / stress placements with
        // a sparse "player tech base" layout — see populateChunkRealistic.
        if (REALISTIC_TEST) {
            populateChunkRealistic(event);
            return;
        }
        // MEGA mode wins over everything else — pure batcher torture test.
        if (MEGA_TEST) {
            populateChunkMega(event);
            return;
        }
        // Factory mode builds a multi-floor Satisfactory-style structure.
        if (FACTORY_TEST) {
            populateChunkFactory(event);
            return;
        }

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

        // ProceduralPose + ADDITIVE blend showcase: cyan-tinted glowing
        // crystal that spins via runtime time, reusing Crystal.obj.
        placeBEAbove(event, 12, 0, plasmaCrystalBlock.id, new PlasmaCrystalBlockEntity());

        // v0.2.0 showcase row — one block per new feature.
        placeBEAbove(event, 0,  0, conveyorBlock.id,     new ConveyorBlockEntity());     // UV scroll
        placeBEAbove(event, 4,  0, spellCircleBlock.id,  new SpellCircleBlockEntity());  // UV combo
        placeBEAbove(event, 8,  0, turretIkBlock.id,     new TurretIKBlockEntity());     // IK CCD
        placeBEAbove(event, 8,  4, morphCrystalBlock.id, new MorphCrystalBlockEntity()); // Morph
        placeBEAbove(event, 12, 4, graphPoweredBlock.id, new GraphPoweredBlockEntity()); // Graph

        // -Daero.stresstest=true: 3×3 motor grid in the chunk's free quadrant
        // so each qualifying chunk renders 9 extra animated motors. Uses
        // placeBEAbove (not placeBE) to avoid overwriting surface terrain
        // and keep neighbor block updates shallow. Positioned at (1,1)..(7,7)
        // step 3 so they don't collide with the showcase placements.
        //
        // The mirrored 3×3 chunk-baked grid sits at x=9..15 step 3 / z=1..7
        // step 3, so the player can see both grids simultaneously and
        // compare per-block cost in JFR. Chunk-baked blocks have NO BE,
        // they live entirely in the chunk vertex buffer.
        if (STRESS_TEST) {
            for (int gx = 0; gx < 3; gx++) {
                for (int gz = 0; gz < 3; gz++) {
                    int sx = 1 + gx * 3;     // 1, 4, 7
                    int sz = 1 + gz * 3;
                    placeBEAbove(event, sx, sz, motorBlock.id, new MotorBlockEntity());
                    int cx = 9 + gx * 3;     // 9, 12, 15
                    int cz = 1 + gz * 3;
                    placeBlockAbove(event, cx, cz, chunkBakedShowcaseBlock.id);
                }
            }
        }

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

    /**
     * Realistic player-tech-base layout. Calibrated against typical
     * gameplay loadouts of mods that consume modellib (retronism IC2-Heavy,
     * beta-energistics): a couple of generators, one or two processing
     * machines, an ME-style controller, and a small line of pipes /
     * conveyors. NOT the demo's 14 BEs per chunk — that's worst-case
     * exhibit hall, not real factories.
     *
     * <p>Per qualifying chunk (~6 chunks apart, vs demo's 2):
     * <ul>
     *   <li>1 motor (animated power generator analogue)</li>
     *   <li>1 pump (animated processing machine analogue)</li>
     *   <li>1 mega-model BE (static decorative casing — the kind of block
     *       that in real mods is just chunk-baked geometry but here uses
     *       a BE so this benchmark counts the animated-path cost too)</li>
     *   <li>1 conveyor (UV-scroll showcase — typical "logistics" piece)</li>
     *   <li>3 chunk-baked showcase blocks (purely chunk-mesh-emitted —
     *       represent decorative non-functional blocks like cabling /
     *       casings that should NOT be BE-rendered in real mods)</li>
     * </ul>
     *
     * <p>That's 4 BEs + 3 chunk-baked = 7 blocks per qualifying chunk
     * spaced 6 chunks apart. From a typical viewing position the player
     * sees 1-3 such bases at once → 4-12 visible BEs total. This is the
     * loadout the released library should be optimized for.
     */
    private static void populateChunkRealistic(WorldGenEvent.ChunkDecoration event) {
        // Two generators + one processor cluster (the "factory floor")
        placeBE(event, 6,  8, motorBlock.id,         new MotorBlockEntity());
        placeBE(event, 8,  8, motorBlock.id,         new MotorBlockEntity());
        placeBE(event, 10, 8, pumpBlock.id,          new PumpBlockEntity());

        // One static "machine casing" — represents the kind of block that
        // SHOULD be chunk-baked in real mods. Here we use a BE-rendered
        // mega-model to count its cost too (vs the chunk-baked alternative).
        placeBE(event, 8, 12, megaModelBlock.id,     new MegaModelBlockEntity());

        // One animated logistics piece — conveyor with UV scroll.
        placeBEAbove(event, 8, 4, conveyorBlock.id,  new ConveyorBlockEntity());

        // 3 chunk-baked decorative blocks lined up — cable / casing
        // analogue. Zero per-frame cost; they live in the chunk vertex
        // buffer until a block update.
        placeBlockAbove(event, 4, 8, chunkBakedShowcaseBlock.id);
        placeBlockAbove(event, 4, 9, chunkBakedShowcaseBlock.id);
        placeBlockAbove(event, 4, 10, chunkBakedShowcaseBlock.id);
    }

    /**
     * Satisfactory-style multi-floor factory. Builds a 16×16 footprint
     * tower at the qualifying chunk's column, 8 floors stacked vertically
     * with a VARIED bank of showcase blocks on each floor (one example
     * of every animation type the test mod exercises) + cobblestone
     * ceilings between them.
     *
     * <p>Per floor (16 unique showcase blocks):
     * <ul>
     *   <li><strong>Row 1 (z=4) — big machines</strong>: AnimatedMegaModel
     *       (fans + particles + sound), Motor, Pump, MegaModel (static),
     *       Conveyor (UV scroll), SpellCircle (UV combo), TurretIK (IK),
     *       GraphPowered (animation graph)</li>
     *   <li><strong>Row 2 (z=12) — crystals + pulsing</strong>: Crystal,
     *       CrystalChaos, PlasmaCrystal, MorphCrystal, EasingShowcase 1/2/3,
     *       ChunkBakedShowcase (chunk-bake fast path)</li>
     * </ul>
     *
     * <p>Total: 16 unique × 8 floors = 128 BEs (= 8 instances per type).
     * The cobblestone ceilings between floors naturally occlude upper
     * floors when the player is on a lower floor — exercises modellib's
     * chunk-visibility cull at the scale that matters for real tech-mod
     * gameplay (Satisfactory-style multi-floor base).
     *
     * <p>Each row repeats every floor, so the player can navigate
     * vertically and still see the same lineup — useful for measuring
     * per-floor cost.
     */
    private static void populateChunkFactory(WorldGenEvent.ChunkDecoration event) {
        // Use the column-center top as base Y so the factory sits on top
        // of the actual terrain (avoids burying floor 1 inside hills).
        int baseY = event.world.getTopSolidBlockY(event.x + 8, event.z + 8) + 1;
        final int FLOOR_HEIGHT = 4; // 1 cobblestone floor + 3 air space
        final int NUM_FLOORS = 8;
        final int COBBLESTONE_ID = net.minecraft.block.Block.COBBLESTONE.id;

        for (int floor = 0; floor < NUM_FLOORS; floor++) {
            int floorY = baseY + floor * FLOOR_HEIGHT;

            // Cobblestone slab spanning the chunk (16×16). Doubles as the
            // ceiling for the floor below.
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = event.x + dx;
                    int z = event.z + dz;
                    event.world.setBlockWithoutNotifyingNeighbors(x, floorY, z, COBBLESTONE_ID);
                }
            }

            int machineY = floorY + 1;

            // factoryEmpty mode: skip all animated BE + chunk-baked
            // placements — only the cobblestone structure ships.
            if (FACTORY_EMPTY) continue;

            // BIG MACHINES — z=3 and z=6, sparse 4-block spacing because
            // their models are visually large and can clip into small
            // showcases if placed too close.
            placeBEAt(event,  2, machineY, 3, animatedMegaModelBlock.id, new AnimatedMegaModelBlockEntity());
            placeBEAt(event,  6, machineY, 3, motorBlock.id,             new MotorBlockEntity());
            placeBEAt(event, 10, machineY, 3, pumpBlock.id,              new PumpBlockEntity());
            placeBEAt(event, 14, machineY, 3, megaModelBlock.id,         new MegaModelBlockEntity());

            placeBEAt(event,  2, machineY, 6, conveyorBlock.id,    new ConveyorBlockEntity());
            placeBEAt(event,  6, machineY, 6, spellCircleBlock.id, new SpellCircleBlockEntity());
            placeBEAt(event, 10, machineY, 6, turretIkBlock.id,    new TurretIKBlockEntity());
            placeBEAt(event, 14, machineY, 6, graphPoweredBlock.id, new GraphPoweredBlockEntity());

            // CRYSTALS — z=10, all 4 spread evenly (x=2,6,10,14).
            placeBEAt(event,  2, machineY, 10, crystalBlock.id,       new CrystalBlockEntity());
            placeBEAt(event,  6, machineY, 10, crystalChaosBlock.id,  new CrystalChaosBlockEntity());
            placeBEAt(event, 10, machineY, 10, plasmaCrystalBlock.id, new PlasmaCrystalBlockEntity());
            placeBEAt(event, 14, machineY, 10, morphCrystalBlock.id,  new MorphCrystalBlockEntity());

            // EASING SHOWCASES — z=12, the 3 pulsing-curve variants.
            placeBEAt(event,  4, machineY, 12, easingShowcaseBlock.id,  new EasingShowcaseBlockEntity());
            placeBEAt(event,  8, machineY, 12, easingShowcase2Block.id, new EasingShowcase2BlockEntity());
            placeBEAt(event, 12, machineY, 12, easingShowcase3Block.id, new EasingShowcase3BlockEntity());

            // PYRAMIDS (chunk-baked, no BE) — two rows back-to-back at
            // z=14 and z=15, 8 each (x=1,3,5,7,9,11,13,15).
            for (int col = 0; col < 8; col++) {
                int px = col * 2 + 1;
                event.world.setBlockWithoutNotifyingNeighbors(
                    event.x + px, machineY, event.z + 14, chunkBakedShowcaseBlock.id);
                event.world.setBlockWithoutNotifyingNeighbors(
                    event.x + px, machineY, event.z + 15, chunkBakedShowcaseBlock.id);
            }
        }

        // Top cap so floor 8's ceiling is also cobblestone (avoids open sky).
        int topY = baseY + NUM_FLOORS * FLOOR_HEIGHT;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                event.world.setBlockWithoutNotifyingNeighbors(
                    event.x + dx, topY, event.z + dz, COBBLESTONE_ID);
            }
        }
    }

    /** Places a BE at an explicit Y (vs placeBE which uses the terrain top). */
    private static void placeBEAt(WorldGenEvent.ChunkDecoration event,
                                   int dx, int y, int dz, int blockId,
                                   net.minecraft.block.entity.BlockEntity be) {
        int x = event.x + dx;
        int z = event.z + dz;
        event.world.setBlockWithoutNotifyingNeighbors(x, y, z, blockId);
        event.world.setBlockEntity(x, y, z, be);
    }

    /**
     * Builds the MEGA-test chunk: 16 floors, each floor packs 4 × 3×3
     * stacks of animated BEs at fixed offsets. Cluster type rotates
     * with floor index so each stack tower is visually consistent
     * vertically (motors only / crushers only / pumps only / conveyors
     * only) — the batcher coalesces same-model instances inside the
     * stack but the per-floor mix means 4 different model identities
     * stay alive in the batch map per floor.
     *
     * <p>Footprint per cluster: 3×3 blocks at corner-ish positions
     * (1,1)..(3,3), (1,11)..(3,13), (11,1)..(13,3), (11,11)..(13,13).
     * Leaves a 5-block central corridor for the player to walk through.
     *
     * <p>Total per chunk: 16 floors × 4 clusters × 9 BEs = 576 animated
     * BlockEntities. With 12-chunk spacing, two qualifying chunks visible
     * at far render-distance = 1152 BEs in the visible chunk set.
     */
    private static void populateChunkMega(WorldGenEvent.ChunkDecoration event) {
        int baseY = event.world.getTopSolidBlockY(event.x + 8, event.z + 8) + 1;
        final int FLOOR_HEIGHT = 4;
        final int NUM_FLOORS = 16;
        final int COBBLESTONE_ID = net.minecraft.block.Block.COBBLESTONE.id;

        // Cluster origin offsets within the 16x16 chunk. Each cluster
        // spans (origin)..(origin+2) inclusive.
        final int[][] CLUSTER_ORIGINS = {
            { 1,  1}, { 1, 11},
            {11,  1}, {11, 11}
        };

        for (int floor = 0; floor < NUM_FLOORS; floor++) {
            int floorY = baseY + floor * FLOOR_HEIGHT;

            placeMegaFloor(event, floorY, COBBLESTONE_ID, CLUSTER_ORIGINS);

            int machineY = floorY + 1;

            for (int c = 0; c < CLUSTER_ORIGINS.length; c++) {
                int ox = CLUSTER_ORIGINS[c][0];
                int oz = CLUSTER_ORIGINS[c][1];
                // Per-cluster, per-floor: same model identity in this
                // 3×3 stack so the batcher can flush 9 instances in one
                // cycle per bone. Cluster index → which model:
                //   0 → motor              (flat skeleton, ideal batch)
                //   1 → animated megacrush (lots of named groups, body+fans)
                //   2 → pump               (different bone set)
                //   3 → conveyor           (UV-scroll showcase)
                int clusterKind = c;
                for (int sx = 0; sx < 3; sx++) {
                    for (int sz = 0; sz < 3; sz++) {
                        int dx = ox + sx;
                        int dz = oz + sz;
                        switch (clusterKind) {
                            case 0:
                                placeBEAt(event, dx, machineY, dz,
                                    motorBlock.id, new MotorBlockEntity());
                                break;
                            case 1:
                                placeBEAt(event, dx, machineY, dz,
                                    animatedMegaModelBlock.id,
                                    new AnimatedMegaModelBlockEntity());
                                break;
                            case 2:
                                placeBEAt(event, dx, machineY, dz,
                                    pumpBlock.id, new PumpBlockEntity());
                                break;
                            case 3:
                                placeBEAt(event, dx, machineY, dz,
                                    conveyorBlock.id, new ConveyorBlockEntity());
                                break;
                        }
                    }
                }
            }
        }

        // Top cap. Sparse by default for the same reason as each floor:
        // keep the benchmark about animated BEs, not terrain display-list
        // throughput. Opt back into the old full slab with
        // -Daero.mega.solidTerrain=true.
        int topY = baseY + NUM_FLOORS * FLOOR_HEIGHT;
        placeMegaFloor(event, topY, COBBLESTONE_ID, CLUSTER_ORIGINS);
    }

    private static void placeMegaFloor(WorldGenEvent.ChunkDecoration event,
                                       int y, int blockId,
                                       int[][] clusterOrigins) {
        if (MEGA_SOLID_TERRAIN) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    event.world.setBlockWithoutNotifyingNeighbors(
                        event.x + dx, y, event.z + dz, blockId);
                }
            }
            return;
        }

        // Sparse support pads directly under the 3x3 BE clusters. This keeps
        // the tower readable and walkable without turning each chunk into a
        // giant terrain display list.
        for (int c = 0; c < clusterOrigins.length; c++) {
            int ox = clusterOrigins[c][0];
            int oz = clusterOrigins[c][1];
            for (int sx = 0; sx < 3; sx++) {
                for (int sz = 0; sz < 3; sz++) {
                    event.world.setBlockWithoutNotifyingNeighbors(
                        event.x + ox + sx, y, event.z + oz + sz, blockId);
                }
            }
        }

        // A small central cross lets the player climb/fly through the tower
        // and gives visual depth, while still being far cheaper than 256
        // terrain blocks per floor.
        for (int i = 0; i < 16; i++) {
            event.world.setBlockWithoutNotifyingNeighbors(event.x + i, y, event.z + 7, blockId);
            event.world.setBlockWithoutNotifyingNeighbors(event.x + i, y, event.z + 8, blockId);
            event.world.setBlockWithoutNotifyingNeighbors(event.x + 7, y, event.z + i, blockId);
            event.world.setBlockWithoutNotifyingNeighbors(event.x + 8, y, event.z + i, blockId);
        }
    }

    private static boolean isChunkMultiple(WorldGenEvent.ChunkDecoration event, int spacing) {
        int chunkX = event.x >> 4;
        int chunkZ = event.z >> 4;
        return Math.floorMod(chunkX, spacing) == 0
            && Math.floorMod(chunkZ, spacing) == 0;
    }

    /**
     * Places a block + its BlockEntity on top of the chunk column.
     *
     * <p>Uses {@code setBlockWithoutNotifyingNeighbors} (vs the regular
     * {@code setBlock}) on purpose: during worldgen, neighbor-notification
     * cascades can recurse infinitely when the column happens to spawn in
     * a snowy biome ({@code SnowyBlock.breakIfCannotPlaceAt} → setBlock
     * → notify → adjacent SnowyBlock → ... StackOverflow). Worldgen
     * placements don't need lighting/redstone propagation immediately —
     * the chunk is committed atomically afterwards anyway.
     */
    private static void placeBE(WorldGenEvent.ChunkDecoration event,
                                int dx, int dz, int blockId,
                                net.minecraft.block.entity.BlockEntity be) {
        int x = event.x + dx;
        int z = event.z + dz;
        int y = event.world.getTopSolidBlockY(x, z);
        event.world.setBlockWithoutNotifyingNeighbors(x, y, z, blockId);
        event.world.setBlockEntity(x, y, z, be);
    }

    /** Like {@link #placeBE}, but with one extra block of air below. */
    private static void placeBEAbove(WorldGenEvent.ChunkDecoration event,
                                      int dx, int dz, int blockId,
                                      net.minecraft.block.entity.BlockEntity be) {
        int x = event.x + dx;
        int z = event.z + dz;
        int y = event.world.getTopSolidBlockY(x, z) + 1;
        event.world.setBlockWithoutNotifyingNeighbors(x, y, z, blockId);
        event.world.setBlockEntity(x, y, z, be);
    }

    /** Pure-block placement (no BlockEntity) — for chunk-baked showcase blocks. */
    private static void placeBlockAbove(WorldGenEvent.ChunkDecoration event,
                                         int dx, int dz, int blockId) {
        int x = event.x + dx;
        int z = event.z + dz;
        int y = event.world.getTopSolidBlockY(x, z) + 1;
        event.world.setBlockWithoutNotifyingNeighbors(x, y, z, blockId);
    }
}
