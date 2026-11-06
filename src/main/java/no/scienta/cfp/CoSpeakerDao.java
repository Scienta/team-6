package no.scienta.cfp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class CoSpeakerDao {

    private final JdbcTemplate jdbc;

    public CoSpeakerDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The talk's co-speakers, in the order they were added. */
    public List<Long> findByTalk(long talkId) {
        return jdbc.queryForList("SELECT speaker_id FROM talk_co_speaker WHERE talk_id = ? ORDER BY id", Long.class, talkId);
    }

    /** Co-speakers for many talks in one query, each list in the order they were added. Talks without any are missing. */
    public Map<Long, List<Long>> findByTalks(List<Long> talkIds) {
        Map<Long, List<Long>> byTalk = new HashMap<>();
        jdbc.query("SELECT talk_id, speaker_id FROM talk_co_speaker WHERE talk_id = ANY(?) ORDER BY id",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", talkIds.toArray())),
                (RowCallbackHandler) rs -> byTalk.computeIfAbsent(rs.getLong("talk_id"), k -> new ArrayList<>())
                        .add(rs.getLong("speaker_id")));
        return byTalk;
    }

    /** Locks the talk row so concurrent adds to the same talk run one at a time. Returns the main speaker, or null. */
    public Long lockTalk(long talkId) {
        List<Long> rows = jdbc.queryForList("SELECT speaker_id FROM talk WHERE id = ? FOR UPDATE", Long.class, talkId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void add(long talkId, long speakerId) {
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?) ON CONFLICT (talk_id, speaker_id) DO NOTHING",
                talkId, speakerId);
    }

    /** Returns how many rows were removed: 0 when the speaker wasn't a co-speaker. */
    public int remove(long talkId, long speakerId) {
        return jdbc.update("DELETE FROM talk_co_speaker WHERE talk_id = ? AND speaker_id = ?", talkId, speakerId);
    }
}
