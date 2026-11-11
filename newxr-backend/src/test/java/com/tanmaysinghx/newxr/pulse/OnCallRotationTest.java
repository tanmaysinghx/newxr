package com.tanmaysinghx.newxr.pulse;

import static org.junit.jupiter.api.Assertions.*;

import com.tanmaysinghx.newxr.code.CiServiceStatusAccess;
import com.tanmaysinghx.newxr.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OnCallRotationTest {

    private static final Instant MONDAY = Instant.parse("2026-11-09T09:00:00Z");

    private static User user(long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        u.setEmail(name.toLowerCase() + "@example.com");
        return u;
    }

    private static OnCallLayer weekly(User... members) {
        OnCallLayer l = new OnCallLayer();
        l.setName("Primary");
        l.setRotationStart(MONDAY);
        l.setRotationDays(7);
        l.getMembers().addAll(List.of(members));
        return l;
    }

    @Test
    void rotatesWeeklyAndWrapsAround() {
        User a = user(1, "Ana"), b = user(2, "Ben"), c = user(3, "Cy");
        OnCallLayer l = weekly(a, b, c);
        assertEquals("Ana", OnCall.userAt(l, MONDAY.plus(Duration.ofHours(1))).getDisplayName());
        assertEquals("Ben", OnCall.userAt(l, MONDAY.plus(Duration.ofDays(7))).getDisplayName());
        assertEquals("Cy", OnCall.userAt(l, MONDAY.plus(Duration.ofDays(15))).getDisplayName());
        assertEquals("Ana", OnCall.userAt(l, MONDAY.plus(Duration.ofDays(21))).getDisplayName());
    }

    @Test
    void timesBeforeTheRotationStartCountBackwards() {
        User a = user(1, "Ana"), b = user(2, "Ben"), c = user(3, "Cy");
        OnCallLayer l = weekly(a, b, c);
        // One week before the start is the last member's turn, not an index error.
        assertEquals("Cy", OnCall.userAt(l, MONDAY.minus(Duration.ofDays(3))).getDisplayName());
    }

    @Test
    void emptyLayerHasNobodyOnCall() {
        assertNull(OnCall.userAt(weekly(), MONDAY));
        assertTrue(OnCall.shifts(weekly(), MONDAY, MONDAY.plus(Duration.ofDays(7))).isEmpty());
    }

    @Test
    void shiftsAreClippedToTheWindow() {
        User a = user(1, "Ana"), b = user(2, "Ben");
        OnCallLayer l = weekly(a, b);
        Instant from = MONDAY.plus(Duration.ofDays(3)), to = MONDAY.plus(Duration.ofDays(10));
        List<OnCall.Shift> s = OnCall.shifts(l, from, to);
        assertEquals(2, s.size());
        assertEquals(from, s.getFirst().start());
        assertEquals(MONDAY.plus(Duration.ofDays(7)), s.getFirst().end());
        assertEquals("Ben", s.get(1).user().getDisplayName());
        assertEquals(to, s.get(1).end());
    }

    @Test
    void severitiesMapToPriorities() {
        assertEquals("P1", IntegrationService.priority("critical"));
        assertEquals("P3", IntegrationService.priority("Warning"));
        assertNull(IntegrationService.priority("whatever"));
        assertEquals("PASSED", CiServiceStatusAccess.status("success"));
        assertEquals("FAILED", CiServiceStatusAccess.status("failure"));
        assertEquals("STOPPED", CiServiceStatusAccess.status("cancelled"));
    }
}
