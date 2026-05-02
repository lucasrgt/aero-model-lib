package aero.modellib;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * Platform-neutral animation playback state.
 *
 * ModLoader and StationAPI wrappers extend this class only to add their NBT
 * read/write adapters. Tick timing, clip caching and interpolation live here
 * so both loaders share exactly the same behavior.
 */
public class Aero_AnimationPlayback {

    private int currentState;

    protected final Aero_AnimationDefinition def;
    protected final Aero_AnimationBundle bundle;

    private float playbackTime;
    private float prevPlaybackTime;

    private Aero_AnimationClip cachedClip;
    private int cachedClipState = -1;
    private Aero_AnimationClip cursorClip;
    private int[] rotCursors;
    private int[] posCursors;
    private int[] sclCursors;
    private int[] uvOffsetCursors;
    private int[] uvScaleCursors;

    // Transition machinery — keeps the snapshotted pose from the previous
    // clip alive while the new clip's first {transitionTicks} ticks blend
    // toward it. Maps are name-indexed so they survive bone-index reshuffles
    // when switching between clips that animate different sets of bones.
    private int transitionTicks = 0;
    private int transitionRemaining = 0;
    private Map snapshotRot;   // Map<String, float[3]>
    private Map snapshotPos;
    private Map snapshotScl;
    private Map snapshotUvOffset;
    private Map snapshotUvScale;
    // Reusable buffer to avoid allocating during the snapshot pass.
    private final float[] snapshotScratch = new float[3];
    private final float[] pivotScratch = new float[3];

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
        Aero_Profiler.start("aero.playback.tick");
        try {
            tickBody();
        } finally {
            Aero_Profiler.end("aero.playback.tick");
        }
    }

    private void tickBody() {
        if (transitionRemaining > 0) transitionRemaining--;
        prevPlaybackTime = playbackTime;

        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            playbackTime = 0f;
            return;
        }

        playbackTime += 1f / 20f;

        boolean wrapped = false;
        if (clip.loop == Aero_AnimationLoop.LOOP) {
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
        // (prev..length], then [0..now) so events at the very end and the
        // very start of the loop both fire each cycle without being double-
        // counted. Non-wrap windows use the standard half-open interval.
        if (eventListener != null && clip.hasEvents()) {
            if (wrapped) {
                fireEvents(clip, prevPlaybackTime, clip.length, false);
                fireEvents(clip, 0f, playbackTime, true);
            } else {
                fireEvents(clip, prevPlaybackTime, playbackTime, false);
            }
        }
    }

    /**
     * Fires every event in the clip whose time falls inside the advanced
     * window. {@code includeFrom} controls whether the lower bound is
     * inclusive — only the post-wrap leg of a looped tick passes
     * {@code true}, so a {@code t = 0} event fires exactly once per loop
     * cycle (it would otherwise be swallowed by the strict {@code t > 0}
     * test that prevents double-firing on consecutive non-wrap ticks).
     */
    private void fireEvents(Aero_AnimationClip clip, float fromBound, float toInclusive,
                            boolean includeFrom) {
        if (toInclusive < fromBound || (toInclusive == fromBound && !includeFrom)) return;
        Aero_AnimationClip.KeyframeEvent[] events = clip.events;
        if (events.length == 0) return;

        // Events are sorted by time at clip construction (see
        // Aero_AnimationClip constructor). Binary-search for the first
        // event whose time satisfies the lower bound; iterate forward
        // and break when we pass the upper bound. Worst case becomes
        // O(log n + matches) instead of O(n).
        int start = lowerBound(events, fromBound, includeFrom);
        for (int i = start; i < events.length; i++) {
            Aero_AnimationClip.KeyframeEvent event = events[i];
            float t = event.time;
            if (t > toInclusive) break;
            eventListener.onEvent(event.channel, event.data, event.locator, t);
        }
    }

    /**
     * Returns the index of the first event with {@code time >= bound}
     * (when {@code inclusive}) or {@code time > bound} (when not). Events
     * MUST be sorted by ascending time. Returns {@code events.length}
     * when every event lies before the bound.
     */
    private static int lowerBound(Aero_AnimationClip.KeyframeEvent[] events,
                                  float bound, boolean inclusive) {
        int lo = 0, hi = events.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            float t = events[mid].time;
            boolean before = inclusive ? (t < bound) : (t <= bound);
            if (before) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    private static float normalizePlaybackTime(Aero_AnimationClip clip, float timeSeconds) {
        if (clip == null || clip.length <= 0f || Float.isNaN(timeSeconds)
            || Float.isInfinite(timeSeconds)) {
            return 0f;
        }
        if (clip.loop == Aero_AnimationLoop.LOOP) {
            float time = timeSeconds % clip.length;
            return time < 0f ? time + clip.length : time;
        }
        if (timeSeconds <= 0f) return 0f;
        return timeSeconds >= clip.length ? clip.length : timeSeconds;
    }

    /**
     * True when the active clip has reached its final keyframe AND its
     * loop type is {@link Aero_AnimationLoop#PLAY_ONCE}. HOLD
     * clips never finish (their pose just stays). LOOP clips never finish
     * (they wrap forever). Useful as a signal to advance the state machine
     * to the next clip in a chain.
     */
    public boolean isFinished() {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null) return false;
        return clip.loop == Aero_AnimationLoop.PLAY_ONCE
            && playbackTime >= clip.length;
    }

    /**
     * Seeks the active clip without firing keyframe events. Both current and
     * previous playback times are updated so the next render frame does not
     * interpolate across the old position.
     *
     * <p>This is useful when many identical machines are placed together and
     * should start at deterministic but different phases. Call after
     * {@link #setState(int)} selects the looping state.
     */
    public void setPlaybackTime(float timeSeconds) {
        Aero_AnimationClip clip = getCurrentClip();
        float time = normalizePlaybackTime(clip, timeSeconds);
        playbackTime = time;
        prevPlaybackTime = time;
        resetSampleCursors();
    }

    /**
     * Seeks the active clip to a normalized phase in [0, 1). Values outside
     * that range wrap, so callers can feed a hash-derived float directly.
     */
    public void setLoopPhase(float phase01) {
        Aero_AnimationClip clip = getCurrentClip();
        if (clip == null || clip.length <= 0f) {
            setPlaybackTime(0f);
            return;
        }
        float phase = phase01 - (float) Math.floor(phase01);
        setPlaybackTime(phase * clip.length);
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
            resetSampleCursors();
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
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleRotInto(boneIdx, time, out, ensureRotCursors(clip));
        return blendWithSnapshot(snapshotRot, boneName, partialTick, got, out);
    }

    public boolean samplePosBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.samplePosInto(boneIdx, time, out, ensurePosCursors(clip));
        return blendWithSnapshot(snapshotPos, boneName, partialTick, got, out);
    }

    public boolean sampleSclBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                    float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleSclInto(boneIdx, time, out, ensureSclCursors(clip));
        // Scale rests at 1 (identity), not 0. A bone present in the OLD clip
        // but absent from the NEW one must fade its scale toward 1, not 0 —
        // otherwise the part collapses into the pivot during crossfade.
        return blendWithSnapshot(snapshotScl, boneName, partialTick, got, out, 1f);
    }

    /**
     * Samples the per-bone UV offset (rest = 0) — matches scroll/atlas-frame
     * style animations. Output components: x=u, y=v (z reserved).
     */
    public boolean sampleUvOffsetBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                         float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleUvOffsetInto(boneIdx, time, out, ensureUvOffsetCursors(clip));
        // UV offset rests at 0 (identity, no scroll).
        return blendWithSnapshot(snapshotUvOffset, boneName, partialTick, got, out, 0f);
    }

    /**
     * Samples the per-bone UV scale (rest = 1). Use to pulse texture zoom
     * or pick atlas frames in combination with sampleUvOffset.
     */
    public boolean sampleUvScaleBlended(Aero_AnimationClip clip, int boneIdx, String boneName,
                                        float time, float partialTick, float[] out) {
        boolean got = clip != null && boneIdx >= 0
            && clip.sampleUvScaleInto(boneIdx, time, out, ensureUvScaleCursors(clip));
        // UV scale rests at 1 (identity).
        return blendWithSnapshot(snapshotUvScale, boneName, partialTick, got, out, 1f);
    }

    private int[] ensureRotCursors(Aero_AnimationClip clip) {
        ensureCursorClip(clip);
        if (rotCursors == null || rotCursors.length < clip.boneNames.length) {
            rotCursors = newCursorArray(clip.boneNames.length);
        }
        return rotCursors;
    }

    private int[] ensurePosCursors(Aero_AnimationClip clip) {
        ensureCursorClip(clip);
        if (posCursors == null || posCursors.length < clip.boneNames.length) {
            posCursors = newCursorArray(clip.boneNames.length);
        }
        return posCursors;
    }

    private int[] ensureSclCursors(Aero_AnimationClip clip) {
        ensureCursorClip(clip);
        if (sclCursors == null || sclCursors.length < clip.boneNames.length) {
            sclCursors = newCursorArray(clip.boneNames.length);
        }
        return sclCursors;
    }

    private int[] ensureUvOffsetCursors(Aero_AnimationClip clip) {
        ensureCursorClip(clip);
        if (uvOffsetCursors == null || uvOffsetCursors.length < clip.boneNames.length) {
            uvOffsetCursors = newCursorArray(clip.boneNames.length);
        }
        return uvOffsetCursors;
    }

    private int[] ensureUvScaleCursors(Aero_AnimationClip clip) {
        ensureCursorClip(clip);
        if (uvScaleCursors == null || uvScaleCursors.length < clip.boneNames.length) {
            uvScaleCursors = newCursorArray(clip.boneNames.length);
        }
        return uvScaleCursors;
    }

    private void ensureCursorClip(Aero_AnimationClip clip) {
        if (cursorClip == clip) return;
        cursorClip = clip;
        resetCursorArray(rotCursors);
        resetCursorArray(posCursors);
        resetCursorArray(sclCursors);
        resetCursorArray(uvOffsetCursors);
        resetCursorArray(uvScaleCursors);
    }

    private void resetSampleCursors() {
        cursorClip = null;
        resetCursorArray(rotCursors);
        resetCursorArray(posCursors);
        resetCursorArray(sclCursors);
        resetCursorArray(uvOffsetCursors);
        resetCursorArray(uvScaleCursors);
    }

    private static int[] newCursorArray(int length) {
        int[] cursors = new int[length];
        Arrays.fill(cursors, -1);
        return cursors;
    }

    private static void resetCursorArray(int[] cursors) {
        if (cursors != null) Arrays.fill(cursors, -1);
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
     *   <li>only snapshot → fade snapshot value out toward {@code restValue}
     *       as alpha→1 (rotation/position rest at 0, scale rests at 1) so a
     *       bone present in the OLD clip but absent from the NEW one
     *       returns to its rest pose smoothly instead of snapping</li>
     * </ul>
     */
    private boolean blendWithSnapshot(Map snapshotMap, String boneName, float partialTick,
                                      boolean newSampleValid, float[] out) {
        return blendWithSnapshot(snapshotMap, boneName, partialTick, newSampleValid, out, 0f);
    }

    private boolean blendWithSnapshot(Map snapshotMap, String boneName, float partialTick,
                                      boolean newSampleValid, float[] out, float restValue) {
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
            // Lerp snapshot → restValue as alpha goes 0 → 1.
            out[0] = snap[0] + (restValue - snap[0]) * a;
            out[1] = snap[1] + (restValue - snap[1]) * a;
            out[2] = snap[2] + (restValue - snap[2]) * a;
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
        if (snapshotUvOffset == null) snapshotUvOffset = new HashMap();
        else                          snapshotUvOffset.clear();
        if (snapshotUvScale == null) snapshotUvScale = new HashMap();
        else                         snapshotUvScale.clear();

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
            if (clip.sampleUvOffsetInto(bi, time, snapshotScratch)) {
                snapshotUvOffset.put(name, new float[]{snapshotScratch[0], snapshotScratch[1], snapshotScratch[2]});
            }
            if (clip.sampleUvScaleInto(bi, time, snapshotScratch)) {
                snapshotUvScale.put(name, new float[]{snapshotScratch[0], snapshotScratch[1], snapshotScratch[2]});
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

        if (clip.loop == Aero_AnimationLoop.LOOP && cur < prev) {
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
    public int getCurrentState() { return currentState; }

    /**
     * Resolves a locator (bone name) to its current animated pivot in
     * block units, relative to the BE / entity origin. Returns the
     * keyframed-position-offset added to the bundle's rest pivot for that
     * bone — so a locator declared on "muzzle" gives you "where the muzzle
     * IS right now in the animation", not where it started.
     *
     * <p>Rotation and scale are deliberately NOT applied here: the pivot
     * is a single point and rotation/scale don't move it (they rotate or
     * scale the surrounding mesh AROUND the pivot). For "tip of a swinging
     * blade" the OBJ should declare a separate bone at the tip with its
     * own pivot — then this method returns the tip's animated position
     * directly.
     *
     * <p>Returns {@code true} when the bone is known to the active clip
     * AND the bundle (so {@code out} now holds a meaningful position);
     * {@code false} otherwise, with {@code out} left untouched. Listeners
     * that need a fallback should check the return and use the BE coords.
     */
    public boolean getAnimatedPivot(String boneName, float partialTick, float[] out) {
        if (boneName == null || out == null) return false;
        Aero_AnimationClip clip = getCurrentClip();
        if (!bundle.getPivotInto(boneName, out)) return false;

        if (clip != null) {
            int bi = clip.indexOfBone(boneName);
            if (bi >= 0) {
                if (clip.samplePosInto(bi, getInterpolatedTime(partialTick), pivotScratch)) {
                    // Position offsets are stored in pixels (Blockbench
                    // convention); divide by 16 to bring them into the
                    // block-unit space of the pivot.
                    out[0] += pivotScratch[0] * (1f / 16f);
                    out[1] += pivotScratch[1] * (1f / 16f);
                    out[2] += pivotScratch[2] * (1f / 16f);
                }
            }
        }
        return true;
    }

    protected float getPlaybackTime() { return playbackTime; }

    protected void restorePlayback(int stateId, float time) {
        currentState = stateId;
        cachedClipState = -1;
        playbackTime = time;
        prevPlaybackTime = time;
    }
}
