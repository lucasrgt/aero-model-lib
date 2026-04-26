package aero.modellib;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight named-section timer for hot animation/render paths.
 *
 * <p><b>Thread safety:</b> the profiler is intended for the Minecraft
 * Beta 1.7.3 client/server thread which is single-threaded by design.
 * {@link #start} and {@link #end} are NOT synchronized — they read/write
 * shared maps and the only concession to safety is on {@link #dump} and
 * {@link #reset}, which are synchronized so an off-thread debug call (e.g.
 * a JMX hook) cannot race with a partial section table. If you instrument
 * code that genuinely runs on background threads, wrap your own calls in
 * the same monitor or use Java Flight Recorder instead.
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
    private static final Map openStarts = new HashMap();

    private Aero_Profiler() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void start(String section) {
        if (!enabled) return;
        openStarts.put(section, Long.valueOf(System.nanoTime()));
    }

    public static void end(String section) {
        if (!enabled) return;
        Long start = (Long) openStarts.remove(section);
        if (start == null) return;
        long elapsed = System.nanoTime() - start.longValue();
        Section s = (Section) sections.get(section);
        if (s == null) {
            s = new Section();
            sections.put(section, s);
        }
        s.calls++;
        s.totalNanos += elapsed;
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
        openStarts.clear();
    }

    private static final class Section {
        long calls;
        long totalNanos;
    }
}
