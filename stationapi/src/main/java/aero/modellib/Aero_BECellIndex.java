package aero.modellib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.entity.BlockEntity;

import aero.modellib.render.Aero_CellRenderableBE;

/**
 * Spatial index for Aero-managed BlockEntities. This is the infrastructure
 * layer for future BE Cell Pages: keep renderable BEs grouped by small world
 * cells so later render passes can iterate visible cells instead of treating
 * every BE as an unrelated object.
 *
 * <p>The current pass is intentionally conservative. It does not skip vanilla
 * BE rendering or draw pages yet; it only tracks membership, dirty state and
 * visibility-friendly cell buckets.
 */
public final class Aero_BECellIndex {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.becell"));

    public static final int CELL_SIZE =
        clampInt(Integer.getInteger("aero.becell.size", 8).intValue(), 1, 32);

    private static final int SWEEP_MASK = 63;

    private static final IdentityHashMap<Object, HashMap<Long, Cell>> CELLS_BY_WORLD =
        new IdentityHashMap<Object, HashMap<Long, Cell>>();
    private static final IdentityHashMap<BlockEntity, Entry> ENTRIES =
        new IdentityHashMap<BlockEntity, Entry>();

    private static int frameCounter;
    private static int dirtyCellCount;
    private static int moveCount;
    private static int staleRemovedCount;

    private Aero_BECellIndex() {}

    /** Frame hook; performs a light periodic stale-entry sweep. */
    public static void beginFrame() {
        if (!ENABLED) return;
        frameCounter++;
        if ((frameCounter & SWEEP_MASK) == 0) {
            sweepStaleEntries();
        }
    }

    /** Adds or updates a BE in the cell index. Safe to call every tick/render. */
    public static void track(BlockEntity be) {
        if (!ENABLED || be == null) return;
        if (!(be instanceof Aero_CellRenderableBE)) return;
        Object world = be.world;
        if (world == null) {
            untrack(be);
            return;
        }

        Aero_CellRenderableBE renderable = (Aero_CellRenderableBE) be;
        int cellX = Math.floorDiv(be.x, CELL_SIZE);
        int cellY = Math.floorDiv(be.y, CELL_SIZE);
        int cellZ = Math.floorDiv(be.z, CELL_SIZE);
        long key = packCellKey(cellX, cellY, cellZ);
        int stateHash = renderable.aeroRenderStateHash();
        int orientationHash = renderable.aeroOrientationHash();
        boolean canCellPage = renderable.aeroCanCellPage();
        boolean wantsAnimation = renderable.aeroWantsAnimation();

        Entry existing = ENTRIES.get(be);
        if (existing != null
            && existing.world == world
            && existing.key == key) {
            if (existing.stateHash != stateHash
                || existing.orientationHash != orientationHash
                || existing.canCellPage != canCellPage
                || existing.wantsAnimation != wantsAnimation) {
                existing.stateHash = stateHash;
                existing.orientationHash = orientationHash;
                existing.canCellPage = canCellPage;
                existing.wantsAnimation = wantsAnimation;
                markDirty(existing.cell);
            }
            return;
        }

        if (existing != null) {
            removeEntry(be, existing, true);
            moveCount++;
        }

        Cell cell = getOrCreateCell(world, key, cellX, cellY, cellZ);
        Entry entry = new Entry(be, world, key, cell, stateHash, orientationHash,
            canCellPage, wantsAnimation);
        cell.entries.add(be);
        ENTRIES.put(be, entry);
        markDirty(cell);
    }

    /** Removes a BE from the index. Future load/unload hooks can call this. */
    public static void untrack(BlockEntity be) {
        if (be == null) return;
        Entry existing = ENTRIES.get(be);
        if (existing == null) return;
        removeEntry(be, existing, true);
    }

    /** Marks the BE's current cell dirty after an external visual-state change. */
    public static void markDirty(BlockEntity be) {
        Entry existing = ENTRIES.get(be);
        if (existing != null) {
            markDirty(existing.cell);
        }
    }

    /** Snapshot of all cells. Intended for debug/future renderer iteration. */
    public static ArrayList<Cell> snapshotCells() {
        ArrayList<Cell> out = new ArrayList<Cell>();
        Iterator<HashMap<Long, Cell>> maps = CELLS_BY_WORLD.values().iterator();
        while (maps.hasNext()) {
            out.addAll(maps.next().values());
        }
        return out;
    }

    /** Snapshot of cells whose x/z chunk is currently visible. */
    public static ArrayList<Cell> snapshotVisibleCells() {
        ArrayList<Cell> out = new ArrayList<Cell>();
        Iterator<HashMap<Long, Cell>> maps = CELLS_BY_WORLD.values().iterator();
        while (maps.hasNext()) {
            Iterator<Cell> cells = maps.next().values().iterator();
            while (cells.hasNext()) {
                Cell cell = cells.next();
                if (Aero_ChunkVisibility.isBlockAreaChunkVisible(
                        cell.minBlockX(), cell.minBlockZ(),
                        cell.maxBlockX(), cell.maxBlockZ())) {
                    out.add(cell);
                }
            }
        }
        return out;
    }

