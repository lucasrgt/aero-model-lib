package aero.modellib;

/**
 * Platform-neutral animation playback state.
 *
 * ModLoader and StationAPI wrappers extend this class only to add their NBT
 * read/write adapters. Tick timing, clip caching and interpolation live here
 * so both loaders share exactly the same behavior.
 */
public class Aero_AnimationPlayback {

    /** Current state (public for renderers and machine logic). */
    public int currentState;

    protected final Aero_AnimationDefinition def;
    protected final Aero_AnimationBundle bundle;

    private float playbackTime;
    private float prevPlaybackTime;

    private Aero_AnimationClip cachedClip;
    private int cachedClipState = -1;

    public Aero_AnimationPlayback(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this.def = def;
        this.bundle = bundle;
        this.currentState = 0;
        this.playbackTime = 0f;
        this.prevPlaybackTime = 0f;
    }

    /** Advances playback by one game tick. Call before setState(). */
    public void tick() {
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        if (clip.loop) {
            if (playbackTime >= clip.length) {
                playbackTime = playbackTime % clip.length;
                if (prevPlaybackTime >= clip.length) prevPlaybackTime = prevPlaybackTime % clip.length;
            }
        } else if (playbackTime >= clip.length) {
            playbackTime = clip.length;
            prevPlaybackTime = clip.length;
        }
    }

    /**
     * Changes current state. Playback resets only when the target clip changes.
     */
    public void setState(int stateId) {
        if (stateId == currentState) return;

        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);

        currentState = stateId;
        cachedClipState = -1;

        boolean clipChanged = (newClip == null) ? (oldClip != null) : !newClip.equals(oldClip);
        if (clipChanged) {
            playbackTime = 0f;
            prevPlaybackTime = 0f;
        }
    }

    /**
     * Returns playback time interpolated for a render frame.
     *
     * Handles loop wrap without jumping backward across the clip boundary.
     */
    public float getInterpolatedTime(float partialTick) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) return 0f;

        float cur = playbackTime;
        float prev = prevPlaybackTime;

        if (clip.loop && cur < prev) {
            cur += clip.length;
            float t = prev + (cur - prev) * partialTick;
            return t % clip.length;
        }

        return prev + (cur - prev) * partialTick;
    }

    /** Returns the currently active clip, or null if the state has no clip. */
    public Aero_AnimationClip getCurrentClip() {
        if (cachedClipState == currentState) return cachedClip;
        String clipName = def.getClipName(currentState);
        cachedClip = clipName != null ? bundle.getClip(clipName) : null;
        cachedClipState = currentState;
        return cachedClip;
    }

    public Aero_AnimationBundle getBundle() { return bundle; }
    public Aero_AnimationDefinition getDef() { return def; }

    protected float getPlaybackTime() { return playbackTime; }

    protected void restorePlayback(int stateId, float time) {
        currentState = stateId;
        cachedClipState = -1;
        playbackTime = time;
        prevPlaybackTime = time;
    }
}
