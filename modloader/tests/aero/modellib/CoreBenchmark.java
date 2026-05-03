package aero.modellib;

import java.util.Arrays;
import java.util.HashMap;

import aero.modellib.animation.Aero_AnimationClip;
import aero.modellib.animation.Aero_AnimationLoop;
import aero.modellib.animation.Aero_Easing;
import aero.modellib.model.Aero_JsonModel;
import aero.modellib.model.Aero_MeshModel;
import aero.modellib.render.Aero_EntityModelTransform;
import aero.modellib.render.Aero_RenderDistanceCulling;
import aero.modellib.render.Aero_RenderLod;
import aero.modellib.skeletal.Aero_BoneFK;
import aero.modellib.skeletal.Aero_BoneRenderPose;
import aero.modellib.skeletal.Aero_CCDSolver;

/**
 * Small deterministic microbenchmark for core hot-path data structures.
 *
 * This is not a substitute for in-game profiling; it exists to catch obvious
 * regressions in pre-baked JSON geometry, smooth-light metadata and animation
 * bone lookup/sample costs.
 */
public final class CoreBenchmark {

    private static volatile float sink;

    public static void main(String[] args) {
        Aero_JsonModel json = buildJsonModel(2048);
        Aero_MeshModel mesh = buildMeshModel(32768);
        Aero_AnimationClip clip = buildClip(128, 16);
        Aero_EntityModelTransform entityTransform = Aero_EntityModelTransform.of(0.1f, 0.2f, -0.3f, 1.25f, 12.5f);

        mesh.getStaticSmoothLightData();

        bench("json.quad.walk", 200, 2000, new Bench() {
            public float run() { return walkJsonQuads(json); }
        });
        bench("mesh.smooth.metadata.walk", 50, 500, new Bench() {
            public float run() { return walkSmoothMetadata(mesh); }
        });
        bench("anim.index.map+sample", 200, 2000, new Bench() {
            public float run() { return sampleWithMapLookup(clip); }
        });
        bench("anim.sample.cursor", 200, 2000, new Bench() {
            public float run() { return sampleWithCursor(clip); }
        });
        bench("anim.index.linear+sample.reference", 20, 200, new Bench() {
            public float run() { return sampleWithLinearLookup(clip); }
        });
        bench("ik.ccd.solve", 20, 200, new Bench() {
            public float run() { return solveCcdChain(); }
        });
        bench("entity.transform.yaw", 500, 50000, new Bench() {
            public float run() { return resolveEntityYaw(entityTransform); }
        });
        bench("renderdistance.cull", 500, 50000, new Bench() {
            public float run() { return resolveRenderDistanceCull(); }
        });
        bench("renderdistance.lod", 500, 50000, new Bench() {
            public float run() { return resolveRenderDistanceLod(); }
        });

        System.out.println("sink=" + sink);
    }

