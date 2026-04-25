package aero.modellib;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable data for an animation clip.
 *
 * Stores rotation, position and scale keyframes for each bone (OBJ named group),
 * in parallel arrays sorted by ascending time.
 *
 * Each channel also stores per-keyframe interpolation modes:
 *   0 = LINEAR (default), 1 = CATMULLROM (smooth), 2 = STEP
 *
 * Units:
 *   - Time: seconds (float)
 *   - Rotation: Euler degrees [X, Y, Z] — applied in Z→Y→X order (Bedrock/GeckoLib compatible)
 *   - Position: Blockbench pixels (divide by 16 for block units in the renderer)
 *   - Scale: multipliers [X, Y, Z] (1.0 = original size)
 *
 * Sampling API:
 *   - sampleRot/Pos/Scl(boneIdx, time)            — returns a freshly allocated float[3]
 *   - sampleRotInto/PosInto/SclInto(idx, t, out)  — writes into the supplied buffer,
 *     returns true if a sample was produced. Use this on the render hot path.
 */
public class Aero_AnimationClip {

    /** Interpolation mode constants */
    public static final int INTERP_LINEAR     = 0;
    public static final int INTERP_CATMULLROM = 1;
    public static final int INTERP_STEP       = 2;

    public final String  name;
    public final boolean loop;
    public final float   length;    // duration in seconds

    // Parallel arrays indexed by bone index (0-based, order of addition)
    final String[]    boneNames;
    private final Map boneIndexByName;
    final float[][]   rotTimes;     // rotTimes[bi]     = float[] of timestamps (seconds)
    final float[][][] rotValues;    // rotValues[bi][ki] = float[3] {rx, ry, rz}
    final int[][]     rotInterps;   // rotInterps[bi][ki] = INTERP_* constant
    final float[][]   posTimes;
    final float[][][] posValues;
    final int[][]     posInterps;
    final float[][]   sclTimes;
    final float[][][] sclValues;
    final int[][]     sclInterps;

    /** Full constructor — used by Aero_AnimationLoader. */
    Aero_AnimationClip(String name, boolean loop, float length,
                  String[] boneNames,
                  float[][] rotTimes, float[][][] rotValues, int[][] rotInterps,
                  float[][] posTimes, float[][][] posValues, int[][] posInterps,
                  float[][] sclTimes, float[][][] sclValues, int[][] sclInterps) {
        int n = boneNames.length;
        this.name       = name;
        this.loop       = loop;
        this.length     = length;
        this.boneNames  = boneNames;
        this.boneIndexByName = buildBoneIndex(boneNames);
        this.rotTimes   = rotTimes   != null ? rotTimes   : new float[n][];
        this.rotValues  = rotValues  != null ? rotValues  : new float[n][][];
        this.rotInterps = rotInterps != null ? rotInterps : new int[n][];
        this.posTimes   = posTimes   != null ? posTimes   : new float[n][];
        this.posValues  = posValues  != null ? posValues  : new float[n][][];
        this.posInterps = posInterps != null ? posInterps : new int[n][];
        this.sclTimes   = sclTimes   != null ? sclTimes   : new float[n][];
        this.sclValues  = sclValues  != null ? sclValues  : new float[n][][];
        this.sclInterps = sclInterps != null ? sclInterps : new int[n][];
    }

    /**
     * Backward-compatible constructor (rotation + position only, all linear).
     * Provided for tests and older callers; the loader uses the full form.
     */
    public Aero_AnimationClip(String name, boolean loop, float length,
                              String[] boneNames,
                              float[][] rotTimes, float[][][] rotValues,
                              float[][] posTimes, float[][][] posValues) {
        this(name, loop, length, boneNames,
             rotTimes, rotValues, null,
             posTimes, posValues, null,
             null, null, null);
    }

    /** Returns the bone index by name, or -1 if not found. */
    public int indexOfBone(String name) {
        Integer idx = (Integer) boneIndexByName.get(name);
        return idx != null ? idx.intValue() : -1;
    }

    // -----------------------------------------------------------------------
    // Allocating sample API (kept for test/back-compat callers)
    // -----------------------------------------------------------------------

    /** Samples rotation at `time`, returning float[3] {rx, ry, rz}, or null if no keyframes. */
    public float[] sampleRot(int boneIdx, float time) {
        float[] out = new float[3];
        return sampleRotInto(boneIdx, time, out) ? out : null;
    }

