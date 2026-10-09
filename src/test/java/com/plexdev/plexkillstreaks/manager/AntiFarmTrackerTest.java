package com.plexdev.plexkillstreaks.manager;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AntiFarmTrackerTest {
    final AtomicLong clock = new AtomicLong();
    final UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3);
    final AntiFarmTracker tracker = new AntiFarmTracker(180, 2, clock::get);
    void reachLimit() {
        assertTrue(tracker.tryCount(a,b)); clock.set(60); assertTrue(tracker.tryCount(a,b));
    }
    @Test void firstKillCounts() { assertTrue(tracker.tryCount(a,b)); }
    @Test void secondKillWithinWindowCounts() { reachLimit(); }
    @Test void thirdKillWithinWindowBlocked() { reachLimit(); clock.set(90); assertFalse(tracker.tryCount(a,b)); }
    @Test void blockedKillDoesNotExtendWindow() {
        reachLimit(); clock.set(120); assertFalse(tracker.tryCount(a,b));
        clock.set(170); assertFalse(tracker.tryCount(a,b));
        clock.set(180); assertTrue(tracker.tryCount(a,b));
        clock.set(181); assertTrue(tracker.tryCount(a,b)); assertFalse(tracker.tryCount(a,b));
    }
    @Test void killAtExactExpiryCounts() { reachLimit(); clock.set(180); assertTrue(tracker.tryCount(a,b)); }
    @Test void differentVictimCounts() { reachLimit(); assertTrue(tracker.tryCount(a,c)); }
    @Test void differentKillerCounts() { reachLimit(); assertTrue(tracker.tryCount(c,b)); }
    @Test void reversePairIsIndependent() { reachLimit(); assertTrue(tracker.tryCount(b,a)); }
    @Test void thresholdOneWorks() {
        var strict = new AntiFarmTracker(180,1,clock::get);
        assertTrue(strict.tryCount(a,b)); assertFalse(strict.tryCount(a,b));
    }
    @Test void expiredCleanup() {
        tracker.tryCount(a,b); clock.set(60); tracker.tryCount(a,b);
        clock.set(150); tracker.tryCount(a,c); clock.set(180);
        assertEquals(1,tracker.cleanup()); assertEquals(1,tracker.size());
    }
    @Test void zeroWindowDoesNotRetainHistory() {
        var zero = new AntiFarmTracker(0,2,clock::get);
        for(int i=0;i<10;i++) assertTrue(zero.tryCount(a,b));
        assertEquals(0,zero.size());
    }
    @Test void capacityRejectsNewPairsUntilExpired() {
        for (int i=0;i<100000;i++) assertTrue(tracker.tryCount(a,new UUID(1,i)));
        assertFalse(tracker.tryCount(a,b)); assertEquals(100000,tracker.size());
        assertTrue(tracker.tryCount(a,new UUID(1,0)));
        assertFalse(tracker.tryCount(a,new UUID(1,0)));
        clock.set(180); assertTrue(tracker.tryCount(a,b)); assertEquals(1,tracker.size());
    }
    @Test void saturatedRejectionsDoNotRepeatCleanupBeforeExpiry() {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var bounded = new AntiFarmTracker(180,2, () -> { reads.incrementAndGet(); return clock.get(); });
        for (int i=0;i<100000;i++) bounded.tryCount(a,new UUID(1,i));
        assertFalse(bounded.tryCount(a,b)); reads.set(0);
        for (int i=0;i<10;i++) assertFalse(bounded.tryCount(a,b));
        assertEquals(10,reads.get());
    }
}
