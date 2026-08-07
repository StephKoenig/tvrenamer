package org.tvrenamer.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ShowOptionDisplayNameTest {
    private static final AtomicInteger ID = new AtomicInteger(880001);

    @Test
    public void getNameReturnsOverrideWhenSet() {
        Series s = Series.createSeries(ID.getAndIncrement(), "Ciudad del Sol");
        assertEquals("Ciudad del Sol", s.getName());
        s.setDisplayNameOverride("Sun City");
        assertEquals("Sun City", s.getName());
    }

    @Test
    public void blankOrNullOverrideFallsBackToOriginal() {
        Series s = Series.createSeries(ID.getAndIncrement(), "Ciudad del Sol");
        s.setDisplayNameOverride("Sun City");
        s.setDisplayNameOverride(null);
        assertEquals("Ciudad del Sol", s.getName());
        s.setDisplayNameOverride("   ");
        assertEquals("Ciudad del Sol", s.getName());
    }
}
