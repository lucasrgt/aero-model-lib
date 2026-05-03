package aero.modellib.animation.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Parameter bag for {@link Aero_AnimationGraph}. Holds named float / bool /
 * trigger values that graph nodes read each frame to drive blend weights
 * and state transitions.
 *
 * <p>{@code float} params are continuous (e.g. movement speed → blend
 * walk/run). {@code boolean} params are toggles (e.g. is_carrying).
 * {@code trigger}s are one-shot booleans that auto-clear on read — useful
 * for state-machine "fire attack now" pulses.
 *
 * <p>Mutable; the consumer typically owns one of these per entity and
 * pushes input each tick before sampling the graph.
 */
public final class Aero_GraphParams {

    private final Map floats = new HashMap();
    private final Map bools = new HashMap();
    private final Map triggers = new HashMap();

    public void setFloat(String name, float value) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("param name must not be empty");
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("param '" + name
                + "': value must be finite, got " + value);
        }
        floats.put(name, Float.valueOf(value));
    }

    public float getFloat(String name) {
        if (name == null) return 0f;
        Float f = (Float) floats.get(name);
        return f == null ? 0f : f.floatValue();
    }

    public void setBool(String name, boolean value) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("param name must not be empty");
        }
        bools.put(name, value ? Boolean.TRUE : Boolean.FALSE);
    }

    public boolean getBool(String name) {
        if (name == null) return false;
        Boolean b = (Boolean) bools.get(name);
        return b != null && b.booleanValue();
    }

    /** Sets a one-shot trigger; consumed on next {@link #consumeTrigger}. */
    public void fireTrigger(String name) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("trigger name must not be empty");
        }
        triggers.put(name, Boolean.TRUE);
    }

    /**
     * Reads + clears a trigger atomically. Returns true once after each
     * {@link #fireTrigger}; subsequent reads return false until fired
     * again. Lets state-machine nodes act on a trigger exactly once.
     */
    public boolean consumeTrigger(String name) {
        if (name == null) return false;
        Boolean b = (Boolean) triggers.remove(name);
        return b != null && b.booleanValue();
    }

    /** Peeks a trigger without consuming. */
    public boolean peekTrigger(String name) {
        if (name == null) return false;
        Boolean b = (Boolean) triggers.get(name);
        return b != null && b.booleanValue();
    }
}