    /** Samples position at `time`, returning float[3] {px, py, pz}, or null if no keyframes. */
    public float[] samplePos(int boneIdx, float time) {
        float[] out = new float[3];
        return samplePosInto(boneIdx, time, out) ? out : null;
    }

    /** Samples scale at `time`, returning float[3] {sx, sy, sz}, or null if no keyframes. */
    public float[] sampleScl(int boneIdx, float time) {
        float[] out = new float[3];
        return sampleSclInto(boneIdx, time, out) ? out : null;
    }

    // -----------------------------------------------------------------------
    // Alloc-free sample API (preferred on the render hot path)
    // -----------------------------------------------------------------------

    /**
     * Samples rotation into the supplied float[3] buffer.
     * @return true if a sample was written, false if the bone has no keyframes
     *         (in which case `out` is left untouched and the caller should use defaults).
     */
    public boolean sampleRotInto(int boneIdx, float time, float[] out) {
        if (boneIdx < 0 || boneIdx >= rotTimes.length) return false;
        return sampleInto(rotTimes[boneIdx], rotValues[boneIdx], rotInterps[boneIdx], time, out);
    }

    public boolean samplePosInto(int boneIdx, float time, float[] out) {
        if (boneIdx < 0 || boneIdx >= posTimes.length) return false;
        return sampleInto(posTimes[boneIdx], posValues[boneIdx], posInterps[boneIdx], time, out);
    }

    public boolean sampleSclInto(int boneIdx, float time, float[] out) {
        if (boneIdx < 0 || boneIdx >= sclTimes.length) return false;
        return sampleInto(sclTimes[boneIdx], sclValues[boneIdx], sclInterps[boneIdx], time, out);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Samples keyframes with per-keyframe interpolation mode, writing the result
     * into `out`. The interp mode on each keyframe defines how to ARRIVE at that
     * keyframe (i.e., interps[hi] is used for the interval lo→hi).
     *
     * @return true if a sample was produced, false if `times` is null/empty.
     */
    private static boolean sampleInto(float[] times, float[][] vals, int[] interps,
                                      float time, float[] out) {
        if (times == null || times.length == 0) return false;
        int n = times.length;
        if (n == 1) { copy3(vals[0], out); return true; }
        if (time <= times[0])      { copy3(vals[0], out); return true; }
        if (time >= times[n - 1])  { copy3(vals[n - 1], out); return true; }

        // Binary search for the interval [lo, hi] s.t. times[lo] <= time < times[hi].
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= time) lo = mid; else hi = mid;
        }

        int mode = (interps != null && hi < interps.length) ? interps[hi] : INTERP_LINEAR;

        if (mode == INTERP_STEP) {
            copy3(vals[lo], out);
            return true;
        }

        float t0 = times[lo], t1 = times[hi];
        float alpha = (t1 > t0) ? (time - t0) / (t1 - t0) : 0f;
        float[] a = vals[lo];
        float[] b = vals[hi];

        if (mode == INTERP_CATMULLROM) {
            float[] p0 = lo > 0 ? vals[lo - 1] : a;
            float[] p3 = hi < n - 1 ? vals[hi + 1] : b;
            float t2 = alpha * alpha;
            float t3 = t2 * alpha;
            out[0] = cr(p0[0], a[0], b[0], p3[0], alpha, t2, t3);
            out[1] = cr(p0[1], a[1], b[1], p3[1], alpha, t2, t3);
            out[2] = cr(p0[2], a[2], b[2], p3[2], alpha, t2, t3);
            return true;
        }

        // Linear (default)
        out[0] = a[0] + (b[0] - a[0]) * alpha;
        out[1] = a[1] + (b[1] - a[1]) * alpha;
        out[2] = a[2] + (b[2] - a[2]) * alpha;
        return true;
    }

    private static float cr(float p0, float p1, float p2, float p3, float t, float t2, float t3) {
        return 0.5f * ((2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    private static void copy3(float[] src, float[] dst) {
        dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2];
    }

    private static Map buildBoneIndex(String[] boneNames) {
        Map map = new HashMap((boneNames.length * 4 / 3) + 1);
        for (int i = 0; i < boneNames.length; i++) {
            map.put(boneNames[i], Integer.valueOf(i));
        }
        return map;
    }
}
