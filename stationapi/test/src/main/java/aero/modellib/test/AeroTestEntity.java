package aero.modellib.test;

import aero.modellib.Aero_AnimationSpec;
import aero.modellib.Aero_AnimationState;
import aero.modellib.Aero_RenderDistance;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.Random;

/**
 * In-game smoke-test entity for Aero_EntityModelRenderer. Walks around in
 * random directions to exercise the animated render path while the entity
 * yaw + position change every frame — both static yaw render and partial-
 * tick interpolation get covered without any AI/Living overhead.
 */
public class AeroTestEntity extends Entity {

    public static final int STATE_SPIN = 1;

    public static final Aero_AnimationSpec ANIMATION =
        Aero_AnimationSpec.builder("/models/MegaCrusher.anim.json")
            .state(STATE_SPIN, "working")
            .build();

    public final Aero_AnimationState animState = ANIMATION.createState();

    private final Random random = new Random();
    private float walkYaw;          // direction the entity is currently walking
    private int turnCooldown;       // ticks remaining before picking a new direction

    public AeroTestEntity(World world) {
        super(world);
        setBoundingBoxSpacing(1.2f, 1.6f);
        standingEyeHeight = 1.2f;
        ignoreFrustumCull = true;
        Aero_RenderDistance.applyEntityRenderDistance(this, 3.0d);
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        animState.setState(STATE_SPIN);
        animState.tick();

        // Yaw + walk logic is server-only — the client receives yaw via the
        // entity-tracking packet and only needs to interpolate prevYaw → yaw.
        // Running the smoothing on both sides made the client drag yaw back
        // to its default (walkYaw == 0) every tick, fighting the server sync
        // and causing a visible flick when the next packet arrived.
        if (!world.isRemote) {
            if (turnCooldown-- <= 0) {
                walkYaw = random.nextFloat() * 360f;
                turnCooldown = 60 + random.nextInt(40);
            }

            float yawRad = walkYaw * 0.017453292f;
            float speed  = 0.06f;
            velocityX = -((double) speed) * (double) Math.sin(yawRad);
            velocityZ =  ((double) speed) * (double) Math.cos(yawRad);
            velocityY -= 0.04; // gravity

            move(velocityX, velocityY, velocityZ);
            if (onGround) velocityY = 0;

            prevYaw = yaw;
            yaw += angleDelta(yaw, walkYaw) * 0.2f;
        }

        // Keep prevYaw within ±180° of yaw so the renderer's linear
        // interpolation always takes the shortest path. Without this, a
        // yaw=1°, prevYaw=359° pair interpolates backwards through 180°
        // (a "flick" once per revolution).
        while (yaw - prevYaw < -180f) prevYaw -= 360f;
        while (yaw - prevYaw >= 180f) prevYaw += 360f;
    }

    /** Shortest signed delta from {@code current} to {@code target} in degrees. */
    private static float angleDelta(float current, float target) {
        float d = (target - current) % 360f;
        if (d >  180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    @Override
    protected void readNbt(NbtCompound nbt) {
        animState.readFromNBT(nbt);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        animState.writeToNBT(nbt);
    }
}
