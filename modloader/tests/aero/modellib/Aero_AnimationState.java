package aero.modellib;


import aero.modellib.animation.Aero_AnimationBundle;
import aero.modellib.animation.Aero_AnimationDefinition;
import aero.modellib.animation.Aero_AnimationPlayback;

/**
 * Test-only platform stub so Aero_AnimationDefinition can compile with the
 * pure-Java test suite. Real loader builds provide their own NBT-aware class.
 */
public class Aero_AnimationState extends Aero_AnimationPlayback {
    public Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        super(def, bundle);
    }

    public Aero_AnimationState(Aero_AnimationDefinition def, Aero_AnimationBundle bundle,
                        String nbtKeyPrefix) {
        super(def, bundle);
        if (nbtKeyPrefix == null || nbtKeyPrefix.length() == 0) {
            throw new IllegalArgumentException("nbtKeyPrefix must not be empty");
        }
    }
}
