package aero.modellib;

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
    final float[][]   rotTimes;     // rotTimes[bi]     = float[] of timestamps (seconds)
    final float[][][] rotValues;    // rotValues[bi][ki] = float[3] {rx, ry, rz}
    final int[][]     rotInterps;   // rotInterps[bi][ki] = INTERP_* constant
    final float[][]   posTimes;
    final float[][][] posValues;    // posValues[bi][ki] = float[3] {px, py, pz}
    final int[][]     posInterps;
    final float[][]   sclTimes;
    final float[][][] sclValues;    // sclValues[bi][ki] = float[3] {sx, sy, sz}
    final int[][]     sclInterps;

    Aero_AnimationClip(String name, boolean loop, float length,
                  String[] boneNames,
                  float[][] rotTimes, float[][][] rotValues, int[][] rotInterps,
                  float[][] posTimes, float[][][] posValues, int[][] posInterps,
                  float[][] sclTimes, float[][][] sclValues, int[][] sclInterps) {
        this.name       = name;
        this.loop       = loop;
        this.length     = length;
        this.boneNames  = boneNames;
        this.rotTimes   = rotTimes;
        this.rotValues  = rotValues;
        this.rotInterps = rotInterps;
        this.posTimes   = posTimes;
        this.posValues  = posValues;
        this.posInterps = posInterps;
        this.sclTimes   = sclTimes;
        this.sclValues  = sclValues;
        this.sclInterps = sclInterps;
    }

    /** Returns the bone index by name, or -1 if not found. */
    public int indexOfBone(String name) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) return i;
        }
        return -1;
    }

    /**
     * Samples the bone rotation at a given time.
     * Returns float[3] {rx, ry, rz} in degrees, or null if no keyframes.
     */
    public float[] sampleRot(int boneIdx, float time) {
        float[] times   = rotTimes[boneIdx];
        float[][] vals  = rotValues[boneIdx];
        int[] interps   = rotInterps[boneIdx];
        if (times == null || times.length == 0) return null;
        return sample(times, vals, interps, time);
    }

    /**
     * Samples the bone position at a given time.
     * Returns float[3] {px, py, pz} in pixels, or null if no keyframes.
     */
    public float[] samplePos(int boneIdx, float time) {
        float[] times   = posTimes[boneIdx];
        float[][] vals  = posValues[boneIdx];
        int[] interps   = posInterps[boneIdx];
        if (times == null || times.length == 0) return null;
        return sample(times, vals, interps, time);
    }

    /**
     * Samples the bone scale at a given time.
     * Returns float[3] {sx, sy, sz}, or null if no keyframes.
     */
    public float[] sampleScl(int boneIdx, float time) {
        float[] times   = sclTimes[boneIdx];
        float[][] vals  = sclValues[boneIdx];
        int[] interps   = sclInterps[boneIdx];
        if (times == null || times.length == 0) return null;
        return sample(times, vals, interps, time);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Samples keyframes with per-keyframe interpolation mode.
     * The interp mode on each keyframe defines how to ARRIVE at that keyframe
     * (i.e., interps[hi] is used for the interval lo→hi).
     */
    private static float[] sample(float[] times, float[][] vals, int[] interps, float time) {
        int n = times.length;
        if (n == 1) return copy3(vals[0]);
        if (time <= times[0]) return copy3(vals[0]);
        if (time >= times[n - 1]) return copy3(vals[n - 1]);

        // Binary search for the interval
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= time) lo = mid; else hi = mid;
        }

        int mode = interps != null && hi < interps.length ? interps[hi] : INTERP_LINEAR;

        // Step: snap to previous keyframe value
        if (mode == INTERP_STEP) {
            return copy3(vals[lo]);
        }

        float t0 = times[lo], t1 = times[hi];
        float alpha = (t1 > t0) ? (time - t0) / (t1 - t0) : 0f;
        float[] a = vals[lo];
        float[] b = vals[hi];

        if (mode == INTERP_CATMULLROM) {
            // Catmull-Rom spline: use 4 control points
            float[] p0 = lo > 0 ? vals[lo - 1] : a;
            float[] p3 = hi < n - 1 ? vals[hi + 1] : b;
            return catmullRom(p0, a, b, p3, alpha);
        }

        // Default: linear
        return new float[]{
            a[0] + (b[0] - a[0]) * alpha,
            a[1] + (b[1] - a[1]) * alpha,
            a[2] + (b[2] - a[2]) * alpha
        };
    }

    /** Catmull-Rom spline interpolation between p1 and p2 */
    private static float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return new float[]{
            cr(p0[0], p1[0], p2[0], p3[0], t, t2, t3),
            cr(p0[1], p1[1], p2[1], p3[1], t, t2, t3),
            cr(p0[2], p1[2], p2[2], p3[2], t, t2, t3)
        };
    }

    private static float cr(float p0, float p1, float p2, float p3, float t, float t2, float t3) {
        return 0.5f * ((2f * p1) +
            (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    private static float[] copy3(float[] src) {
        return new float[]{src[0], src[1], src[2]};
    }
}
