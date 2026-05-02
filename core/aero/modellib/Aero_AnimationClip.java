package aero.modellib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable animation clip.
 *
 * The public construction path is {@link #builder(String)}. Internally the
 * clip is structured as bone tracks and channel tracks rather than one large
 * telescoping constructor with parallel arrays.
 */
public final class Aero_AnimationClip {

    public final String name;
    public final Aero_AnimationLoop loop;
    public final float length;

    final String[] boneNames;
    final BoneTrack[] bones;
    final KeyframeEvent[] events;

    private final Map boneIndexByName;
    private final boolean hasUvAnimation;

    public static Builder builder(String name) {
        return new Builder(name);
    }

    private Aero_AnimationClip(Builder builder) {
        if (builder.name == null || builder.name.length() == 0) {
            throw new IllegalArgumentException("clip name must not be empty");
        }
        if (builder.length < 0f || Float.isNaN(builder.length) || Float.isInfinite(builder.length)) {
            throw new IllegalArgumentException("clip '" + builder.name
                + "': length must be finite and >= 0, got " + builder.length);
        }

        this.name = builder.name;
        this.loop = builder.loop;
        this.length = builder.length;
        this.bones = new BoneTrack[builder.bones.size()];
        this.boneNames = new String[bones.length];

        boolean uv = false;
        for (int i = 0; i < bones.length; i++) {
            BoneBuilder b = (BoneBuilder) builder.bones.get(i);
            bones[i] = b.build();
            boneNames[i] = bones[i].name;
            uv |= bones[i].uvOffset != null || bones[i].uvScale != null;
        }
        this.hasUvAnimation = uv;
        this.boneIndexByName = buildBoneIndex(boneNames);

        Collections.sort(builder.events, new Comparator() {
            public int compare(Object a, Object b) {
                KeyframeEvent ea = (KeyframeEvent) a;
                KeyframeEvent eb = (KeyframeEvent) b;
                return Float.compare(ea.time, eb.time);
            }
        });
        this.events = (KeyframeEvent[]) builder.events.toArray(new KeyframeEvent[builder.events.size()]);
    }

    public boolean hasEvents() {
        return events.length > 0;
    }

    /** True when any bone in this clip animates UV offset or UV scale. */
    public boolean hasUvAnimation() {
        return hasUvAnimation;
    }

    // Single-entry reference-equality cache for indexOfBone. The hot
    // pattern is multi-layer stacks calling indexOfBone(boneName) 3-N
    // times in a row with the same {@code boneName} reference (one
    // call per layer or per channel inside Aero_AnimationStack /
    // Aero_AnimationPlayback). The cache turns those follow-ups into
    // a reference-equality compare + array-equivalent hit.
    private String cachedBoneNameRef;
    private int cachedBoneIdx;

    public int indexOfBone(String name) {
        // Reference-eq fast path — covers the "same boneName called N
        // times in a tight loop" pattern. JVM-interned strings compare
        // by reference too, so callers passing literal strings hit this.
        if (name == cachedBoneNameRef) return cachedBoneIdx;
        Integer idx = (Integer) boneIndexByName.get(name);
        int resolved = idx != null ? idx.intValue() : -1;
        cachedBoneNameRef = name;
        cachedBoneIdx = resolved;
        return resolved;
    }

    public boolean sampleRotInto(int boneIdx, float time, float[] out) {
        return sampleChannel(boneIdx, time, out, Channel.ROTATION);
    }

    boolean sampleRotInto(int boneIdx, float time, float[] out, int[] cursor) {
        return sampleChannel(boneIdx, time, out, Channel.ROTATION, cursor);
    }

    public boolean samplePosInto(int boneIdx, float time, float[] out) {
        return sampleChannel(boneIdx, time, out, Channel.POSITION);
    }

    boolean samplePosInto(int boneIdx, float time, float[] out, int[] cursor) {
        return sampleChannel(boneIdx, time, out, Channel.POSITION, cursor);
    }

    public boolean sampleSclInto(int boneIdx, float time, float[] out) {
        return sampleChannel(boneIdx, time, out, Channel.SCALE);
    }

    boolean sampleSclInto(int boneIdx, float time, float[] out, int[] cursor) {
        return sampleChannel(boneIdx, time, out, Channel.SCALE, cursor);
    }

    /**
     * Samples the per-bone UV offset (added to the vertex's u/v before
     * tess.addVertexWithUV). Vec3 with the third component reserved (the
     * lib only consumes out[0]=u and out[1]=v). Defaults to (0, 0) when
     * the bone has no uv_offset channel — see {@link Aero_BoneRenderPose}.
     */
    public boolean sampleUvOffsetInto(int boneIdx, float time, float[] out) {
        return sampleChannel(boneIdx, time, out, Channel.UV_OFFSET);
    }

    boolean sampleUvOffsetInto(int boneIdx, float time, float[] out, int[] cursor) {
        return sampleChannel(boneIdx, time, out, Channel.UV_OFFSET, cursor);
    }

    /**
     * Samples the per-bone UV scale (multiplies the vertex's u/v before the
     * offset is added). Defaults to (1, 1) when the bone has no uv_scale
     * channel — see {@link Aero_BoneRenderPose}.
     */
    public boolean sampleUvScaleInto(int boneIdx, float time, float[] out) {
        return sampleChannel(boneIdx, time, out, Channel.UV_SCALE);
    }

    boolean sampleUvScaleInto(int boneIdx, float time, float[] out, int[] cursor) {
        return sampleChannel(boneIdx, time, out, Channel.UV_SCALE, cursor);
    }

    private boolean sampleChannel(int boneIdx, float time, float[] out, Channel channel) {
        return sampleChannel(boneIdx, time, out, channel, null);
    }

    private boolean sampleChannel(int boneIdx, float time, float[] out,
                                  Channel channel, int[] cursor) {
        if (boneIdx < 0 || boneIdx >= bones.length) return false;
        ChannelTrack track = bones[boneIdx].track(channel);
        return track != null && track.sampleInto(time, out, cursor, boneIdx);
    }

    private static Map buildBoneIndex(String[] boneNames) {
        Map map = new HashMap((boneNames.length * 4 / 3) + 1);
        for (int i = 0; i < boneNames.length; i++) {
            map.put(boneNames[i], Integer.valueOf(i));
        }
        return map;
    }

    private static void copy3(float[] src, float[] dst) {
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
    }

    private static float cr(float p0, float p1, float p2, float p3,
                            float t, float t2, float t3) {
        return 0.5f * ((2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    private enum Channel {
        ROTATION,
        POSITION,
        SCALE,
        UV_OFFSET,
        UV_SCALE
    }

    static final class BoneTrack {
        final String name;
        final ChannelTrack rotation;
        final ChannelTrack position;
        final ChannelTrack scale;
        final ChannelTrack uvOffset;
        final ChannelTrack uvScale;

        BoneTrack(String name, ChannelTrack rotation,
                  ChannelTrack position, ChannelTrack scale,
                  ChannelTrack uvOffset, ChannelTrack uvScale) {
            this.name = name;
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
            this.uvOffset = uvOffset;
            this.uvScale = uvScale;
        }

        ChannelTrack track(Channel channel) {
            switch (channel) {
                case ROTATION:  return rotation;
                case POSITION:  return position;
                case SCALE:     return scale;
                case UV_OFFSET: return uvOffset;
                default:        return uvScale;
            }
        }
    }

    static final class ChannelTrack {
        final float[] times;
        final float[][] values;
        final Aero_Easing[] easings;

        // Non-null iff this is a rotation channel — pre-baked unit quats
        // (w, x, y, z) per keyframe so sampleInto can slerp the short arc
        // instead of euler-lerping (which can take the long way around the
        // rotation sphere when keyframes cross the 180° wrap).
        final float[][] quatValues;

        // Per-segment opt-in: slerp is only applied when every axis delta
        // between adjacent keyframes is ≤ 180°. Beyond that the user's
        // intent is ambiguous (a 0→360 spin reads as "stay at 0" in quat
        // space because both endpoints are the same orientation; a 350→10
        // pair could mean "20° short" OR "340° long"). Falling back to
        // euler-lerp on long-arc segments preserves v0.1 behavior for
        // sloppy 2-keyframe full-revolutions and keeps slerp's benefit
        // confined to the unambiguous short-arc case.
        final boolean[] useSlerpSegment;

        // Scratch buffers for slerp output and for unpacking the
        // resampled quat back to euler. Per-track instead of per-call so
        // sampleInto stays alloc-free in the hot path.
        private final float[] slerpScratch;

        // Pre-baked LUT covering [times[0], times[n-1]]. Populated when
        // Aero_AnimationLUTConfig.ENABLED is true and the channel has at
        // least 2 keyframes spanning a positive duration. When set, runtime
        // sampling collapses to a 1 mul + 1 index + 3 lerps — see
        // sampleLut. When null the canonical sampleRaw path runs.
        private float[][] lut;
        private float lutTimeMin;
        private float lutTimeRange;

        ChannelTrack(String clipName, String boneName, String channelKind,
                     float[] times, float[][] values, Aero_Easing[] easings) {
            String ctx = "clip '" + clipName + "' bone '" + boneName + "' " + channelKind;
            if (times == null || values == null || easings == null) {
                throw new IllegalArgumentException(ctx + ": channel arrays must not be null"
                    + " (times=" + (times == null ? "null" : "ok")
                    + ", values=" + (values == null ? "null" : "ok")
                    + ", easings=" + (easings == null ? "null" : "ok") + ")");
            }
            if (times.length != values.length || times.length != easings.length) {
                throw new IllegalArgumentException(ctx + ": channel array lengths must match"
                    + " (times=" + times.length
                    + ", values=" + values.length
                    + ", easings=" + easings.length + ")");
            }
            this.times = new float[times.length];
            this.values = new float[values.length][];
            this.easings = new Aero_Easing[easings.length];
            for (int i = 0; i < times.length; i++) {
                float time = times[i];
                if (Float.isNaN(time) || Float.isInfinite(time)) {
                    throw new IllegalArgumentException(ctx + ": keyframe[" + i
                        + "] time must be finite, got " + time);
                }
                if (i > 0 && time < times[i - 1]) {
                    throw new IllegalArgumentException(ctx + ": keyframe times must be sorted ascending"
                        + " (t[" + (i - 1) + "]=" + times[i - 1]
                        + " > t[" + i + "]=" + time + ")");
                }
                float[] value = values[i];
                if (value == null || value.length < 3) {
                    throw new IllegalArgumentException(ctx + ": keyframe[" + i
                        + "] value must have 3 components, got "
                        + (value == null ? "null" : ("length=" + value.length)));
                }
                Aero_Easing easing = easings[i];
                if (easing == null) {
                    throw new IllegalArgumentException(ctx + ": keyframe[" + i
                        + "] easing must not be null");
                }
                this.times[i] = time;
                this.values[i] = new float[]{value[0], value[1], value[2]};
                this.easings[i] = easing;
            }

            if ("rotation".equals(channelKind) && times.length > 0) {
                this.quatValues = new float[times.length][4];
                for (int i = 0; i < times.length; i++) {
                    float[] v = this.values[i];
                    Aero_Quaternion.fromEulerDegrees(v[0], v[1], v[2], this.quatValues[i]);
                }
                this.slerpScratch = new float[4];
                if (times.length > 1) {
                    this.useSlerpSegment = new boolean[times.length - 1];
                    for (int i = 0; i < times.length - 1; i++) {
                        float[] a = this.values[i], b = this.values[i + 1];
                        float dx = Math.abs(b[0] - a[0]);
                        float dy = Math.abs(b[1] - a[1]);
                        float dz = Math.abs(b[2] - a[2]);
                        // Strict less-than: at exactly 180° the two quats are
                        // antipodal and slerp's direction flips on FP rounding.
                        // Falling back to euler at the boundary avoids that
                        // instability and preserves the v0.1 visual.
                        this.useSlerpSegment[i] = dx < 180f && dy < 180f && dz < 180f;
                    }
                } else {
                    this.useSlerpSegment = null;
                }
            } else {
                this.quatValues = null;
                this.slerpScratch = null;
                this.useSlerpSegment = null;
            }

            // LUT bake (opt-in). Calls sampleRaw at evenly-spaced times
            // across [times[0], times[n-1]] and snapshots the final post-
            // easing post-slerp output so runtime sampling becomes O(1).
            if (Aero_AnimationLUTConfig.ENABLED && times.length >= 2) {
                bakeLut(Aero_AnimationLUTConfig.SAMPLES);
            }
        }

        private void bakeLut(int samples) {
            int n = times.length;
            float t0 = times[0];
            float t1 = times[n - 1];
            float range = t1 - t0;
            if (range <= 0f) return;          // degenerate — keep lut null
            float[][] table = new float[samples][3];
            float[] scratch = new float[3];
            int last = samples - 1;
            for (int i = 0; i < samples; i++) {
                float t = t0 + (range * i) / last;
                sampleRaw(t, scratch, null, -1);
                table[i][0] = scratch[0];
                table[i][1] = scratch[1];
                table[i][2] = scratch[2];
            }
            this.lut = table;
            this.lutTimeMin = t0;
            this.lutTimeRange = range;
        }

        boolean sampleInto(float time, float[] out) {
            return sampleInto(time, out, null, -1);
        }

        boolean sampleInto(float time, float[] out, int[] cursor, int cursorIndex) {
            if (lut != null) return sampleLut(time, out);
            return sampleRaw(time, out, cursor, cursorIndex);
        }

        private boolean sampleLut(float time, float[] out) {
            int last = lut.length - 1;
            if (lutTimeRange <= 0f || time <= lutTimeMin) {
                float[] a = lut[0];
                out[0] = a[0]; out[1] = a[1]; out[2] = a[2];
                return true;
            }
            if (time >= lutTimeMin + lutTimeRange) {
                float[] a = lut[last];
                out[0] = a[0]; out[1] = a[1]; out[2] = a[2];
                return true;
            }
            float idx = ((time - lutTimeMin) / lutTimeRange) * last;
            int lo = (int) idx;
            if (lo >= last) lo = last - 1;
            int hi = lo + 1;
            float blend = idx - lo;
            float[] a = lut[lo];
            float[] b = lut[hi];
            out[0] = a[0] + (b[0] - a[0]) * blend;
            out[1] = a[1] + (b[1] - a[1]) * blend;
            out[2] = a[2] + (b[2] - a[2]) * blend;
            return true;
        }

        private boolean sampleRaw(float time, float[] out, int[] cursor, int cursorIndex) {
            int n = times.length;
            if (n == 0) return false;
            if (n == 1) { copy3(values[0], out); return true; }
            if (time <= times[0]) { copy3(values[0], out); return true; }
            if (time >= times[n - 1]) { copy3(values[n - 1], out); return true; }

            int lo = findSegment(time, cursor, cursorIndex, n);
            int hi = lo + 1;

            Aero_Easing easing = easings[hi];
            if (easing == Aero_Easing.STEP) {
                copy3(values[lo], out);
                return true;
            }

            float t0 = times[lo];
            float t1 = times[hi];
            float alpha = (t1 > t0) ? (time - t0) / (t1 - t0) : 0f;
            float[] a = values[lo];
            float[] b = values[hi];

            // Rotation channels slerp the short arc when adjacent keyframes
            // are ≤ 180° apart per axis. Long-arc segments (e.g. a 0→360 fan
            // spin) fall through to euler-lerp because slerp would collapse
            // them to "no rotation" — the two euler keyframes encode the
            // same quat-space orientation. CATMULLROM on a slerp segment is
            // demoted to LINEAR-eased slerp (no spherical Catmull-Rom —
            // squad adds complexity for marginal benefit on rotation curves
            // where slerp is already smooth). Documented in DOC.md.
            if (quatValues != null && useSlerpSegment != null && useSlerpSegment[lo]) {
                float eased = easing == Aero_Easing.LINEAR || easing == Aero_Easing.CATMULLROM
                    ? alpha
                    : easing.apply(alpha);
                Aero_Quaternion.slerp(quatValues[lo], quatValues[hi], eased, slerpScratch);
                Aero_Quaternion.toEulerDegrees(slerpScratch, out);
                return true;
            }

            if (easing == Aero_Easing.CATMULLROM) {
                float[] p0 = lo > 0 ? values[lo - 1] : a;
                float[] p3 = hi < n - 1 ? values[hi + 1] : b;
                float t2 = alpha * alpha;
                float t3 = t2 * alpha;
                out[0] = cr(p0[0], a[0], b[0], p3[0], alpha, t2, t3);
                out[1] = cr(p0[1], a[1], b[1], p3[1], alpha, t2, t3);
                out[2] = cr(p0[2], a[2], b[2], p3[2], alpha, t2, t3);
                return true;
            }

            float eased = easing == Aero_Easing.LINEAR ? alpha : easing.apply(alpha);
            out[0] = a[0] + (b[0] - a[0]) * eased;
            out[1] = a[1] + (b[1] - a[1]) * eased;
            out[2] = a[2] + (b[2] - a[2]) * eased;
            return true;
        }

        private int findSegment(float time, int[] cursor, int cursorIndex, int n) {
            if (cursor != null && cursorIndex >= 0 && cursorIndex < cursor.length) {
                int lo = cursor[cursorIndex];
                if (lo >= 0 && lo < n - 1) {
                    if (time >= times[lo] && time < times[lo + 1]) return lo;
                    if (time >= times[lo + 1]) {
                        while (lo < n - 2 && time >= times[lo + 1]) lo++;
                        if (time >= times[lo] && time < times[lo + 1]) {
                            cursor[cursorIndex] = lo;
                            return lo;
                        }
                    }
                }
            }

            int lo = 0;
            int hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= time) lo = mid; else hi = mid;
            }
            if (cursor != null && cursorIndex >= 0 && cursorIndex < cursor.length) {
                cursor[cursorIndex] = lo;
            }
            return lo;
        }
    }

    static final class KeyframeEvent {
        final float time;
        final String channel;
        final String data;
        final String locator;

        KeyframeEvent(float time, String channel, String data, String locator) {
            this.time = time;
            this.channel = channel;
            this.data = data;
            this.locator = locator;
        }
    }

    public static final class Builder {
        private final String name;
        private Aero_AnimationLoop loop = Aero_AnimationLoop.PLAY_ONCE;
        private float length = 1f;
        private final List bones = new ArrayList();
        private final Map bonesByName = new HashMap();
        private final List events = new ArrayList();

        private Builder(String name) {
            this.name = name;
        }

        public Builder loop(Aero_AnimationLoop loop) {
            if (loop == null) throw new IllegalArgumentException("loop must not be null");
            this.loop = loop;
            return this;
        }

        public Builder length(float length) {
            this.length = length;
            return this;
        }

        public BoneBuilder bone(String name) {
            if (name == null || name.length() == 0) {
                throw new IllegalArgumentException("clip '" + this.name
                    + "': bone name must not be empty");
            }
            BoneBuilder bone = (BoneBuilder) bonesByName.get(name);
            if (bone == null) {
                bone = new BoneBuilder(this, name);
                bonesByName.put(name, bone);
                bones.add(bone);
            }
            return bone;
        }

        public Builder event(float time, String channel, String data, String locator) {
            if (Float.isNaN(time) || Float.isInfinite(time)) {
                throw new IllegalArgumentException("clip '" + name
                    + "': event time must be finite, got " + time);
            }
            if (channel == null || channel.length() == 0) {
                throw new IllegalArgumentException("clip '" + name
                    + "': event channel must not be empty");
            }
            if (data == null || data.length() == 0) {
                throw new IllegalArgumentException("clip '" + name + "' channel '" + channel
                    + "' @t=" + time + ": event name must not be empty");
            }
            events.add(new KeyframeEvent(time, channel, data, locator));
            return this;
        }

        public Aero_AnimationClip build() {
            return new Aero_AnimationClip(this);
        }
    }

    public static final class BoneBuilder {
        private final Builder owner;
        private final String name;
        private ChannelTrack rotation;
        private ChannelTrack position;
        private ChannelTrack scale;
        private ChannelTrack uvOffset;
        private ChannelTrack uvScale;

        private BoneBuilder(Builder owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        public BoneBuilder rotation(float[] times, float[][] values, Aero_Easing[] easings) {
            rotation = new ChannelTrack(owner.name, name, "rotation", times, values, easings);
            return this;
        }

        public BoneBuilder position(float[] times, float[][] values, Aero_Easing[] easings) {
            position = new ChannelTrack(owner.name, name, "position", times, values, easings);
            return this;
        }

        public BoneBuilder scale(float[] times, float[][] values, Aero_Easing[] easings) {
            scale = new ChannelTrack(owner.name, name, "scale", times, values, easings);
            return this;
        }

        /** Animates the per-bone UV offset (vec3, only x=u and y=v consumed). */
        public BoneBuilder uvOffset(float[] times, float[][] values, Aero_Easing[] easings) {
            uvOffset = new ChannelTrack(owner.name, name, "uv_offset", times, values, easings);
            return this;
        }

        /** Animates the per-bone UV scale (vec3, only x=u and y=v consumed). */
        public BoneBuilder uvScale(float[] times, float[][] values, Aero_Easing[] easings) {
            uvScale = new ChannelTrack(owner.name, name, "uv_scale", times, values, easings);
            return this;
        }

        public Builder endBone() {
            return owner;
        }

        private BoneTrack build() {
            return new BoneTrack(name, rotation, position, scale, uvOffset, uvScale);
        }
    }
}
