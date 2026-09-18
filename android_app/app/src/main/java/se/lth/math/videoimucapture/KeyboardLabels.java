package se.lth.math.videoimucapture;

import java.util.ArrayList;
import java.util.List;

/** Recording-local timeline, owned by the TXT writer thread. */
final class KeyboardLabels {
    static final long WINDOW_NS = 200_000_000L;
    static final class Event {
        final long timeNs;
        final String label;
        final boolean enabled;
        long id;
        Event(long timeNs, String label, boolean enabled) {
            this.timeNs = timeNs;
            this.label = label;
            this.enabled = enabled;
        }
    }
    private final List<Event> events = new ArrayList<>();
    private long nextId;

    void add(Event event) {
        event.id = event.label.isEmpty() ? -1 : ++nextId;
        events.add(event);
    }

    Event at(long timeNs) {
        int low = 0, high = events.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (events.get(mid).timeNs <= timeNs) low = mid + 1;
            else high = mid;
        }
        if (low == 0) return null;
        Event event = events.get(low - 1);
        return event.enabled && !event.label.isEmpty()
                && timeNs - event.timeNs < WINDOW_NS ? event : null;
    }
}
