package org.example.chapter07;

/**
 * Listing 7.26 - Registering a Shutdown Hook.
 *
 * Demonstrates the standard pattern: in {@code start()}, register a
 * shutdown hook that calls {@code stop()} so the service is cleaned up
 * during an orderly JVM shutdown (last non-daemon thread exits,
 * System.exit, SIGINT/Ctrl-C).
 *
 * Constraints on shutdown hooks:
 *   - Thread-safe — they run alongside any application threads still alive.
 *   - Defensive — make no assumption about the state of the application
 *     or whether other hooks have run.
 *   - Fast — they delay JVM exit while the user is waiting.
 *   - Independent — hooks run concurrently in unspecified order. A hook
 *     that closes the log file may break a hook that wants to log.
 *
 * Best practice when you have several services to clean up: register one
 * master hook that calls them sequentially in a defined order, rather
 * than N concurrent hooks.
 *
 * Hooks do NOT run on abrupt shutdown ({@code Runtime.halt}, SIGKILL).
 *
 * This class wraps the registration so it can be called from a real
 * service start() without polluting it with anonymous-thread boilerplate.
 */
public final class ShutdownHookExample {

    private ShutdownHookExample() {}

    /**
     * Register an unstarted thread that will run {@code stopAction} when
     * the JVM begins an orderly shutdown.
     */
    public static Thread registerStopHook(Runnable stopAction) {
        Thread hook = new Thread(stopAction, "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);
        return hook;
    }
}
