package aero.modellib;

/**
 * One playback head inside an {@link Aero_AnimationStack}. Wraps a
 * {@link Aero_AnimationPlayback} with two render-time knobs:
 *
 * <ul>
 *   <li>{@link #additive} — when {@code true} the layer's per-bone deltas
 *       are <em>summed</em> into the running pose; when {@code false} they
 *       <em>replace</em> whatever the previous layers produced for that
 *       bone. Replace mode is the default and matches single-clip playback;
 *       additive mode is for stacking secondary motions (head look, arm
 *       wave, hit reaction) on top of a base loop.</li>
 *   <li>{@link #weight} — multiplier applied to the layer's contribution
 *       (0..1). Lets a fade-in/fade-out of a secondary layer happen
 *       independently from any clip-internal transitions.</li>
 * </ul>
 *
 * The stack iterates layers in insertion order, so a typical setup is
 * <pre>
 *   stack.add(walkLayer);              // base, replace, weight 1
 *   stack.add(armWaveLayer.additive(true));
 * </pre>
 * with the base providing the full skeleton pose and the additive layer
 * only animating the bones it cares about.
 */
public final class Aero_AnimationLayer {

    public final Aero_AnimationPlayback playback;
    public boolean additive;
    public float   weight;

    public Aero_AnimationLayer(Aero_AnimationPlayback playback) {
        this(playback, false, 1.0f);
    }

    public Aero_AnimationLayer(Aero_AnimationPlayback playback, boolean additive, float weight) {
        if (playback == null) throw new IllegalArgumentException("playback must not be null");
        this.playback = playback;
        this.additive = additive;
        this.weight   = weight;
    }

    /** Fluent setter — {@code stack.add(new Aero_AnimationLayer(pb).additive(true))}. */
    public Aero_AnimationLayer additive(boolean v) { this.additive = v; return this; }
    /** Fluent setter — opacity-style multiplier, clamped at the call site if you need [0,1]. */
    public Aero_AnimationLayer weight(float v)     { this.weight   = v; return this; }
}
