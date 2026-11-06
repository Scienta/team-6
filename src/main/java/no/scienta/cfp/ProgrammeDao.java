package no.scienta.cfp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** The programme: the venue's 15 fixed places (room, slot), each empty or holding one talk. */
@Repository
public class ProgrammeDao {

    private final JdbcTemplate jdbc;

    public ProgrammeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Locks every room's place in the slot until the transaction ends, so concurrent PUTs into one slot run one at a
     * time. Returns room → talk id for the places that hold a talk.
     */
    public Map<String, Long> lockSlot(String slot) {
        Map<String, Long> byRoom = new HashMap<>();
        jdbc.query("SELECT room, talk_id FROM programme_entry WHERE slot = ? ORDER BY room FOR UPDATE",
                (RowCallbackHandler) rs -> {
                    long talkId = rs.getLong("talk_id");
                    if (!rs.wasNull()) {
                        byRoom.put(rs.getString("room"), talkId);
                    }
                }, slot);
        return byRoom;
    }

    /** The talk in this place, or null when it's empty. */
    public Long find(String room, String slot) {
        List<Long> rows = jdbc.queryForList("SELECT talk_id FROM programme_entry WHERE room = ? AND slot = ? AND talk_id IS NOT NULL",
                Long.class, room, slot);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Whether the talk is anywhere on the programme. */
    public boolean isScheduled(long talkId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM programme_entry WHERE talk_id = ?)",
                Boolean.class, talkId));
    }

    /**
     * Puts the talk in the place, only if it's empty: a conditional write, so R6 holds even if the slot lock is ever
     * lost. Returns how many rows changed: 0 when the place was taken.
     */
    public int place(String room, String slot, long talkId) {
        return jdbc.update("UPDATE programme_entry SET talk_id = ? WHERE room = ? AND slot = ? AND talk_id IS NULL", talkId, room, slot);
    }

    /** Empties the place. Returns how many rows changed: 0 when it was already empty. */
    public int clear(String room, String slot) {
        return jdbc.update("UPDATE programme_entry SET talk_id = NULL WHERE room = ? AND slot = ? AND talk_id IS NOT NULL", room, slot);
    }

    /** Every speaker of the talks, main and co-speakers, in one query. */
    public Set<Long> speakersOf(List<Long> talkIds) {
        Set<Long> speakers = new HashSet<>();
        if (talkIds.isEmpty()) {
            return speakers;
        }
        Object[] ids = talkIds.toArray();
        jdbc.query("SELECT speaker_id FROM talk WHERE id = ANY(?) UNION SELECT speaker_id FROM talk_co_speaker WHERE talk_id = ANY(?)",
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids));
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", ids));
                },
                (RowCallbackHandler) rs -> speakers.add(rs.getLong("speaker_id")));
        return speakers;
    }
}
