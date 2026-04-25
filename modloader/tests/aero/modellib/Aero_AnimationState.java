package aero.modellib;

/**
 * Test-only platform stub so Aero_AnimationDefinition can compile with the
 * pure-Java test suite. Real loader builds provide their own NBT-aware class.
 */
class Aero_AnimationState extends Aero_AnimationPlayback {
    Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        super(def, bundle);
    }
}
