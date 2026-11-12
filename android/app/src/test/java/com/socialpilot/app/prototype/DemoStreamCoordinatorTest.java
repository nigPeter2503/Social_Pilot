package com.socialpilot.app.prototype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class DemoStreamCoordinatorTest {
    @Test
    public void cancellingOneStreamDoesNotStopTheOtherOrAcceptLateChunks() {
        ManualScheduler scheduler = new ManualScheduler();
        DemoStreamCoordinator coordinator = new DemoStreamCoordinator(scheduler);
        List<String> events = new ArrayList<>();
        DemoStreamCoordinator.Listener listener = (channel, phase, text) ->
                events.add(channel + ":" + phase + ":" + text);
        assertTrue(coordinator.start("A", Arrays.asList("A1", "A2"), listener));
        assertTrue(coordinator.start("B", Arrays.asList("B1", "B2"), listener));
        assertFalse(coordinator.start("A", Arrays.asList("duplicate"), listener));

        coordinator.cancel("A");
        scheduler.runAll();

        assertEquals(Arrays.asList("A:RUNNING:", "B:RUNNING:", "A:CANCELLED:",
                "B:RUNNING:B1", "B:COMPLETE:B1 B2"), events);
    }

    @Test
    public void retryAfterErrorIgnoresCallbacksFromOldRequest() {
        ManualScheduler scheduler = new ManualScheduler();
        DemoStreamCoordinator coordinator = new DemoStreamCoordinator(scheduler);
        List<String> events = new ArrayList<>();
        DemoStreamCoordinator.Listener listener = (channel, phase, text) ->
                events.add(channel + ":" + phase + ":" + text);
        coordinator.start("A", Arrays.asList("old"), listener);
        coordinator.fail("A");
        coordinator.start("A", Arrays.asList("new"), listener);

        scheduler.runAll();

        assertEquals(Arrays.asList("A:RUNNING:", "A:ERROR:", "A:RUNNING:",
                "A:COMPLETE:new"), events);
    }

    private static final class ManualScheduler implements DemoStreamCoordinator.Scheduler {
        private final List<Runnable> actions = new ArrayList<>();

        @Override public void schedule(Runnable action, long delayMillis) {
            actions.add(action);
        }

        void runAll() {
            for (Runnable action : actions) action.run();
        }
    }
}
