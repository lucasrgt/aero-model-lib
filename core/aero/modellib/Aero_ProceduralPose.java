package aero.modellib;

/**
 * Render-time hook that adds runtime / input-driven rotations on top of
 * the keyframed pose — the bridge between the declarative
 * {@link Aero_AnimationSpec} / {@link Aero_ModelSpec} and per-frame state
 * the lib can't possibly know about (player input, physics, RPMs).
 *
 * <p>The lib calls this once per animated bone, AFTER the keyframe pose
 * is resolved into {@link Aero_BoneRenderPose}. Hooks just add deltas to
 * the rotation / offset / scale fields:
 *
 * <pre>
 * Aero_EntityModelRenderer.render(MyTank.MODEL, tank.animState,
 *     entity, x, y, z, yaw, partialTick,
 *     new Aero_ProceduralPose() {
 *         public void apply(String bone, Aero_BoneRenderPose pose) {
 *             if ("turret".equals(bone))    pose.rotY += tank.turretYaw;
 *             if ("barrel".equals(bone))    pose.rotX += tank.barrelPitch;
 *             if ("propeller".equals(bone)) pose.rotX += tank.engineRPM
 *                                                       * (tank.age + partialTick);
 *         }
 *     });
 * </pre>
 *
 * <p>Composes with multi-layer Stack rendering as well. {@code null}
 * passed to the renderer skips the hook entirely with no overhead.
 */
public interface Aero_ProceduralPose {

    /**
     * @param boneName  current bone (matches the OBJ named group / pivot name)
     * @param pose      mutable pose, keyframe values already applied — add deltas in place
     */
    void apply(String boneName, Aero_BoneRenderPose pose);
}
