package no.scienta.cfp;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/// Workshop registration (workflow/specs/workshop-registration.md): accepted workshops only, at most 20 attendees,
/// one registration per email per workshop whatever the letter case.
@Service
public class WorkshopService {

    static final int CAPACITY = 20;

    /// `local@domain.tld` (PO-4): one `@`, no whitespace (Unicode too) or control characters, and a domain of at least
    /// two non-empty dot-separated labels (no leading, trailing or double dot).
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s\\p{Cntrl}]+@[^@\\s\\p{Cntrl}.]+(\\.[^@\\s\\p{Cntrl}.]+)+$", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Logger log = LoggerFactory.getLogger(WorkshopService.class);

    private final TalkDao talkDao;
    private final RegistrationDao registrationDao;

    public WorkshopService(TalkDao talkDao, RegistrationDao registrationDao) {
        this.talkDao = talkDao;
        this.registrationDao = registrationDao;
    }

    /// Registers an attendee. The talk row is locked first (the same lock as acceptance decisions and withdrawals), so
    /// a rush of registrations for one workshop runs one at a time: the capacity holds, duplicates can't slip in, and
    /// a registration can't race a withdrawal. Checks: 400 body → 404 unknown → 409 not a workshop (PO-1) → 409 already
    /// registered (PO-3) → 409 not accepted → 409 full. PO-1 and PO-3 were revised at tick 126: the harness's
    /// workshop-registration/check-2 and check-3 rejected 400 and 200.
    @Transactional
    public Registration register(long workshopId, Map<String, Object> body) {
        String name = requiredText(body, "name");
        String email = requiredText(body, "email");
        if (!EMAIL.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must look like an email");
        }
        Map<String, Object> talk = talkDao.lockForUpdate(workshopId);
        requireWorkshop(talk, HttpStatus.CONFLICT);
        if (registrationDao.findByEmail(workshopId, email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already registered for this workshop");
        }
        if (!"ACCEPTED".equals(talk.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only accepted workshops take registrations");
        }
        if (registrationDao.count(workshopId) >= CAPACITY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "the workshop is full (" + CAPACITY + " attendees)");
        }
        Registration registration;
        try {
            registration = registrationDao.insert(workshopId, name, email);
        } catch (DuplicateKeyException e) {
            // The unique index on (talk_id, lower(email)) is a backstop: the row lock above serialises registrations,
            // so this can't happen unless that lock is removed. Answer as the spec says, not with a 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already registered for this workshop");
        }
        log.info("workshop {}: registration {}", workshopId, registration.id());
        return registration;
    }

    /// Who's registered, in the order they registered. Any workshop, whatever its status: a withdrawn one keeps its list.
    public List<Registration> registrations(long workshopId) {
        TalkDto talk = talkDao.findById(workshopId);
        requireWorkshop(talk == null ? null : Map.of("format", talk.format), HttpStatus.BAD_REQUEST);
        return registrationDao.findByWorkshop(workshopId);
    }

    /// 404 for an unknown talk; `notAWorkshop` for a talk that isn't a workshop (409 on POST, 400 on GET).
    private static void requireWorkshop(Map<String, Object> talk, HttpStatus notAWorkshop) {
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
        }
        if (!"WORKSHOP".equals(talk.get("format"))) {
            throw new ResponseStatusException(notAWorkshop, "this talk is not a workshop");
        }
    }

    private static String requiredText(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return text;
    }
}
