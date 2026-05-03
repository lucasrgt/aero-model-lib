package aero.modellib.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight named-section timer for hot animation/render paths.
 *
 * <p><b>Thread safety:</b> the profiler is intended for the Minecraft
 * Beta 1.7.3 client/server thread which is single-threaded by design, but
 * enabled calls synchronize on the class monitor so an off-thread debug hook
 * cannot corrupt the section maps. Disabled calls still short-circuit before
 * taking a lock.
 *
 * <p>The profiler is fully off by default — every call short-circuits on a
 * single boolean read so untouched ship builds pay nothing. Enable it at
 * launch with {@code -Daero.profiler=true} (or programmatically via
 * {@link #setEnabled(boolean)}) and pair it with Java Flight Recorder for
 * full method-level data.
 *
 * <p>Typical use:
 * <pre>
 *   Aero_Profiler.start("aero.tick");
 *   try {
 *       playback.tick();
 *   } finally {
 *       Aero_Profiler.end("aero.tick");
 *   }
 *
 *   // After a render burst:
 *   Aero_Profiler.dump();   // prints {section, calls, total ms, avg µs}
 * </pre>
 *
 * <p>Sections are nested by name only — there is no parent/child stack.
 * Calls are accumulated globally; {@link #reset()} zeroes the table and
 * {@link #dump()} prints + resets.
 */
public final class Aero_Profiler {

    private static volatile boolean enabled = Boolean.getBoolean("aero.profiler");

    private static final Map sections = new LinkedHashMap();

    private Aero_Profiler() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Starts a section timer. Zero-allocation when the section was seen at
     * least once (the start time is stored on the cached {@link Section}
     * object instead of in a parallel {@code Map<String, Long>}).
     */
    public static void start(String section) {
        if (!enabled) return;
        synchronized (Aero_Profiler.class) {
            Section s = (Section) sections.get(section);
            if (s == null) {
                s = new Section();
                sections.put(section, s);
            }
            s.openStartNanos = System.nanoTime();
        }
    }

    public static void end(String section) {
        if (!enabled) return;
        synchronized (Aero_Profiler.class) {
            Section s = (Section) sections.get(section);
            if (s == null || s.openStartNanos == 0L) return;
            long elapsed = System.nanoTime() - s.openStartNanos;
            s.openStartNanos = 0L;
            s.calls++;
            s.totalNanos += elapsed;
        }
    }

    /** Prints a one-shot table to stdout and resets counters. */
    public static synchronized void dump() {
        if (sections.isEmpty()) {
            System.out.println("[Aero_Profiler] no samples recorded (enabled=" + enabled + ")");
            return;
        }
        System.out.println("[Aero_Profiler] section                                       calls       total ms     avg us");
        Iterator it = sections.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry e = (Map.Entry) it.next();
            String name = (String) e.getKey();
            Section s = (Section) e.getValue();
            double totalMs = s.totalNanos / 1_000_000.0;
            double avgUs = s.calls == 0 ? 0.0 : s.totalNanos / 1000.0 / s.calls;
            System.out.println(String.format("[Aero_Profiler]   %-50s %8d   %10.3f   %8.2f",
                name, s.calls, totalMs, avgUs));
        }
        reset();
    }

    public static synchronized void reset() {
        sections.clear();
    }

    private static final class Section {
        long calls;
        long totalNanos;
        long openStartNanos;   // 0 = not currently open; set by start(), cleared by end()
    }
}
