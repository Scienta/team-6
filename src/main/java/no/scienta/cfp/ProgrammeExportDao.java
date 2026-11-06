package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/// Reads the whole programme in one query (D2082): every filled place, its talk, the main speaker's name and the
/// co-speakers' names in the order they were added.
@Repository
public class ProgrammeExportDao {

    private static final String SQL = """
            SELECT e.slot, e.room, t.id, t.title, t.format, s.name AS main_speaker,
                   COALESCE((SELECT array_agg(cs.name ORDER BY c.id)
                             FROM talk_co_speaker c JOIN speaker cs ON cs.id = c.speaker_id
                             WHERE c.talk_id = t.id), '{}') AS co_speakers
            FROM programme_entry e
            JOIN talk t ON t.id = e.talk_id
            JOIN speaker s ON s.id = t.speaker_id
            """;

    private final JdbcTemplate jdbc;

    public ProgrammeExportDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /// The rows in no particular order; the service sorts them (D2002).
    public List<ProgrammeRow> rows() {
        return jdbc.query(SQL, (rs, rowNum) -> {
            String slot = rs.getString("slot");
            List<String> speakers = new ArrayList<>();
            speakers.add(rs.getString("main_speaker"));
            speakers.addAll(Arrays.asList((String[]) rs.getArray("co_speakers").getArray()));
            return new ProgrammeRow(slot, Programme.SLOTS.get(slot)[0], Programme.SLOTS.get(slot)[1], rs.getString("room"),
                    rs.getLong("id"), rs.getString("title"), rs.getString("format"), List.copyOf(speakers));
        });
    }
}
