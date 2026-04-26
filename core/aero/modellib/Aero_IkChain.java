package aero.modellib;

/**
 * Configuration for a single IK chain solved each frame by the renderer.
 *
 * <p>Implement this at your renderer (or wrap a static instance) to declare
 * which bones form a kinematic chain and what world point the chain's
 * end-effector should reach for. The lib's {@link Aero_CCDSolver} mutates
 * the named bones' rotations to bring the end-effector close to the target.
 *
 * <pre>
 *   Aero_IkChain footPlanting = new Aero_IkChain() {
 *       public String[] getBoneChain() {
 *           return new String[]{ "hip", "knee", "ankle", "foot" };
 *       }
 *       public boolean resolveTargetInto(float[] worldPos) {
 *           worldPos[0] = entity.posX;
 *           worldPos[1] = groundRaycastY(entity);
 *           worldPos[2] = entity.posZ;
 *           return true;
 *       }
 *   };
 * </pre>
 *
 * <p>The chain is processed root-to-end: index 0 is the bone closest to the
 * pivot of the model (e.g. hip), the last index is the end-effector (the
 * foot tip). The solver walks the chain backward from end-effector toward
 * root, rotating each intermediate bone to align the end-effector with the
 * target.
 */
public interface Aero_IkChain {

    /**
     * Bones in this chain, ordered root → end-effector. Names must match
     * bones in the model's named-group set; bones missing from the model
     * are silently skipped by the solver.
     */
    String[] getBoneChain();

    /**
     * Computes the world-space target position for this chain's
     * end-effector. Return {@code false} to skip this chain for this frame
     * (e.g. when the entity has no valid target). The output array is
     * pre-allocated by the renderer; populate {@code worldPos[0..2]} only.
     */
    boolean resolveTargetInto(float[] worldPos);
}
