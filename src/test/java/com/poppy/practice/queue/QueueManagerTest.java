package com.poppy.practice.queue;

import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.ProfileManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QueueManagerTest {
    private final QueueManager manager = new QueueManager(new ProfileManager(),
            new KitManager(), null, null, null);

    @Test
    public void sharedArenaReleaseRetriesAllThreeRegisteredQueues() {
        List<String> retried = new ArrayList<String>();
        manager.tryMatchAll(retried::add);
        assertEquals(Arrays.asList("nodebuff", "boxing", "combo"), retried);
        assertEquals(3, manager.all().size());
    }

    @Test
    public void nestedArenaReleaseDoesNotReenterTheFullQueueSweep() {
        List<String> retried = new ArrayList<String>();
        manager.tryMatchAll(kit -> {
            retried.add(kit);
            manager.tryMatchAll(nested -> fail("Queue sweep must not recurse"));
        });
        assertEquals(Arrays.asList("nodebuff", "boxing", "combo"), retried);
    }

    @Test
    public void aFailedRetryDoesNotPermanentlyLockAllQueues() {
        try {
            manager.tryMatchAll(kit -> { throw new IllegalStateException("test failure"); });
            fail("The failure should be reported to the caller");
        } catch (IllegalStateException expected) {
            assertEquals("test failure", expected.getMessage());
        }
        List<String> retried = new ArrayList<String>();
        manager.tryMatchAll(retried::add);
        assertEquals(Arrays.asList("nodebuff", "boxing", "combo"), retried);
    }

    @Test
    public void shutdownDoesNotStartAnotherQueueSweep() {
        manager.clear();
        List<String> retried = new ArrayList<String>();
        manager.tryMatchAll(retried::add);
        assertTrue(retried.isEmpty());
    }
}
