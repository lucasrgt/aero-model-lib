package aero.modellib;

/**
 * Blend mode applied by mesh renderers when {@link Aero_RenderOptions}
 * carries blending. Maps to a GL blend func + enable pair.
 *
 * <ul>
 *   <li>{@link #OFF} — {@code GL_BLEND} disabled, opaque output.</li>
 *   <li>{@link #ALPHA} — {@code GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA};
 *       standard translucency. Use for ghosts, ghost-block previews,
 *       windowed materials.</li>
 *   <li>{@link #ADDITIVE} — {@code GL_SRC_ALPHA, GL_ONE}; pixels brighten
 *       what's behind them. Use for energy beams, glow halos, plasma
 *       effects, magic auras.</li>
 * </ul>
 */
public enum Aero_MeshBlendMode {
    OFF,
    ALPHA,
    ADDITIVE
}
