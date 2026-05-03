package aero.modellib.animation;

public enum Aero_AnimationLoop {
    PLAY_ONCE("play_once"),
    LOOP("loop"),
    HOLD_ON_LAST_FRAME("hold_on_last_frame");

    public final String jsonName;

    Aero_AnimationLoop(String jsonName) {
        this.jsonName = jsonName;
    }

    public static Aero_AnimationLoop fromName(String name) {
        if (name == null) throw new IllegalArgumentException("loop must be a string");
        Aero_AnimationLoop[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].jsonName.equals(name)) return values[i];
        }
        throw new IllegalArgumentException("unknown loop type: " + name);
    }
}
