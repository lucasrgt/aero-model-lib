package aero.modellib;

import java.util.HashMap;
import java.util.Map;

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

    // Transition machinery — keeps the snapshotted pose from the previous
    // clip alive while the new clip's first {transitionTicks} ticks blend
    // toward it. Maps are name-indexed so they survive bone-index reshuffles
    // when switching between clips that animate different sets of bones.
    private int transitionTicks = 0;
    private int transitionRemaining = 0;
    private Map snapshotRot;   // Map<String, float[3]>
    private Map snapshotPos;
    private Map snapshotScl;
    // Reusable buffer to avoid allocating during the snapshot pass.
    private final float[] snapshotScratch = new float[3];

    // Optional keyframe-event sink — set by the consumer to receive
    // sound/particle/custom events from the playing clip.
    private Aero_AnimationEventListener eventListener;

    public Aero_AnimationPlayback(Aero_AnimationDefinition def, Aero_AnimationBundle bundle) {
        this.def = def;
        this.bundle = bundle;
        this.currentState = 0;
        this.playbackTime = 0f;
        this.prevPlaybackTime = 0f;
    }

    /**
     * Registers a listener that receives non-pose keyframe events fired by
     * the active clip during {@link #tick()}. Pass {@code null} to clear.
     */
    public void setEventListener(Aero_AnimationEventListener listener) {
        this.eventListener = listener;
    }

    /** Advances playback by one game tick. Call before setState(). */
    public void tick() {
        if (transitionRemaining > 0) transitionRemaining--;
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        boolean wrapped = false;
        if (clip.loopType == Aero_AnimationClip.LOOP_TYPE_LOOP) {
            if (playbackTime >= clip.length) {
                playbackTime = playbackTime % clip.length;
                if (prevPlaybackTime >= clip.length) prevPlaybackTime = prevPlaybackTime % clip.length;
                wrapped = true;
            }
        } else if (playbackTime >= clip.length) {
            // PLAY_ONCE and HOLD both clamp at the final keyframe — the
            // visual difference is captured by isFinished(), which only
            // PLAY_ONCE flips to true so callers can chain into the next
            // clip while HOLD keeps holding the last pose.
            playbackTime = clip.length;
            prevPlaybackTime = clip.length;
        }

        // Fire any non-pose keyframes whose timestamp lies in the just-
        // advanced window. For looped wraps the window splits in two
        // (prev..length, then 0..now) so events at the very end and the
        // very start of the loop don't get swallowed by the boundary.
        if (eventListener != null && clip.hasEvents()) {
            if (wrapped) {
                fireEvents(clip, prevPlaybackTime, clip.length);
                fireEvents(clip, 0f, playbackTime);
            } else {
                fireEvents(clip, prevPlaybackTime, playbackTime);
            }
        }
    }

    /**
     * Fires every event in the clip whose time t satisfies
     * {@code fromExclusive < t <= toInclusive}. Open lower bound stops the
     * same event from firing twice when prev == event time on consecutive
     * ticks (an edge case after {@link #setStateWithTransition} resets the
     * playback head to 0).
     */
    private void fireEvents(Aero_AnimationClip clip, float fromExclusive, float toInclusive) {
        if (toInclusive <= fromExclusive) return;
        float[] times = clip.eventTimes;
        for (int i = 0; i < times.length; i++) {
            float t = times[i];
            if (t > fromExclusive && t <= toInclusive) {
                eventListener.onEvent(clip.eventChannels[i], clip.eventData[i], t);
            }
        }
    }

    /**
     * True when the active clip has reached its final keyframe AND its
     * loop type is {@link Aero_AnimationClip#LOOP_TYPE_PLAY_ONCE}. HOLD
     * clips never finish (their pose just stays). LOOP clips never finish
     * (they wrap forever). Useful as a signal to advance the state machine
     * to the next clip in a chain.
     */
    public boolean isFinished() {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null) return false;
        return clip.loopType == Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE
            && playbackTime >= clip.length;
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
     * Same as {@link #setState(int)} but smoothly fades the previous pose
     * into the new clip's start over the next {@code ticks} game ticks.
     * Call before {@link #tick()} on the same frame so the transition
     * counts down starting from the next tick.
     *
     * <p>If {@code ticks <= 0} or the target clip is identical, behaves
     * like the bare setState (snap, no fade).
     *
     * <p>Snapshot covers every bone in the OLD clip — bones that only exist
     * in the NEW clip start from the new value with no blend (no per-bone
     * "wrong identity" pop), and bones that only exist in the OLD clip
     * fade out to zero over the transition.
     */
    public void setStateWithTransition(int stateId, int ticks) {
        if (ticks <= 0) {
            setState(stateId);
            transitionTicks = 0;
            transitionRemaining = 0;
            return;
        }
        String oldClip = def.getClipName(currentState);
        String newClip = def.getClipName(stateId);
        boolean clipChanged = (newClip == null) ? (oldClip != null) : !newClip.equals(oldClip);
        if (!clipChanged) {
            // Same clip — nothing to blend, just adopt the state.
            currentState = stateId;
            return;
        }
        captureSnapshot();
        transitionTicks = ticks;
        transitionRemaining = ticks;
        setState(stateId);
    }

    /** True while a transition is still ramping the snapshot toward the new clip. */
    public boolean inTransition() {
        return transitionRemaining > 0 && transitionTicks > 0;
    }

    /**
     * Returns the blend ratio for the current frame: 0 = full snapshot
     * pose, 1 = full new clip. Linear over the configured transition
     * duration; clamps at 1 once the transition ends.
     */
    public float getTransitionAlpha(float partialTick) {
        if (transitionTicks <= 0) return 1f;
        // ticksDone counts from 1 on the first tick after setState until
        // it reaches transitionTicks — partialTick smooths the boundaries
        // so the blend doesn't step on tick edges.
        float ticksDone = (transitionTicks - transitionRemaining) + partialTick;
        if (ticksDone >= transitionTicks) return 1f;
        if (ticksDone <= 0f) return 0f;
        return ticksDone / (float) transitionTicks;
    }

    /**
     * Samples rotation for {@code boneIdx} in {@code clip} at {@code time},
     * blending against the snapshot pose for {@code boneName} when in
     * transition. Returns true if {@code out} now contains a usable value
     * (either a fresh sample, a blended sample, or a fading-out snapshot).
     *
     * <p>Renderers should call this in place of {@link Aero_AnimationClip#sampleRotInto}
     * to get free fade-in behaviour after {@link #setStateWithTransition}.
     */
    public boolean sampleRotBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0 && clip.sampleRotInto(boneIdx, time, out);
        return blendWithSnapshot(snapshotRot, boneName, partialTick, got, out);
    }

    public boolean samplePosBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0 && clip.samplePosInto(boneIdx, time, out);
        return blendWithSnapshot(snapshotPos, boneName, partialTick, got, out);
    }

    public boolean sampleSclBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0 && clip.sampleSclInto(boneIdx, time, out);
        return blendWithSnapshot(snapshotScl, boneName, partialTick, got, out);
    }

    /**
     * Shared blend kernel for the three sample channels. Picks one of three
     * outcomes based on whether the new clip + the snapshot have data for
     * the given bone:
     *
     * <ul>
     *   <li>both → linear lerp from snapshot to new value, ratio = alpha</li>
     *   <li>only new → return new value as-is (no fade-in needed since the
     *       previous pose for this bone was identity)</li>
     *   <li>only snapshot → fade snapshot value out toward 0 as alpha→1
     *       (so a bone present in the OLD clip but absent from the NEW one
     *       returns to its rest pose smoothly instead of snapping)</li>
     * </ul>
     */
    private boolean blendWithSnapshot(Map snapshotMap, String boneName, float partialTick,
                                      boolean newSampleValid, float[] out) {
        if (!inTransition() || snapshotMap == null || boneName == null) {
            return newSampleValid;
        }
        float[] snap = (float[]) snapshotMap.get(boneName);
        if (snap == null) return newSampleValid;

        float a = getTransitionAlpha(partialTick);
        if (newSampleValid) {
            out[0] = snap[0] + (out[0] - snap[0]) * a;
            out[1] = snap[1] + (out[1] - snap[1]) * a;
            out[2] = snap[2] + (out[2] - snap[2]) * a;
        } else {
            float k = 1f - a;
            out[0] = snap[0] * k;
            out[1] = snap[1] * k;
            out[2] = snap[2] * k;
        }
        return true;
    }

    /**
     * Captures the OLD clip's pose at the current playback time into the
     * snapshot maps, so subsequent sampleXxxBlended calls can fade from
     * here to the new clip.
     *
     * <p>Reuses the same map instances across transitions to avoid GC
     * pressure from repeated state changes. {@code snapshotScratch} holds
     * the per-call read-out so each captured pose only allocates the
     * tiny float[3] that will live in the map.
     */
    private void captureSnapshot() {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null) return;

        if (snapshotRot == null) snapshotRot = new HashMap();
        else                     snapshotRot.clear();
        if (snapshotPos == null) snapshotPos = new HashMap();
        else                     snapshotPos.clear();
        if (snapshotScl == null) snapshotScl = new HashMap();
        else                     snapshotScl.clear();

        float time = playbackTime;
        for (int bi = 0; bi < clip.boneNames.length; bi++) {
            String name = clip.boneNames[bi];
            if (clip.sampleRotInto(bi, time, snapshotScratch)) {
                snapshotRot.put(name, new float[]{snapshotScratch[0], snapshotScratch[1], snapshotScratch[2]});
            }
            if (clip.samplePosInto(bi, time, snapshotScratch)) {
                snapshotPos.put(name, new float[]{snapshotScratch[0], snapshotScratch[1], snapshotScratch[2]});
            }
            if (clip.sampleSclInto(bi, time, snapshotScratch)) {
                snapshotScl.put(name, new float[]{snapshotScratch[0], snapshotScratch[1], snapshotScratch[2]});
            }
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

        if (clip.loopType == Aero_AnimationClip.LOOP_TYPE_LOOP && cur < prev) {
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
