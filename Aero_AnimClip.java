package aero.modellib;

/**
 * Immutable data for an animation clip.
 *
 * Stores rotation and position keyframes for each bone (OBJ named group),
 * in parallel arrays sorted by ascending time.
 *
 * Units:
 *   - Time: seconds (float)
 *   - Rotation: Euler degrees [X, Y, Z] — applied in Z→Y→X order (Bedrock/GeckoLib compatible)
 *   - Position: Blockbench pixels (divide by 16 for block units in the renderer)
 */
public class Aero_AnimClip {

    public final String  name;
    public final boolean loop;
    public final float   length;    // duration in seconds

    // Parallel arrays indexed by bone index (0-based, order of addition)
    final String[]    boneNames;
    final float[][]   rotTimes;     // rotTimes[bi]    = float[] of timestamps (seconds)
    final float[][][] rotValues;    // rotValues[bi][ki] = float[3] {rx, ry, rz}
    final float[][]   posTimes;
    final float[][][] posValues;    // posValues[bi][ki] = float[3] {px, py, pz}

    Aero_AnimClip(String name, boolean loop, float length,
                  String[] boneNames,
                  float[][] rotTimes, float[][][] rotValues,
                  float[][] posTimes, float[][][] posValues) {
        this.name      = name;
        this.loop      = loop;
        this.length    = length;
        this.boneNames = boneNames;
        this.rotTimes  = rotTimes;
        this.rotValues = rotValues;
        this.posTimes  = posTimes;
        this.posValues = posValues;
    }

    /** Returns the bone index by name, or -1 if not found. */
    public int indexOfBone(String name) {
        for (int i = 0; i < boneNames.length; i++) {
            if (boneNames[i].equals(name)) return i;
        }
        return -1;
    }

    /**
     * Samples the bone rotation at a given time, with LINEAR interpolation.
     * Returns float[3] {rx, ry, rz} in degrees, or null if the bone has no rotation keyframes.
     */
    public float[] sampleRot(int boneIdx, float time) {
        float[] times = rotTimes[boneIdx];
        float[][]  vals  = rotValues[boneIdx];
        if (times == null || times.length == 0) return null;
        return sample(times, vals, time);
    }

    /**
     * Samples the bone position at a given time, with LINEAR interpolation.
     * Returns float[3] {px, py, pz} in pixels, or null if the bone has no position keyframes.
     */
    public float[] samplePos(int boneIdx, float time) {
        float[] times = posTimes[boneIdx];
        float[][] vals  = posValues[boneIdx];
        if (times == null || times.length == 0) return null;
        return sample(times, vals, time);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /** LINEAR interpolation between keyframes. Clamps outside bounds. */
    private static float[] sample(float[] times, float[][] vals, float time) {
        int n = times.length;
        if (n == 1) {
            return copy3(vals[0]);
        }
        // Clamp before the first keyframe
        if (time <= times[0]) return copy3(vals[0]);
        // Clamp after the last keyframe
        if (time >= times[n - 1]) return copy3(vals[n - 1]);

        // Binary search for the interval
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= time) lo = mid; else hi = mid;
        }

        float t0 = times[lo], t1 = times[hi];
        float alpha = (t1 > t0) ? (time - t0) / (t1 - t0) : 0f;

        float[] a = vals[lo];
        float[] b = vals[hi];
        return new float[]{
            a[0] + (b[0] - a[0]) * alpha,
            a[1] + (b[1] - a[1]) * alpha,
            a[2] + (b[2] - a[2]) * alpha
        };
    }

    private static float[] copy3(float[] src) {
        return new float[]{src[0], src[1], src[2]};
    }
}
