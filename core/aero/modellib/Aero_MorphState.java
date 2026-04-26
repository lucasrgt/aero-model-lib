package aero.modellib;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-instance morph weights — usually attached to a tile/entity
 * alongside an {@link Aero_AnimationState} so persistent state survives
 * world saves.
 *
 * <p>Weights are unbounded by design (clamping at runtime would silently
 * eat overshoot, which is occasionally desired for stylized animation).
 * Renderers that need clamped behavior should clamp on read.
 *
 * <p>NBT serialization is delegated to a platform adapter so this class
 * stays in pure-Java {@code core/} — see
 * {@link Aero_MorphState#writeStringFloatMapNbt} for the contract.
 */
public final class Aero_MorphState {

    private final Map weights = new HashMap();

    public boolean isEmpty() {
        if (weights.isEmpty()) return true;
        Iterator it = weights.values().iterator();
        while (it.hasNext()) {
            float w = ((Float) it.next()).floatValue();
            if (w != 0f) return false;
        }
        return true;
    }

    public float get(String name) {
        if (name == null) return 0f;
        Float f = (Float) weights.get(name);
        return f == null ? 0f : f.floatValue();
    }

    public void set(String name, float weight) {
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("morph name must not be empty");
        }
        if (Float.isNaN(weight) || Float.isInfinite(weight)) {
            throw new IllegalArgumentException("morph '" + name
                + "': weight must be finite, got " + weight);
        }
        if (weight == 0f) {
            weights.remove(name);
        } else {
            weights.put(name, Float.valueOf(weight));
        }
    }

    public void clear() {
        weights.clear();
    }

    /** Read-only view for renderers + serialization adapters. */
    public Map getWeightsView() {
        return weights;
    }

    /**
     * Writes morph weights into an NBT-compatible bag via the supplied
     * adapter. The adapter receives entries in deterministic key order
     * (HashMap iteration order — fine for save round-trips since we
     * also read by key, not order). Caller's adapter knows how to
     * write {@code (String, float)} pairs into its NBT compound.
     */
    public void writeStringFloatMapNbt(StringFloatBagWriter writer) {
        Iterator it = weights.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            writer.put((String) e.getKey(), ((Float) e.getValue()).floatValue());
        }
    }

    /**
     * Reads weights from a bag, replacing any existing state.
     */
    public void readStringFloatMapNbt(StringFloatBagReader reader) {
        weights.clear();
        reader.forEach(new StringFloatBagWriter() {
            public void put(String name, float value) {
                if (value != 0f) weights.put(name, Float.valueOf(value));
            }
        });
    }

    /** Adapter interface for writing into NBT (or any string→float bag). */
    public interface StringFloatBagWriter {
        void put(String name, float value);
    }

    /** Adapter interface for reading out of NBT. */
    public interface StringFloatBagReader {
        void forEach(StringFloatBagWriter sink);
    }
}
