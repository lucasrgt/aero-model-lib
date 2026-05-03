package aero.modellib.skeletal;

/**
 * Renderer-owned display-list pages for rigid animated groups.
 *
 * <p>Pure int holder so the shared core stays free of GL imports. The
 * platform renderer compiles/deletes the list ids on the render thread.
 */
public final class Aero_BonePageLists {
    public final int[] staticPages;   // [brightness bucket], null when not compiled
    public final int[][] bonePages;   // [named group][brightness bucket], entries may be null
    public final boolean hasAnyPages;

    public Aero_BonePageLists(int[] staticPages, int[][] bonePages, boolean hasAnyPages) {
        this.staticPages = staticPages;
        this.bonePages = bonePages;
        this.hasAnyPages = hasAnyPages;
    }
}
