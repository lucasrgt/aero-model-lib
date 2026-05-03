package aero.modellib.test;
import aero.modellib.Aero_AnimationState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.Random;

import aero.modellib.animation.Aero_AnimationPlayback;
import aero.modellib.animation.Aero_AnimationPredicate;
import aero.modellib.animation.Aero_AnimationSpec;
import aero.modellib.animation.Aero_AnimationStateRouter;

/**
 * Walking robot mob with a 5-phase thermal cycle:
 *
 * <pre>
 *   NORMAL (8s)        — idle / walk clips, white tint
 *   OVERHEATING (4s)   — overheat 0 → 1, walks faster, tint white → red,
 *                        animation snaps to *_hot at level=1
 *   OVERHEAT (4s)      — overheat = 1, hot clips (inverted phase, ±60°
 *                        amplitude, motor faster), full red tint
 *   MELTDOWN (4s)      — meltdown clip plays: head flies off the body
 *                        spinning on every axis, arms rotate around the Z
 *                        axis (broken pivots), legs shake with non-uniform
 *                        scale, motor flips direction every half second.
 *                        Tint pulses red↔yellow.
 *   COOLING (4s)       — overheat 1 → 0, animation back to idle/walk, tint
 *                        fades to white
 *   → loop
 * </pre>
 *
 * Exercises clip switching, smooth tint interpolation (explicit render options),
 * yaw normalisation across the network sync boundary, AND every animation
 * channel (rotation X+Y+Z, position 3D, non-uniform scale) inside meltdown.
 */
public class AeroRobotEntity extends Entity {

    public static final int STATE_IDLE     = 0;
    public static final int STATE_WALK     = 1;
    public static final int STATE_IDLE_HOT = 2;
    public static final int STATE_WALK_HOT = 3;
    public static final int STATE_MELTDOWN = 4;

    private static final int PHASE_NORMAL      = 0;
    private static final int PHASE_OVERHEATING = 1;
    private static final int PHASE_OVERHEAT    = 2;
    private static final int PHASE_MELTDOWN    = 3;
    private static final int PHASE_COOLING     = 4;
    private static final int PHASE_COUNT       = 5;

    private static final int PHASE_NORMAL_TICKS      = 160; // 8s
    private static final int PHASE_OVERHEATING_TICKS = 80;  // 4s
    private static final int PHASE_OVERHEAT_TICKS    = 80;  // 4s
    private static final int PHASE_MELTDOWN_TICKS    = 80;  // 4s
    private static final int PHASE_COOLING_TICKS     = 80;  // 4s

    public static final Aero_AnimationSpec ANIMATION =
        Aero_AnimationSpec.builder("/models/Robot.anim.json")
            .state(STATE_IDLE,     "idle")
            .state(STATE_WALK,     "walk")
            .state(STATE_IDLE_HOT, "idle_hot")
            .state(STATE_WALK_HOT, "walk_hot")
            .state(STATE_MELTDOWN, "meltdown")
            .build();

    public final Aero_AnimationState animState = ANIMATION.createState();

    private final Random random = new Random();
    private float walkYaw;
    private int turnCooldown;
    private boolean walking;

    private int phase = PHASE_NORMAL;
    private int phaseTimer = PHASE_NORMAL_TICKS;
    /** Visible tracking of the thermal level so the renderer can sample it
     *  at any partial-tick boundary; updated each tick from phase + timer. */
    public float overheatLevel;
    public float prevOverheatLevel;

