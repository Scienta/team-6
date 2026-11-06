package no.scienta.cfp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class RatingDao {

    private final JdbcTemplate jdbc;

    public RatingDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert or replace the reviewer's rating. One statement, so two clicks at once can't make two rows.
     */
    public void save(long talkId, long reviewerId, int score, String ratedAt) {
        jdbc.update("INSERT INTO rating (talk_id, reviewer_id, score, rated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (talk_id, reviewer_id) DO UPDATE SET score = excluded.score, rated_at = excluded.rated_at",
                talkId, reviewerId, score, ratedAt);
    }

    /**
     * {count, total, max, min} per talk, for many talks in one query (max and min for the ratings-cr trim). Talks nobody
     * has rated are missing from the map.
     */
    public Map<Long, long[]> statsByTalk(List<Long> talkIds) {
        Map<Long, long[]> stats = new HashMap<>();
        jdbc.query("SELECT talk_id, COUNT(*) AS cnt, SUM(score) AS total, MAX(score) AS hi, MIN(score) AS lo "
                        + "FROM rating WHERE talk_id = ANY(?) GROUP BY talk_id",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", talkIds.toArray())),
                (RowCallbackHandler) rs -> stats.put(rs.getLong("talk_id"), new long[] {rs.getLong("cnt"), rs.getLong("total"), rs.getLong("hi"), rs.getLong("lo")}));
        return stats;
    }

    /** How many different reviewers have rated the talk. */
    public int reviewerCount(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT reviewer_id) FROM rating WHERE talk_id = ?", Integer.class, talkId);
        return n == null ? 0 : n;
    }

    // public double average(long talkId) {
    //     return jdbc.queryForObject("SELECT AVG(score) FROM rating WHERE talk_id = ?", Double.class, talkId);
    // }
}
