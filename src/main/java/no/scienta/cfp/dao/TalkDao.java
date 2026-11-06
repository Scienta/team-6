package no.scienta.cfp.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import no.scienta.cfp.model.TalkDto;
import no.scienta.cfp.util.Utils;

@Repository
public class TalkDao {

    private static final String COLUMNS = "id, title, format, duration, level, status, speaker_id, submitted_at";

    private final JdbcTemplate jdbc;

    private final RowMapper<TalkDto> mapper = (rs, rowNum) -> {
        TalkDto t = new TalkDto();
        t.id = rs.getLong("id");
        t.title = rs.getString("title");
        t.format = rs.getString("format");
        t.durationMinutes = rs.getInt("duration");
        t.level = rs.getString("level");
        t.status = rs.getString("status");
        t.speakerId = rs.getLong("speaker_id");
        long millis = rs.getLong("submitted_at");
        t.submittedAt = new Date(millis);
        t.submittedOn = Utils.utcDay(millis);
        return t;
    };

    public TalkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TalkDto findById(long id) {
        List<TalkDto> list = jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<TalkDto> findBySpeaker(long speakerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE speaker_id = ? ORDER BY id", mapper, speakerId);
    }

    public List<TalkDto> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk ORDER BY id", mapper);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM talk WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /**
     * Locks the talk row until the transaction ends, so concurrent status changes run one at a time.
     * Returns {status, speaker_id, format}, or null when there is no such talk.
     */
    public Map<String, Object> lockForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, speaker_id, format FROM talk WHERE id = ? FOR UPDATE", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE talk SET status = ? WHERE id = ?", status, id);
    }

    /**
     * Inserts the talk only if the speaker has fewer than {@code max} talks that count: withdrawn and rejected talks
     * don't (acceptance PO-2). Returns the new id, or null.
     * The speaker row is locked first so two submissions for the same speaker can't both see room under the limit.
     */
    @Transactional
    public Long insertIfBelow(int max, String title, String format, int duration, String level, long speakerId, long submittedAt) {
        lockSpeaker(speakerId);
        List<Long> ids = jdbc.queryForList(
                "INSERT INTO talk (title, format, duration, level, status, speaker_id, submitted_at) "
                        + "SELECT ?, ?, ?, ?, 'SUBMITTED', ?, ? "
                        + "WHERE (" + COUNTING + ") < ? "
                        + "RETURNING id",
                Long.class, title, format, duration, level, speakerId, submittedAt, speakerId, speakerId, max);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * The talks that count towards a speaker's limit (submission-cap-cr, DESIGN.md D1105, D1106): talks not rejected
     * or withdrawn where they are the main speaker or a co-speaker. Parameters: speaker id, speaker id.
     */
    private static final String COUNTING = "SELECT COUNT(*) FROM talk WHERE status NOT IN ('WITHDRAWN', 'REJECTED') "
            + "AND (speaker_id = ? OR id IN (SELECT talk_id FROM talk_co_speaker WHERE speaker_id = ?))";

    public int countingTalks(long speakerId) {
        return jdbc.queryForObject(COUNTING, Integer.class, speakerId, speakerId);
    }

    /**
     * Locks the speaker's row until the transaction ends. Everything that adds to a speaker's count takes this lock
     * (submissions, co-speaker PUTs, D1354), so the limit holds when they race. Lock order: talk row, then speaker.
     */
    public void lockSpeaker(long speakerId) {
        jdbc.queryForList("SELECT id FROM speaker WHERE id = ? FOR UPDATE", speakerId);
    }

    // old version, raced when the mobile app double-posted
    // public long insert(String title, String format, int duration, String level, long speakerId) {
    //     jdbc.update("INSERT INTO talk (title, format, duration, level, status, speaker_id) VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
    //             title, format, duration, level, speakerId);
    //     return jdbc.queryForObject("SELECT currval('talk_id_seq')", Long.class);
    // }
}
