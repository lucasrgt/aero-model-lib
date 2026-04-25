package aero.modellib;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class AnimClipTest {

	private static final float DELTA = 0.01f;

	// Shared single-bone clip: fan rotates 0→360 Y, moves 0→2 Y over 1 second
	private Aero_AnimationClip fanClip;

	@Before
	public void setUp() {
		// Arrange: single bone "fan", 2 keyframes at 0s and 1s
		String[] bones = {"fan"};
		float[][] rotTimes = { {0f, 1f} };
		float[][][] rotValues = { { {0f, 0f, 0f}, {0f, 360f, 0f} } };
		float[][] posTimes = { {0f, 1f} };
		float[][][] posValues = { { {0f, 0f, 0f}, {0f, 2f, 0f} } };
		fanClip = clip("spin", Aero_AnimationClip.LOOP_TYPE_LOOP, 1.0f, bones, rotTimes, rotValues, posTimes, posValues);
	}

	// --- Field tests ---

	@Test
	public void testClipFieldsAreCorrect() {
		// Assert: public fields match constructor args
		assertEquals("spin", fanClip.name);
		assertEquals(Aero_AnimationClip.LOOP_TYPE_LOOP, fanClip.loopType);
		assertEquals(1.0f, fanClip.length, DELTA);
	}

	// --- indexOfBone tests ---

	@Test
	public void testIndexOfBoneReturnsCorrectIndex() {
		// Act
		int idx = fanClip.indexOfBone("fan");

		// Assert
		assertEquals(0, idx);
	}

	@Test
	public void testIndexOfBoneReturnsNegativeOneForMissing() {
		// Act
		int idx = fanClip.indexOfBone("nonexistent");

		// Assert
		assertEquals(-1, idx);
	}

	// --- sampleRot tests ---

	@Test
	public void testSampleRotAtExactFirstKeyframe() {
		// Act: sample at t=0 (exact first keyframe)
		float[] rot = fanClip.sampleRot(0, 0f);

		// Assert: should return [0, 0, 0]
		assertNotNull(rot);
		assertEquals(0f, rot[0], DELTA);
		assertEquals(0f, rot[1], DELTA);
		assertEquals(0f, rot[2], DELTA);
	}

	@Test
	public void testSampleRotAtExactLastKeyframe() {
		// Act: sample at t=1 (exact last keyframe)
		float[] rot = fanClip.sampleRot(0, 1f);

		// Assert: should return [0, 360, 0]
		assertNotNull(rot);
		assertEquals(0f, rot[0], DELTA);
		assertEquals(360f, rot[1], DELTA);
		assertEquals(0f, rot[2], DELTA);
	}

	@Test
	public void testSampleRotAtMidpointInterpolates() {
		// Act: sample at t=0.5 (halfway)
		float[] rot = fanClip.sampleRot(0, 0.5f);

		// Assert: linear interpolation → [0, 180, 0]
		assertNotNull(rot);
		assertEquals(0f, rot[0], DELTA);
		assertEquals(180f, rot[1], DELTA);
		assertEquals(0f, rot[2], DELTA);
	}

	@Test
	public void testSampleRotBeforeFirstKeyframeClampsToFirst() {
		// Act: sample before the first keyframe
		float[] rot = fanClip.sampleRot(0, -0.5f);

		// Assert: clamped to first keyframe [0, 0, 0]
		assertNotNull(rot);
		assertEquals(0f, rot[0], DELTA);
		assertEquals(0f, rot[1], DELTA);
		assertEquals(0f, rot[2], DELTA);
	}

	@Test
	public void testSampleRotAfterLastKeyframeClampsToLast() {
		// Act: sample after the last keyframe
		float[] rot = fanClip.sampleRot(0, 2.0f);

		// Assert: clamped to last keyframe [0, 360, 0]
		assertNotNull(rot);
		assertEquals(0f, rot[0], DELTA);
		assertEquals(360f, rot[1], DELTA);
		assertEquals(0f, rot[2], DELTA);
	}

	@Test
	public void testSampleRotWithInvalidBoneIdxReturnsNull() {
		assertNull(fanClip.sampleRot(-1, 0f));
		assertFalse(fanClip.sampleRotInto(-1, 0f, new float[3]));
	}

	// --- samplePos tests ---

	@Test
	public void testSamplePosAtExactFirstKeyframe() {
		// Act
		float[] pos = fanClip.samplePos(0, 0f);

		// Assert: [0, 0, 0]
		assertNotNull(pos);
		assertEquals(0f, pos[0], DELTA);
		assertEquals(0f, pos[1], DELTA);
		assertEquals(0f, pos[2], DELTA);
	}

	@Test
	public void testSamplePosAtExactLastKeyframe() {
		// Act
		float[] pos = fanClip.samplePos(0, 1f);

		// Assert: [0, 2, 0]
		assertNotNull(pos);
		assertEquals(0f, pos[0], DELTA);
		assertEquals(2f, pos[1], DELTA);
		assertEquals(0f, pos[2], DELTA);
	}

	@Test
	public void testSamplePosAtMidpointInterpolates() {
		// Act: sample at t=0.5
		float[] pos = fanClip.samplePos(0, 0.5f);

		// Assert: linear interpolation → [0, 1, 0]
		assertNotNull(pos);
		assertEquals(0f, pos[0], DELTA);
		assertEquals(1f, pos[1], DELTA);
		assertEquals(0f, pos[2], DELTA);
	}

	@Test
	public void testSamplePosWithInvalidBoneIdxReturnsNull() {
		assertNull(fanClip.samplePos(-1, 0f));
		assertFalse(fanClip.samplePosInto(-1, 0f, new float[3]));
	}

	@Test
	public void testSampleSclWithInvalidBoneIdxReturnsNull() {
		assertNull(fanClip.sampleScl(-1, 0f));
		assertFalse(fanClip.sampleSclInto(-1, 0f, new float[3]));
	}

	// --- Multiple bones test ---

	@Test
	public void testMultipleBonesHaveIndependentKeyframes() {
		// Arrange: two bones with different animations
		String[] bones = {"arm", "leg"};
		float[][] rotTimes = {
			{0f, 1f},          // arm: 0→90 X
			{0f, 0.5f, 1f}    // leg: 0→45→0 Z (3 keyframes)
		};
		float[][][] rotValues = {
			{ {0f, 0f, 0f}, {90f, 0f, 0f} },
			{ {0f, 0f, 0f}, {0f, 0f, 45f}, {0f, 0f, 0f} }
		};
		float[][] posTimes = { {0f}, {0f} };
		float[][][] posValues = { { {0f, 0f, 0f} }, { {0f, 0f, 0f} } };
		Aero_AnimationClip clip = clip("walk", Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE, 1.0f, bones, rotTimes, rotValues, posTimes, posValues);

		// Act
		int armIdx = clip.indexOfBone("arm");
		int legIdx = clip.indexOfBone("leg");
		float[] armRot = clip.sampleRot(armIdx, 0.5f);
		float[] legRot = clip.sampleRot(legIdx, 0.25f);

		// Assert: arm at 0.5s → [45, 0, 0] (halfway 0→90)
		assertEquals(0, armIdx);
		assertEquals(1, legIdx);
		assertEquals(45f, armRot[0], DELTA);
		assertEquals(0f, armRot[1], DELTA);
		assertEquals(0f, armRot[2], DELTA);

		// Assert: leg at 0.25s → [0, 0, 22.5] (halfway of first segment 0→45)
		assertEquals(0f, legRot[0], DELTA);
		assertEquals(0f, legRot[1], DELTA);
		assertEquals(22.5f, legRot[2], DELTA);
	}

	// --- Single keyframe test ---

	@Test
	public void testSingleKeyframeAlwaysReturnsThatValue() {
		// Arrange: one bone with a single keyframe
		String[] bones = {"static"};
		float[][] rotTimes = { {0f} };
		float[][][] rotValues = { { {10f, 20f, 30f} } };
		float[][] posTimes = { {0f} };
		float[][][] posValues = { { {1f, 2f, 3f} } };
		Aero_AnimationClip clip = clip("idle", Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE, 1.0f, bones, rotTimes, rotValues, posTimes, posValues);

		// Act: sample at various times — all should return the single keyframe
		float[] rotBefore = clip.sampleRot(0, -1f);
		float[] rotAt     = clip.sampleRot(0, 0f);
		float[] rotAfter  = clip.sampleRot(0, 5f);

		// Assert: always [10, 20, 30]
		assertEquals(10f, rotBefore[0], DELTA);
		assertEquals(20f, rotBefore[1], DELTA);
		assertEquals(30f, rotBefore[2], DELTA);

		assertEquals(10f, rotAt[0], DELTA);
		assertEquals(20f, rotAt[1], DELTA);
		assertEquals(30f, rotAt[2], DELTA);

		assertEquals(10f, rotAfter[0], DELTA);
		assertEquals(20f, rotAfter[1], DELTA);
		assertEquals(30f, rotAfter[2], DELTA);
	}

	// --- Interpolation at quarter points ---

	@Test
	public void testSampleRotAtQuarterPoints() {
		// Act: sample at t=0.25 and t=0.75
		float[] rotQ1 = fanClip.sampleRot(0, 0.25f);
		float[] rotQ3 = fanClip.sampleRot(0, 0.75f);

		// Assert: 25% → [0, 90, 0], 75% → [0, 270, 0]
		assertEquals(90f, rotQ1[1], DELTA);
		assertEquals(270f, rotQ3[1], DELTA);
	}

	@Test
	public void testPlayOnceClipFieldIsPlayOnce() {
		// Arrange
		String[] bones = {"bone"};
		float[][] rt = { {0f} };
		float[][][] rv = { { {0f, 0f, 0f} } };
		Aero_AnimationClip clip = clip("once", Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE, 2.0f, bones, rt, rv, rt, rv);

		// Assert
		assertEquals("once", clip.name);
		assertEquals(Aero_AnimationClip.LOOP_TYPE_PLAY_ONCE, clip.loopType);
		assertEquals(2.0f, clip.length, DELTA);
	}

	/**
	 * Helper that adapts the legacy "rotation + position only, all linear"
	 * test shape onto the current full constructor — passes nulls for the
	 * interp arrays (defaults to LINEAR everywhere) and an empty scale
	 * channel.
	 */
	private static Aero_AnimationClip clip(String name, int loopType, float length,
	                                       String[] bones,
	                                       float[][] rotTimes, float[][][] rotValues,
	                                       float[][] posTimes, float[][][] posValues) {
		return new Aero_AnimationClip(
			name, loopType, length, bones,
			rotTimes, rotValues, null,
			posTimes, posValues, null,
			null, null, null);
	}
}
