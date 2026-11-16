package client.nowhere.helper;

import client.nowhere.dao.FeatureFlagDAO;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureFlagHelperTest {

    private final FeatureFlagDAO dao = mock(FeatureFlagDAO.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-15T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final FeatureFlagHelper helper = new FeatureFlagHelper(dao, clock);

    @Test
    void readsAFlagOnceThenServesItFromCacheUntilItExpires() {
        when(dao.getFlagValue("locationVoting")).thenReturn(true, false);

        assertTrue(helper.getFlagValue("locationVoting"));
        now.set(now.get().plus(FeatureFlagHelper.CACHE_TTL).minusSeconds(1));
        assertTrue(helper.getFlagValue("locationVoting"));
        verify(dao, times(1)).getFlagValue("locationVoting");

        now.set(now.get().plusSeconds(1));
        assertFalse(helper.getFlagValue("locationVoting"));
        verify(dao, times(2)).getFlagValue("locationVoting");
    }

    @Test
    void cachesEachFlagSeparatelyAndTreatsAMissingValueAsFalse() {
        when(dao.getFlagValue("a")).thenReturn(true);
        when(dao.getFlagValue("b")).thenReturn(null);

        assertTrue(helper.getFlagValue("a"));
        assertFalse(helper.getFlagValue("b"));
        assertFalse(helper.getFlagValue("b"));

        verify(dao, times(1)).getFlagValue("a");
        verify(dao, times(1)).getFlagValue("b");
    }
}
