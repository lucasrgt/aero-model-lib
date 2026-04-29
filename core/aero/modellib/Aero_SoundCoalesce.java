package aero.modellib;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-frame coalescer for animation-driven sound calls. Caps how many
 * instances of the same sound name fire in one drain — prevents the
 * "144 BEs all play random.click on the same tick" hitch when a chunk
 * full of identical machines first becomes visible.
 *
 * <h2>Why</h2>
 * <p>Paul Lamb's SoundSystem (the audio backend Beta 1.7.3 ships with)
 * serialises {@code playSound} requests through a small finite source
 * pool. When N requests land in the same render frame, the first few
 * succeed, the rest cause source eviction + reallocation churn through
 * OpenAL/JNI. With N ~ 144 (mega test, 4×3×3 stacks × 16 floors × one
 * AnimatedMegaModel cluster) the spike is visible as a 1-frame stutter
 * synchronized with the keyframe event — calmilamsy-flavoured "flick".
 *
 * <p>Real gameplay also hits this: a tech-mod base with 20+ generators,
 * processors, conveyors firing footstep / process / chime sounds on
 * close timers will produce the same pattern at smaller amplitude.
 *
 * <h2>Behaviour</h2>
 * <p>Each {@link #queue} call appends a {@code PendingSound} to a
 * per-name bucket. {@link #flush} drains every bucket: for each bucket
 * with more than {@link #getMaxPerName()} entries, it sorts by
 * camera-distance ascending and plays only the closest N — the rest
 * are dropped. The flush also clears all buckets, so the next frame
 * starts empty.
 *
 * <p>Default cap: 3 simultaneous fires per name per drain. Tuned by
 * ear: most "machine ambience" patterns (clicks, hisses, chime loops)
 * are perceptually identical at 3 vs 30 sources but the 30-source
 * variant audibly stutters on entry. Configurable via
 * {@link #setMaxPerName} or {@code -Daero.soundcap=N}.
 *
 * <p>Toggle the whole system off with {@code -Daero.soundcoalesce=false}
 * — useful for downstream mods that want raw {@code world.playSound}
 * semantics for gameplay-critical timing.
 *
 * <h2>Threading</h2>
 * <p>Single-threaded. Both queue (called from BE.tick()) and flush
 * (called from render thread / world tick) run on the main thread.
 * No synchronisation needed.
 *
 * <h2>API contract</h2>
 * <p>The dispatcher is the runtime-specific bridge to
 * {@code World.playSound}. Core can't reference Minecraft API directly;
 * stationapi/modloader sides hand a {@code (x,y,z,name,vol,pitch) -> void}
 * lambda that wraps the right vanilla call. The dispatcher is also where
 * camera-relative coordinates can be converted if the runtime needs that.
 */
public final class Aero_SoundCoalesce {

    public static final boolean ENABLED =
        !"false".equalsIgnoreCase(System.getProperty("aero.soundcoalesce"));

    private static int maxPerName = parseMaxPerName();

    private static int parseMaxPerName() {
        String s = System.getProperty("aero.soundcap");
        if (s == null) return 3;
        try {
            int n = Integer.parseInt(s);
            return n < 1 ? 1 : n;
        } catch (NumberFormatException nfe) {
            return 3;
        }
    }

    /** Bridge to the runtime's {@code World.playSound}. */
    public interface Dispatcher {
        void play(double x, double y, double z, String name, float volume, float pitch);
    }

    /** Single queued sound. SoA-style packed inside a per-name {@link Bucket}. */
    static final class PendingSound {
        double x, y, z;
        float volume, pitch;
        // distSq is computed lazily at flush time using the camera coords
        // passed to flush() — queue() doesn't need a camera reference.
        double distSq;
    }

    static final class Bucket {
        PendingSound[] entries = new PendingSound[8];
        int count = 0;

        void add(double x, double y, double z, float volume, float pitch) {
            if (count == entries.length) {
                entries = Arrays.copyOf(entries, entries.length * 2);
            }
            PendingSound p = entries[count];
            if (p == null) {
                p = new PendingSound();
                entries[count] = p;
            }
            p.x = x; p.y = y; p.z = z;
            p.volume = volume; p.pitch = pitch;
            count++;
        }

        void clear() {
            // PendingSound entries themselves are reused next frame; we
            // just mark the bucket as drained.
            count = 0;
        }
    }

    private static final HashMap<String, Bucket> BUCKETS = new HashMap<String, Bucket>();

    private Aero_SoundCoalesce() {}

    /**
     * Queues a sound for the next {@link #flush}. Falls back to immediate
     * fire-and-forget if the system is disabled — but the runtime still
     * has to provide its own dispatcher in that case (this method only
     * queues, never plays directly).
     *
     * <p>Volume / pitch follow vanilla {@code world.playSound} semantics.
     */
    public static void queue(double x, double y, double z, String name,
                             float volume, float pitch) {
        if (!ENABLED) {
            // When disabled the queue stays empty; callers should bypass
            // this path entirely (call world.playSound directly). Quiet
            // no-op here so toggling at runtime doesn't crash.
            return;
        }
        if (name == null) return;
        Bucket b = BUCKETS.get(name);
        if (b == null) {
            b = new Bucket();
            BUCKETS.put(name, b);
        }
        b.add(x, y, z, volume, pitch);
    }

    /**
     * Drains every queued sound through the dispatcher. For each name
     * bucket with more than {@link #getMaxPerName()} entries, sorts by
     * distance to {@code (camX, camY, camZ)} ascending and plays only
     * the closest N. The rest are silently dropped.
     *
     * <p>Always clears all buckets — including the cap-exceeded ones —
     * so the next frame starts fresh.
     */
    public static void flush(double camX, double camY, double camZ,
                             Dispatcher dispatcher) {
        if (!ENABLED || BUCKETS.isEmpty() || dispatcher == null) return;
        Iterator<Map.Entry<String, Bucket>> it = BUCKETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            String name = entry.getKey();
            Bucket b = entry.getValue();
            int n = b.count;
            if (n == 0) continue;
            int limit = (n <= maxPerName) ? n : maxPerName;
            if (n > maxPerName) {
                // Compute distances + partial-sort only when over cap.
                // Below cap, every entry plays — no sort needed.
                for (int i = 0; i < n; i++) {
                    PendingSound p = b.entries[i];
                    double dx = p.x - camX, dy = p.y - camY, dz = p.z - camZ;
                    p.distSq = dx*dx + dy*dy + dz*dz;
                }
                partialSortClosest(b.entries, n, limit);
            }
            for (int i = 0; i < limit; i++) {
                PendingSound p = b.entries[i];
                dispatcher.play(p.x, p.y, p.z, name, p.volume, p.pitch);
            }
            b.clear();
        }
    }

    /**
     * Partial selection sort: brings the {@code k} smallest-distSq
     * entries to the front of {@code arr[0..n)}. O(n*k) — fine for
     * small k (3-4) and modest n (hundreds). Avoids the allocation of
     * {@code Arrays.sort} on a wrapper-array.
     */
    private static void partialSortClosest(PendingSound[] arr, int n, int k) {
        for (int i = 0; i < k; i++) {
            int minIdx = i;
            double minVal = arr[i].distSq;
            for (int j = i + 1; j < n; j++) {
                if (arr[j].distSq < minVal) {
                    minVal = arr[j].distSq;
                    minIdx = j;
                }
            }
            if (minIdx != i) {
                PendingSound tmp = arr[i];
                arr[i] = arr[minIdx];
                arr[minIdx] = tmp;
            }
        }
    }

    public static int getMaxPerName() { return maxPerName; }

    public static void setMaxPerName(int n) {
        maxPerName = n < 1 ? 1 : n;
    }

    /** Diagnostic — current pending count summed across all buckets. */
    public static int pendingCount() {
        int sum = 0;
        Iterator<Bucket> it = BUCKETS.values().iterator();
        while (it.hasNext()) sum += it.next().count;
        return sum;
    }

    /** Diagnostic — drop everything without firing. Mostly for tests. */
    public static void clearAll() {
        Iterator<Bucket> it = BUCKETS.values().iterator();
        while (it.hasNext()) it.next().clear();
    }
}
