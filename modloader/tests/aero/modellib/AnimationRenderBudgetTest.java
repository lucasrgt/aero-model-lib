package aero.modellib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnimationRenderBudgetTest {

    @Test
    public void lowPriorityObjectsStopConsumingBudgetEarly() {
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        for (int i = 0; i < 64; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.STATIC,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                200d, 0d, 0d, 0.5d));
        assertEquals(1, Aero_AnimationRenderBudget.priorityRejectedThisFrame());
    }

    @Test
    public void criticalNearObjectsCanUseReserveAfterNormalBudgetIsFull() {
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        for (int i = 0; i < Aero_AnimationRenderBudget.MAX_ANIMATED; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d));
        assertTrue(Aero_AnimationRenderBudget.criticalAcceptedThisFrame() > 0);
    }

    @Test
    public void recentlyAnimatedObjectsGetShortHysteresisReserve() {
        Object key = new Object();
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                80d, 0d, 0d, 2d, key));

        Aero_AnimationRenderBudget.beginFrame();
        for (int i = 0; i < Aero_AnimationRenderBudget.MAX_ANIMATED; i++) {
            assertEquals(Aero_RenderLod.ANIMATED,
                Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                    80d, 0d, 0d, 2d));
        }

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                80d, 0d, 0d, 2d, key));
        assertEquals(1, Aero_AnimationRenderBudget.hysteresisAcceptedThisFrame());
    }

    @Test
    public void keyedDecisionIsReusedWithinFrame() {
        Object key = new Object();
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));
        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));

        assertEquals(1, Aero_AnimationRenderBudget.acceptedThisFrame());
    }

    @Test
    public void primitiveKeyedDecisionIsReusedWithinFrame() {
        long key = 0x1234ABCDL;
        Aero_AnimationRenderBudget.updateFromDisplayHeight(1080);
        Aero_AnimationRenderBudget.beginFrame();

        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));
        assertEquals(Aero_RenderLod.ANIMATED,
            Aero_AnimationRenderBudget.apply(Aero_RenderLod.ANIMATED,
                4d, 0d, 0d, 2d, key));

        assertEquals(1, Aero_AnimationRenderBudget.acceptedThisFrame());
    }
}