    public static int entryCount() {
        return ENTRIES.size();
    }

    public static int cellCount() {
        int count = 0;
        Iterator<HashMap<Long, Cell>> maps = CELLS_BY_WORLD.values().iterator();
        while (maps.hasNext()) {
            count += maps.next().size();
        }
        return count;
    }

    public static int dirtyCellCount() {
        return dirtyCellCount;
    }

    public static int moveCount() {
        return moveCount;
    }

    public static int staleRemovedCount() {
        return staleRemovedCount;
    }

    public static final class Cell {
        public final Object world;
        public final long key;
        public final int cellX;
        public final int cellY;
        public final int cellZ;

        private final ArrayList<BlockEntity> entries = new ArrayList<BlockEntity>();
        private boolean dirty;

        private Cell(Object world, long key, int cellX, int cellY, int cellZ) {
            this.world = world;
            this.key = key;
            this.cellX = cellX;
            this.cellY = cellY;
            this.cellZ = cellZ;
        }

        public int size() {
            return entries.size();
        }

        public BlockEntity get(int index) {
            return entries.get(index);
        }

        public boolean isDirty() {
            return dirty;
        }

        public void clearDirty() {
            if (dirty) {
                dirty = false;
                dirtyCellCount--;
            }
        }

        public int minBlockX() {
            return cellX * CELL_SIZE;
        }

        public int minBlockY() {
            return cellY * CELL_SIZE;
        }

        public int minBlockZ() {
            return cellZ * CELL_SIZE;
        }

        public int maxBlockX() {
            return minBlockX() + CELL_SIZE - 1;
        }

        public int maxBlockZ() {
            return minBlockZ() + CELL_SIZE - 1;
        }
    }

    private static final class Entry {
        final BlockEntity be;
        final Object world;
        final long key;
        final Cell cell;
        int stateHash;
        int orientationHash;
        boolean canCellPage;
        boolean wantsAnimation;

        Entry(BlockEntity be, Object world, long key, Cell cell,
              int stateHash, int orientationHash,
              boolean canCellPage, boolean wantsAnimation) {
            this.be = be;
            this.world = world;
            this.key = key;
            this.cell = cell;
            this.stateHash = stateHash;
            this.orientationHash = orientationHash;
            this.canCellPage = canCellPage;
            this.wantsAnimation = wantsAnimation;
        }
    }

    private static Cell getOrCreateCell(Object world, long key, int cellX, int cellY, int cellZ) {
        HashMap<Long, Cell> cells = CELLS_BY_WORLD.get(world);
        if (cells == null) {
            cells = new HashMap<Long, Cell>();
            CELLS_BY_WORLD.put(world, cells);
        }
        Long boxedKey = Long.valueOf(key);
        Cell cell = cells.get(boxedKey);
        if (cell == null) {
            cell = new Cell(world, key, cellX, cellY, cellZ);
            cells.put(boxedKey, cell);
        }
        return cell;
    }

    private static void sweepStaleEntries() {
        Iterator<Map.Entry<BlockEntity, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockEntity, Entry> mapEntry = it.next();
            BlockEntity be = mapEntry.getKey();
            Entry entry = mapEntry.getValue();
            if (be.world == null || be.world != entry.world) {
                removeEntry(be, entry, false);
                it.remove();
                staleRemovedCount++;
            }
        }
    }

    private static void removeEntry(BlockEntity be, Entry entry, boolean removeFromEntries) {
        entry.cell.entries.remove(be);
        markDirty(entry.cell);
        if (entry.cell.entries.isEmpty()) {
            removeCell(entry.cell);
        }
        if (removeFromEntries) {
            ENTRIES.remove(be);
        }
    }

    private static void removeCell(Cell cell) {
        HashMap<Long, Cell> cells = CELLS_BY_WORLD.get(cell.world);
        if (cells == null) return;
        cells.remove(Long.valueOf(cell.key));
        if (cell.dirty) {
            cell.dirty = false;
            dirtyCellCount--;
        }
        if (cells.isEmpty()) {
            CELLS_BY_WORLD.remove(cell.world);
        }
    }

    private static void markDirty(Cell cell) {
        if (cell == null || cell.dirty) return;
        cell.dirty = true;
        dirtyCellCount++;
    }

    private static long packCellKey(int cellX, int cellY, int cellZ) {
        return ((long) cellX & 0x3FFFFFL) << 42
            | ((long) cellY & 0xFFFFFL) << 22
            | ((long) cellZ & 0x3FFFFFL);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
