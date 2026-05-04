package org.example.chapter06;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Listing 6.9 - Class Illustrating Confusing Timer Behavior.
 *
 * Two tasks scheduled one second apart. The first throws a
 * {@link RuntimeException}; this is the booby trap of {@link Timer}:
 *
 *   - Timer runs tasks on a SINGLE thread.
 *   - An unchecked exception in a task TERMINATES that thread.
 *   - All subsequent tasks are silently dropped — no notification.
 *   - Calling cancel() afterward throws IllegalStateException.
 *
 * Run main() to see that the second task never prints. Real code that
 * relies on Timer is one bad task away from a silent outage.
 *
 * The replacement is {@link java.util.concurrent.ScheduledThreadPoolExecutor}.
 */
public class OutOfTime {

    public static void main(String[] args) throws Exception {
        Timer timer = new Timer();
        timer.schedule(new ThrowTask(), 1);
        Thread.sleep(1000);
        timer.schedule(new ThrowTask(), 1);
        Thread.sleep(1000);
        timer.cancel();
    }

    static class ThrowTask extends TimerTask {
        @Override
        public void run() {
            throw new RuntimeException("oops");
        }
    }
}