    public AeroRobotEntity(World world) {
        super(world);
        setBoundingBoxSpacing(0.6f, 1.25f);
        standingEyeHeight = 1.0f;
        ignoreFrustumCull = true;
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();

        if (!world.isRemote) {
            tickPhase();
            tickWander();
        }

        prevOverheatLevel = overheatLevel;
        overheatLevel = computeOverheatLevel();

        // Derive "is walking?" from horizontal position delta — both client
        // and server set prevX/prevZ in Entity.tick(), so this stays in sync
        // without an extra packet. The server-side `walking` field would
        // otherwise be stale on the client and the legs flicker between idle
        // and walk while the body actually translates.
        double dx = x - prevX;
        double dz = z - prevZ;
        boolean isMoving = dx * dx + dz * dz > 1.0e-4;

        // State decision routed through Aero_AnimationStateRouter — predicates
        // evaluate top-down and the first match wins, with smooth 6-tick
        // transitions on every state change so e.g. swapping idle⇄walk
        // doesn't snap the legs. Captures the local context (phase, overheat
        // level, whether we are moving) into final locals so the predicates
        // can close over them.
        final int   localPhase    = phase;
        final float localOverheat = overheatLevel;
        final boolean localMoving = isMoving;
        Aero_AnimationStateRouter router = new Aero_AnimationStateRouter()
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return localPhase == PHASE_MELTDOWN; }
            }, STATE_MELTDOWN)
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) {
                    boolean hot = localPhase == PHASE_OVERHEAT
                              || (localPhase == PHASE_OVERHEATING && localOverheat >= 0.5f);
                    return hot && localMoving;
                }
            }, STATE_WALK_HOT)
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) {
                    boolean hot = localPhase == PHASE_OVERHEAT
                              || (localPhase == PHASE_OVERHEATING && localOverheat >= 0.5f);
                    return hot;   // not moving but hot
                }
            }, STATE_IDLE_HOT)
            .when(new Aero_AnimationPredicate() {
                public boolean test(Aero_AnimationPlayback p) { return localMoving; }
            }, STATE_WALK)
            .otherwise(STATE_IDLE)
            .withTransition(6);
        router.applyTo(animState);
        animState.tick();

        // Yaw shortest-path normalisation (see AeroTestEntity).
        while (yaw - prevYaw < -180f) prevYaw -= 360f;
        while (yaw - prevYaw >= 180f) prevYaw += 360f;
    }

    private void tickPhase() {
        if (phaseTimer-- > 0) return;
        phase = (phase + 1) % PHASE_COUNT;
        switch (phase) {
            case PHASE_NORMAL:      phaseTimer = PHASE_NORMAL_TICKS; break;
            case PHASE_OVERHEATING: phaseTimer = PHASE_OVERHEATING_TICKS; break;
            case PHASE_OVERHEAT:    phaseTimer = PHASE_OVERHEAT_TICKS; break;
            case PHASE_MELTDOWN:    phaseTimer = PHASE_MELTDOWN_TICKS; break;
            case PHASE_COOLING:     phaseTimer = PHASE_COOLING_TICKS; break;
        }
    }

    private void tickWander() {
        if (turnCooldown-- <= 0) {
            walking  = random.nextFloat() < 0.7f;
            walkYaw  = random.nextFloat() * 360f;
            turnCooldown = walking ? 60 + random.nextInt(40)
                                   : 40 + random.nextInt(40);
        }

        // Faster motion when overheating/overheat — cooling slows back down.
        // Meltdown locks the robot in place so the user can stare at the
        // chaotic head/limb animation without chasing the entity around.
        float speedMul;
        switch (phase) {
            case PHASE_OVERHEATING: speedMul = 1f + overheatLevel; break;
            case PHASE_OVERHEAT:    speedMul = 2.0f; break;
            case PHASE_MELTDOWN:    speedMul = 0f; break;
            case PHASE_COOLING:     speedMul = 0.5f + overheatLevel * 0.5f; break;
            default:                speedMul = 1.0f;
        }

        if (walking) {
            float yawRad = walkYaw * 0.017453292f;
            float speed  = 0.05f * speedMul;
            velocityX = -((double) speed) * (double) Math.sin(yawRad);
            velocityZ =  ((double) speed) * (double) Math.cos(yawRad);
        } else {
            velocityX = 0;
            velocityZ = 0;
        }
        // Vanilla mob gravity is ~0.08 per tick — the older 0.04 was too
        // weak to overcome any collision wobble at the spawn boundary, so
        // the robot would float instead of settling onto the ground.
        velocityY -= 0.08;
        // Cap velocityY at the vanilla terminal so the entity doesn't
        // tunnel through thin platforms when it's been falling for a while.
        if (velocityY < -3.92) velocityY = -3.92;
        move(velocityX, velocityY, velocityZ);
        if (onGround && velocityY < 0) velocityY = 0;

        prevYaw = yaw;
        if (walking) yaw += angleDelta(yaw, walkYaw) * 0.2f;
    }

    /** 0.0 = cool/normal, 1.0 = fully overheated. Linear ramp across the
     *  OVERHEATING and COOLING phases; pinned at 1 during OVERHEAT and
     *  MELTDOWN. */
    private float computeOverheatLevel() {
        switch (phase) {
            case PHASE_NORMAL:      return 0f;
            case PHASE_OVERHEATING: return 1f - phaseTimer / (float) PHASE_OVERHEATING_TICKS;
            case PHASE_OVERHEAT:    return 1f;
            case PHASE_MELTDOWN:    return 1f;
            case PHASE_COOLING:     return phaseTimer / (float) PHASE_COOLING_TICKS;
        }
        return 0f;
    }

    /** Returns true while the robot is in MELTDOWN — renderer pulses the
     *  tint red↔yellow on top of the regular overheat-red multiplier. */
    public boolean isMeltdown() {
        return phase == PHASE_MELTDOWN;
    }

    /** Linearly samples overheat level for the partial-tick frame. */
    public float getInterpolatedOverheat(float partialTick) {
        return prevOverheatLevel + (overheatLevel - prevOverheatLevel) * partialTick;
    }

    private static float angleDelta(float current, float target) {
        float d = (target - current) % 360f;
        if (d >  180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    @Override
    protected void readNbt(NbtCompound nbt) {
        animState.readFromNBT(nbt);
        phase           = nbt.getInt("Robot_phase");
        phaseTimer      = nbt.getInt("Robot_phaseTimer");
        if (phaseTimer <= 0) phaseTimer = PHASE_NORMAL_TICKS;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        animState.writeToNBT(nbt);
        nbt.putInt("Robot_phase", phase);
        nbt.putInt("Robot_phaseTimer", phaseTimer);
    }
}
