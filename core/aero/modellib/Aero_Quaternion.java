package aero.modellib;

/**
 * Allocation-free quaternion utilities for short-arc rotation interpolation.
 *
 * <p>The lib's animation channels store rotation as Euler degrees (X, Y, Z),
 * which interpolated linearly take the long way around the rotation sphere
 * when keyframes cross the 180° wrap (e.g. 350° → 10° goes 0→180→0 instead of
 * 0→0). Pre-baking quaternions at clip-build time and slerping at sample
 * time gives the visually-correct short arc.
 *
 * <p>Convention: matches the renderer's GL11 call order
 * {@code glRotatef(rotZ, Z); glRotatef(rotY, Y); glRotatef(rotX, X)} —
 * which post-multiplies into a final matrix {@code M = R_z * R_y * R_x},
 * applying X first then Y then Z to body-frame vectors. Quaternion
 * conversion uses the same convention so round-tripping through quat
 * leaves the rendered orientation identical.
 *
 * <p>All public methods write into a caller-provided output array — no
 * allocation in the hot path.
 */
public final class Aero_Quaternion {

    private Aero_Quaternion() {}

    /**
     * Converts Euler angles (degrees, XYZ extrinsic / ZYX intrinsic) to a
     * unit quaternion {@code (w, x, y, z)}. Output is normalized.
     */
    public static void fromEulerDegrees(float rxDeg, float ryDeg, float rzDeg, float[] out4) {
        float rx = (float) Math.toRadians(rxDeg) * 0.5f;
        float ry = (float) Math.toRadians(ryDeg) * 0.5f;
        float rz = (float) Math.toRadians(rzDeg) * 0.5f;

        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);

        // Composition order R_z * R_y * R_x (apply X to body, then Y, then Z)
        // matches the renderer's glRotatef(rotZ); glRotatef(rotY); glRotatef(rotX)
        // sequence.
        out4[0] = cx * cy * cz + sx * sy * sz; // w
        out4[1] = sx * cy * cz - cx * sy * sz; // x
        out4[2] = cx * sy * cz + sx * cy * sz; // y
        out4[3] = cx * cy * sz - sx * sy * cz; // z
    }

    /**
     * Extracts Euler angles (degrees, ZYX intrinsic) from a unit quaternion.
     * Output ordering matches {@link #fromEulerDegrees} so a round-trip is
     * the identity (modulo floating-point error).
     */
    public static void toEulerDegrees(float[] q, float[] out3) {
        float w = q[0], x = q[1], y = q[2], z = q[3];

        // Pitch (X). Clamp before asin to avoid NaN at gimbal-singular poses.
        float sinp = 2f * (w * x + y * z);
        float cosp = 1f - 2f * (x * x + y * y);
        float rx = (float) Math.atan2(sinp, cosp);

        // Yaw (Y).
        float siny = 2f * (w * y - z * x);
        if (siny > 1f) siny = 1f;
        if (siny < -1f) siny = -1f;
        float ry = (float) Math.asin(siny);

        // Roll (Z).
        float sinr = 2f * (w * z + x * y);
        float cosr = 1f - 2f * (y * y + z * z);
        float rz = (float) Math.atan2(sinr, cosr);

        out3[0] = (float) Math.toDegrees(rx);
        out3[1] = (float) Math.toDegrees(ry);
        out3[2] = (float) Math.toDegrees(rz);
    }

    /**
     * Builds a unit quaternion from an axis (does not need to be normalized
     * — this method normalizes internally) and an angle in radians. Useful
     * for IK solvers and procedural rotation that work in axis-angle form.
     */
    public static void fromAxisAngle(float axisX, float axisY, float axisZ,
                                     float angleRad, float[] out4) {
        float len2 = axisX * axisX + axisY * axisY + axisZ * axisZ;
        if (len2 < 1e-12f) {
            out4[0] = 1f; out4[1] = 0f; out4[2] = 0f; out4[3] = 0f;
            return;
        }
        float invLen = 1f / (float) Math.sqrt(len2);
        float ax = axisX * invLen, ay = axisY * invLen, az = axisZ * invLen;
        float half = angleRad * 0.5f;
        float s = (float) Math.sin(half);
        out4[0] = (float) Math.cos(half);
        out4[1] = ax * s;
        out4[2] = ay * s;
        out4[3] = az * s;
    }

    /**
     * Hamilton product {@code out = a * b}. Composition order: applying
     * {@code out} to a vector is the same as applying b first, then a.
     * Output is safe even when {@code out4} aliases {@code a} or {@code b}.
     */
    public static void multiply(float[] a, float[] b, float[] out4) {
        float aw = a[0], ax = a[1], ay = a[2], az = a[3];
        float bw = b[0], bx = b[1], by = b[2], bz = b[3];
        out4[0] = aw * bw - ax * bx - ay * by - az * bz;
        out4[1] = aw * bx + ax * bw + ay * bz - az * by;
        out4[2] = aw * by - ax * bz + ay * bw + az * bx;
        out4[3] = aw * bz + ax * by - ay * bx + az * bw;
    }

    /**
     * Rotates a 3D vector by a unit quaternion. {@code out3} is safe even
     * when it aliases {@code v3}.
     */
    public static void rotateVec(float[] q, float[] v3, float[] out3) {
        float w = q[0], x = q[1], y = q[2], z = q[3];
        float vx = v3[0], vy = v3[1], vz = v3[2];
        // out = q * (0, v) * conj(q), expanded.
        float tx = 2f * (y * vz - z * vy);
        float ty = 2f * (z * vx - x * vz);
        float tz = 2f * (x * vy - y * vx);
        out3[0] = vx + w * tx + (y * tz - z * ty);
        out3[1] = vy + w * ty + (z * tx - x * tz);
        out3[2] = vz + w * tz + (x * ty - y * tx);
    }

    /**
     * Spherical linear interpolation between two unit quaternions. Always
     * takes the short arc (flips one operand if the dot product is negative).
     * Falls back to nlerp when the two quats are nearly colinear, where
     * slerp's denominator goes to zero.
     */
    public static void slerp(float[] a, float[] b, float t, float[] out4) {
        float aw = a[0], ax = a[1], ay = a[2], az = a[3];
        float bw = b[0], bx = b[1], by = b[2], bz = b[3];

        float dot = aw * bw + ax * bx + ay * by + az * bz;
        if (dot < 0f) {
            // Flip B so we interpolate along the short arc.
            bw = -bw; bx = -bx; by = -by; bz = -bz;
            dot = -dot;
        }

        float scaleA, scaleB;
        // Near-colinear: slerp's sin(theta) is too small to be stable. nlerp
        // gives a near-identical visual result and avoids the singularity.
        if (dot > 0.9995f) {
            scaleA = 1f - t;
            scaleB = t;
        } else {
            float theta = (float) Math.acos(dot);
            float sinTheta = (float) Math.sin(theta);
            float invSin = 1f / sinTheta;
            scaleA = (float) Math.sin((1f - t) * theta) * invSin;
            scaleB = (float) Math.sin(t * theta) * invSin;
        }

        float ow = scaleA * aw + scaleB * bw;
        float ox = scaleA * ax + scaleB * bx;
        float oy = scaleA * ay + scaleB * by;
        float oz = scaleA * az + scaleB * bz;

        // Renormalize against accumulated FP drift.
        float invLen = 1f / (float) Math.sqrt(ow * ow + ox * ox + oy * oy + oz * oz);
        out4[0] = ow * invLen;
        out4[1] = ox * invLen;
        out4[2] = oy * invLen;
        out4[3] = oz * invLen;
    }
}
