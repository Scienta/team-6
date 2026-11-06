package no.scienta.cfp;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The venue (scheduling R1): three rooms and the same five slots in each. Fixed; nobody can change them. Times are
 * written as on the signs, HH:mm on a 24-hour clock, and kept as strings so no formatter decides them (D1702).
 */
final class Programme {

    static final List<String> ROOMS = Collections.unmodifiableList(Arrays.asList("R1", "R2", "R3"));

    /** Slot → {start, end}, in programme order. */
    static final Map<String, String[]> SLOTS;

    /** The only slot long enough for a workshop (scheduling R5). */
    static final String LONG_SLOT = "S4";

    static {
        Map<String, String[]> slots = new LinkedHashMap<>();
        slots.put("S1", new String[] {"09:00", "10:00"});
        slots.put("S2", new String[] {"10:15", "11:15"});
        slots.put("S3", new String[] {"11:30", "12:30"});
        slots.put("S4", new String[] {"13:30", "15:30"});
        slots.put("S5", new String[] {"15:45", "16:45"});
        SLOTS = Collections.unmodifiableMap(slots);
    }

    private Programme() {
    }

    static boolean exists(String room, String slot) {
        return ROOMS.contains(room) && SLOTS.containsKey(slot);
    }

    /** The Entry as the API shows it: room, slot, start, end, talkId, in that order. */
    static Map<String, Object> entry(String room, String slot, long talkId) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("room", room);
        e.put("slot", slot);
        e.put("start", SLOTS.get(slot)[0]);
        e.put("end", SLOTS.get(slot)[1]);
        e.put("talkId", talkId);
        return e;
    }
}