    private static void bench(String name, int warmup, int iterations, Bench bench) {
        for (int i = 0; i < warmup; i++) sink += bench.run();
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) sink += bench.run();
        long nanos = System.nanoTime() - start;
        double nsPerOp = (double) nanos / (double) iterations;
        System.out.println(name + ": " + format(nsPerOp) + " ns/op");
    }

    private static float walkJsonQuads(Aero_JsonModel model) {
        float sum = 0f;
        for (int f = 0; f < Aero_JsonModel.FACE_COUNT; f++) {
            float[][] quads = model.quadsByFace[f];
            for (int i = 0; i < quads.length; i++) {
                float[] q = quads[i];
                sum += q[0] + q[5] + q[10] + q[15];
                sum += q[3] + q[8] + q[13] + q[18];
            }
        }
        return sum;
    }

    private static float walkSmoothMetadata(Aero_MeshModel model) {
        Aero_MeshModel.SmoothLightData data = model.getStaticSmoothLightData();
        float sum = data.minX + data.maxX + data.minZ + data.maxZ;
        for (int g = 0; g < 4; g++) {
            float[] cx = data.centroidX[g];
            float[] cz = data.centroidZ[g];
            for (int i = 0; i < cx.length; i++) {
                sum += cx[i] + cz[i];
            }
        }
        return sum;
    }

    private static float sampleWithMapLookup(Aero_AnimationClip clip) {
        float[] out = new float[3];
        float sum = 0f;
        for (int i = 0; i < clip.boneNames.length; i++) {
            int idx = clip.indexOfBone("bone_" + i);
            if (clip.sampleRotInto(idx, 0.375f, out)) sum += out[0] + out[1] + out[2];
        }
        return sum;
    }

    private static float sampleWithLinearLookup(Aero_AnimationClip clip) {
        float[] out = new float[3];
        float sum = 0f;
        for (int i = 0; i < clip.boneNames.length; i++) {
            int idx = linearIndexOf(clip.boneNames, "bone_" + i);
            if (clip.sampleRotInto(idx, 0.375f, out)) sum += out[0] + out[1] + out[2];
        }
        return sum;
    }

    private static float sampleWithCursor(Aero_AnimationClip clip) {
        float[] out = new float[3];
        int[] cursor = new int[clip.boneNames.length];
        Arrays.fill(cursor, -1);
        float sum = 0f;
        for (int step = 0; step < 64; step++) {
            float time = step * (1f / 63f);
            for (int i = 0; i < clip.boneNames.length; i++) {
                if (clip.sampleRotInto(i, time, out, cursor)) {
                    sum += out[0] + out[1] + out[2];
                }
            }
        }
        return sum;
    }

    private static int linearIndexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) return i;
        }
        return -1;
    }

    private static float resolveEntityYaw(Aero_EntityModelTransform transform) {
        float sum = transform.offsetX + transform.offsetY + transform.offsetZ + transform.scale;
        for (int i = 0; i < 256; i++) {
            sum += transform.modelYaw(i * 1.40625f);
        }
        return sum;
    }

    private static float resolveRenderDistanceCull() {
        float sum = 0f;
        for (int i = 0; i < 256; i++) {
            if (Aero_RenderDistanceCulling.shouldRenderRelative(i, i & 15, i >>> 1, i & 3, 4d)) {
                sum += i;
            }
        }
        return sum;
    }

    private static float resolveRenderDistanceLod() {
        float sum = 0f;
        for (int i = 0; i < 256; i++) {
            Aero_RenderLod lod = Aero_RenderDistanceCulling.lodRelative(
                i, i & 15, i >>> 1, i & 3, 4d, 32d);
            if (lod.shouldAnimate()) sum += i;
            else if (lod.isStaticOnly()) sum += i * 0.5f;
        }
        return sum;
    }

    private static float solveCcdChain() {
        int[] idx = {0, 1, 2, 3};
        float[][] pivots = {
            {0f, 0f, 0f},
            {0f, 0f, 1f},
            {0f, 0f, 2f},
            {0f, 0f, 3f}
        };
        Aero_BoneRenderPose[] pool = new Aero_BoneRenderPose[idx.length];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new Aero_BoneRenderPose();
            pool[i].reset();
        }
        float[] target = {1.5f, 0.25f, 1.5f};
        int iters = Aero_CCDSolver.solve(idx, pivots, pool, target, 0.01f);
        float[] effector = new float[3];
        Aero_BoneFK.computePivotInto(idx, pivots, pool, effector);
        return iters + effector[0] + effector[1] + effector[2];
    }

    private static Aero_JsonModel buildJsonModel(int cubes) {
        float[][] elements = new float[cubes][30];
        for (int i = 0; i < cubes; i++) {
            float[] p = elements[i];
            float x = (i & 31) * 2f;
            float y = ((i >>> 5) & 31) * 2f;
            float z = ((i >>> 10) & 31) * 2f;
            p[0] = x; p[1] = y; p[2] = z;
            p[3] = x + 1f; p[4] = y + 1f; p[5] = z + 1f;
            for (int f = 0; f < Aero_JsonModel.FACE_COUNT; f++) {
                int base = 6 + f * 4;
                p[base] = 0f;
                p[base + 1] = 0f;
                p[base + 2] = 16f;
                p[base + 3] = 16f;
            }
        }
        return new Aero_JsonModel("bench-json", elements, 16f, 16f);
    }

    private static Aero_MeshModel buildMeshModel(int triangles) {
        float[][][] groups = new float[4][][];
        for (int g = 0; g < 4; g++) groups[g] = new float[triangles / 4][];
        for (int g = 0; g < 4; g++) {
            for (int i = 0; i < groups[g].length; i++) {
                float x = (i & 255);
                float z = (i >>> 8);
                groups[g][i] = new float[]{
                    x, 0f, z, 0f, 0f,
                    x + 1f, 0f, z, 0f, 0f,
                    x, 0f, z + 1f, 0f, 0f
                };
            }
        }
        return new Aero_MeshModel("bench-mesh", groups, 16f, new HashMap());
    }

    private static Aero_AnimationClip buildClip(int bones, int keys) {
        String[] boneNames = new String[bones];
        float[][] times = new float[bones][];
        float[][][] values = new float[bones][][];
        for (int b = 0; b < bones; b++) {
            boneNames[b] = "bone_" + b;
            times[b] = new float[keys];
            values[b] = new float[keys][];
            for (int k = 0; k < keys; k++) {
                times[b][k] = k / (float) (keys - 1);
                values[b][k] = new float[]{b + k, b - k, k};
            }
        }
        Aero_AnimationClip.Builder builder = Aero_AnimationClip.builder("bench")
            .loop(Aero_AnimationLoop.LOOP)
            .length(1f);
        for (int b = 0; b < bones; b++) {
            builder.bone(boneNames[b])
                .rotation(times[b], values[b], linearEasings(times[b].length))
                .position(times[b], values[b], linearEasings(times[b].length));
        }
        return builder.build();
    }

    private static Aero_Easing[] linearEasings(int count) {
        Aero_Easing[] easings = new Aero_Easing[count];
        for (int i = 0; i < count; i++) easings[i] = Aero_Easing.LINEAR;
        return easings;
    }

    private static String format(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private interface Bench {
        float run();
    }
}
