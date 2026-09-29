package ml.mypals.vectorthree.core;

import ml.mypals.vectorthree.core.clips.TimeMap;
import ml.mypals.vectorthree.core.clips.TimeMap.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeMapTest {
    // Two clips: project 10..30 shows replay 100..120, project 50..60 shows replay 0..10 (out of order, with gaps).
    private final TimeMap map = TimeMap.of(List.of(new Segment(50, 10, 0), new Segment(10, 20, 100)));

    @Test
    void insideSegments() {
        assertEquals(100, map.physical(10));
        assertEquals(119, map.physical(29));
        assertEquals(0, map.physical(50));
        assertEquals(9, map.physical(59));
        assertFalse(map.inGap(10));
    }

    @Test
    void gapsHoldTheLastFrame() {
        assertEquals(100, map.physical(0));
        assertTrue(map.inGap(0));
        assertEquals(119, map.physical(30));
        assertEquals(119, map.physical(49));
        assertTrue(map.inGap(30));
        assertEquals(9, map.physical(60));
        assertEquals(9, map.physical(10_000));
        assertEquals(60, map.end());
    }

    @Test
    void overlapCutsTheEarlierSegment() {
        TimeMap overlapped = TimeMap.of(List.of(new Segment(0, 30, 0), new Segment(20, 10, 500)));
        assertEquals(19, overlapped.physical(19));
        assertEquals(500, overlapped.physical(20));
    }

    @Test
    void emptyMapIsIdentity() {
        assertTrue(TimeMap.EMPTY.isEmpty());
        assertEquals(42, TimeMap.EMPTY.physical(42));
        assertEquals(0, TimeMap.EMPTY.end());
    }
}
