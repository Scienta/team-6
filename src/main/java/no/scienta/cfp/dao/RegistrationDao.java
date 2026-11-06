package no.scienta.cfp.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import no.scienta.cfp.model.Registration;

/// Workshop registrations, in the order they were made (id order).
@Repository
public class RegistrationDao {

    private static final RowMapper<Registration> MAPPER = (rs, rowNum) ->
            new Registration(rs.getLong("id"), rs.getLong("talk_id"), rs.getString("name"), rs.getString("email"));

    private final JdbcTemplate jdbc;

    public RegistrationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Registration insert(long workshopId, String name, String email) {
        long id = jdbc.queryForObject(
                "INSERT INTO workshop_registration (talk_id, name, email) VALUES (?, ?, ?) RETURNING id",
                Long.class, workshopId, name, email);
        return new Registration(id, workshopId, name, email);
    }

    public int count(long workshopId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM workshop_registration WHERE talk_id = ?", Integer.class, workshopId);
    }

    /// The registration with this email for this workshop, whatever the letter case.
    public Optional<Registration> findByEmail(long workshopId, String email) {
        return jdbc.query("SELECT id, talk_id, name, email FROM workshop_registration WHERE talk_id = ? AND lower(email) = lower(?)",
                MAPPER, workshopId, email).stream().findFirst();
    }

    public List<Registration> findByWorkshop(long workshopId) {
        return jdbc.query("SELECT id, talk_id, name, email FROM workshop_registration WHERE talk_id = ? ORDER BY id",
                MAPPER, workshopId);
    }
}
