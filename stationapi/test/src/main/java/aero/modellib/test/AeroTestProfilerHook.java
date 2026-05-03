package aero.modellib.test;

import aero.modellib.util.Aero_Profiler;

/**
 * Test-mod-only hook that installs two {@link Aero_Profiler#dump()} drivers
 * when profiling is enabled (<code>-Daero.profiler=true</code>):
 *
 * <ul>
 *   <li>A 60-second periodic dump on a daemon thread, so long sessions
 *       produce a streaming view of what's hot per minute (instead of one
 *       giant blob at the very end).</li>
 *   <li>A JVM shutdown hook so the final partial-minute also gets dumped
 *       when the user closes the game.</li>
 * </ul>
 *
 * <p>Gated entirely by {@link Aero_Profiler#isEnabled()} — when the flag is
 * off, calling {@link #install()} is a no-op and no thread is spawned, so
 * non-profile dev runs pay nothing.
 */
public final class AeroTestProfilerHook {

    private static volatile boolean installed;

    private AeroTestProfilerHook() {}

    public static synchronized void install() {
        if (installed) return;
        if (!Aero_Profiler.isEnabled()) return;
        installed = true;

        Thread periodic = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(60_000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    Aero_Profiler.dump();
                }
            }
        }, "aero-profiler-dumper");
        periodic.setDaemon(true);
        periodic.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                Aero_Profiler.dump();
            }
        }, "aero-profiler-shutdown"));

        // Optional benchmark mode — `-Daero.benchmark.exitAfterSec=N` quits
        // the JVM cleanly N seconds after install. Lets a CI / local script
        // wrap a fixed-duration profile run without manual close. JFR's
        // shutdown hook flushes the recording before exit, so the .jfr is
        // safe to parse afterwards.
        long exitAfter = Long.getLong("aero.benchmark.exitAfterSec", 0L);
        if (exitAfter > 0) {
            Thread killer = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(exitAfter * 1000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    System.out.println("[AeroTestProfilerHook] benchmark window elapsed ("
                        + exitAfter + "s) — dumping + exiting");
                    Aero_Profiler.dump();
                    System.exit(0);
                }
            }, "aero-benchmark-killer");
            killer.setDaemon(true);
            killer.start();
            System.out.println("[AeroTestProfilerHook] installed (periodic 60s + shutdown dump"
                + " + benchmark exit @ " + exitAfter + "s)");
        } else {
            System.out.println("[AeroTestProfilerHook] installed (periodic 60s + shutdown dump)");
        }
    }
}
