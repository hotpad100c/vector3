package ml.mypals.vectorthree.core.clips;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps project ticks (the timeline) to replay ticks (the open archive). Each segment plays {@code length} replay ticks
 * starting at {@code physical} from project tick {@code start}. Between and around segments the project has gaps,
 * where the replay holds the last frame it showed (before the first segment, that segment's first frame).
 */
public final class TimeMap {
    public static final TimeMap EMPTY = new TimeMap(List.of());

    public record Segment(int start, int length, int physical) {
        public int end() {
            return start + length;
        }
    }

    private final List<Segment> segments;

    private TimeMap(List<Segment> segments) {
        this.segments = segments;
    }

    /** Segments in any order; empty ones are dropped and a segment overlapped by the next one is cut short. */
    public static TimeMap of(List<Segment> raw) {
        List<Segment> sorted = new ArrayList<>(raw.stream().filter(segment -> segment.length() > 0).toList());
        sorted.sort(Comparator.comparingInt(Segment::start));
        List<Segment> result = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            Segment segment = sorted.get(i);
            int length = i + 1 < sorted.size() ? Math.min(segment.length(), sorted.get(i + 1).start() - segment.start()) : segment.length();
            if (length > 0) result.add(new Segment(segment.start(), length, segment.physical()));
        }
        return new TimeMap(List.copyOf(result));
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /** The tick after the last segment ends. */
    public int end() {
        return segments.isEmpty() ? 0 : segments.getLast().end();
    }

    private int indexAtOrBefore(int project) {
        int low = 0, high = segments.size() - 1, found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (segments.get(middle).start() <= project) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }

    public boolean inGap(int project) {
        int index = indexAtOrBefore(project);
        return index < 0 || project >= segments.get(index).end();
    }

    /** The replay tick shown at project tick {@code project}. */
    public int physical(int project) {
        if (segments.isEmpty()) return project;
        int index = indexAtOrBefore(project);
        if (index < 0) return segments.getFirst().physical();
        Segment segment = segments.get(index);
        if (project < segment.end()) return segment.physical() + project - segment.start();
        return segment.physical() + segment.length() - 1;
    }
}
