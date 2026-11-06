package no.scienta.cfp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Speakers, talks and ratings.
 *
 * TODO split this up (CFP-140)
 */
@Service
public class ConferenceService {

    private static final Logger log = LoggerFactory.getLogger(ConferenceService.class);

    private static final Map<String, Integer> DURATIONS = new HashMap<>();

    static {
        DURATIONS.put("LIGHTNING_10", 10);
        DURATIONS.put("LIGHTNING_20", 20);
        DURATIONS.put("PRESENTATION", 45);
        DURATIONS.put("WORKSHOP", 120);
        // DURATIONS.put("KEYNOTE", 60);  // keynotes are invited, not submitted (2021)
    }

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final CoSpeakerDao coSpeakerDao;
    private final NotificationService notifications;

    public ConferenceService(SpeakerDao speakerDao, TalkDao talkDao, RatingDao ratingDao,
                             CoSpeakerDao coSpeakerDao, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.coSpeakerDao = coSpeakerDao;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- speakers

    @Transactional
    public Map<String, Object> registerSpeaker(SpeakerForm form) {
        if (speakerDao.emailTaken(form.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        long id;
        try {
            id = speakerDao.insert(form.getName(), form.getEmail(), form.getBio());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        log.info("registered speaker " + id + " (" + form.getEmail() + ")");
        notifications.speakerRegistered(form.getEmail(), form.getName());
        Map<String, Object> speaker = new LinkedHashMap<>();
        speaker.put("id", id);
        speaker.put("name", form.getName());
        speaker.put("email", form.getEmail());
        speaker.put("bio", form.getBio());
        return speaker;
    }

    public Map<String, Object> getSpeaker(long id) {
        Map<String, Object> speaker = speakerDao.findById(id);
        if (speaker == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        return speaker;
    }

    @Transactional
    public Map<String, Object> updateSpeaker(long id, String name, String email, String bio) {
        Map<String, Object> existing = getSpeaker(id);
        if (name == null || name.equals("")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (!Utils.isValidEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        if (bio != null && bio.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bio too long");
        }
        if (!email.equalsIgnoreCase((String) existing.get("email")) && speakerDao.emailTaken(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        speakerDao.update(id, name, email, bio);
        return getSpeaker(id);
    }

    public List<Map<String, Object>> searchSpeakers(String q) {
        return speakerDao.search(q);
    }

    // ---------------------------------------------------------------- talks

    @Transactional
    public TalkDto submitTalk(TalkForm form) {
        if (form.title == null || form.title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (form.format == null || !DURATIONS.containsKey(form.format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be one of " + DURATIONS.keySet());
        }
        if (form.level == null || !Arrays.asList("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(form.level)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown level");
        }
        if (form.speakerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId is required");
        }
        if (!speakerDao.exists(form.speakerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speaker " + form.speakerId + " does not exist");
        }

        int duration;
        switch (form.format) {
            case "LIGHTNING_10":
                duration = 10;
                break;
            case "LIGHTNING_20":
                duration = 20;
                break;
            case "WORKSHOP":
                duration = 120;
                break;
            default:
                duration = 45;
        }

        Long id = checkSubmissionLimit(form, duration);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a speaker can submit at most 3 talks");
        }
        Map<String, Object> speaker = speakerDao.findById(form.speakerId);
        notifications.talkSubmitted((String) speaker.get("email"), form.title);
        return getTalk(id);
    }

    /**
     * Checks that the speaker is below the limit of 3 talks.
     */
    private Long checkSubmissionLimit(TalkForm form, int duration) {
        return talkDao.insertIfBelow(3, form.title, form.format, duration, form.level, form.speakerId,
                System.currentTimeMillis());
    }

    public TalkDto getTalk(long id) {
        TalkDto talk = talkDao.findById(id);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        enrich(talk);
        return talk;
    }

    public List<TalkDto> getTalksForSpeaker(long speakerId) {
        if (speakerDao.findById(speakerId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        List<TalkDto> talks = talkDao.findBySpeaker(speakerId);
        for (TalkDto t : talks) {
            enrich(t);
        }
        return talks;
    }

    public List<TalkDto> listTalks(String sort, Long speakerId) {
        if (sort != null && !sort.equals("rating")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be 'rating'");
        }
        List<TalkDto> talks = speakerId == null ? talkDao.findAll() : talkDao.findBySpeaker(speakerId);
        for (TalkDto t : talks) {
            enrich(t);
        }
        if ("rating".equals(sort)) {
            List<TalkDto> rated = new ArrayList<>();
            List<TalkDto> unrated = new ArrayList<>();
            for (TalkDto t : talks) {
                if (t.ratingCount > 0) {
                    rated.add(t);
                } else {
                    unrated.add(t);
                }
            }
            rated.sort((a, b) -> {
                int c = Double.compare(b.averageRating, a.averageRating);
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(b.ratingCount, a.ratingCount);
                if (c != 0) {
                    return c;
                }
                return Long.compare(a.id, b.id);
            });
            rated.addAll(unrated);
            return rated;
        }
        return talks;
    }

    /** Fills in what other features add to every talk shown: ratings and co-speakers. */
    private void enrich(TalkDto talk) {
        talk.coSpeakerIds = coSpeakerDao.findByTalk(talk.id);
        Map<String, Object> stats = ratingDao.stats(talk.id);
        int count = ((Number) stats.get("cnt")).intValue();
        long total = ((Number) stats.get("total")).longValue();
        talk.ratingCount = count;
        talk.averageRating = count == 0 ? null : calculateAverage(total, count);
    }

    // 1 decimal, half up (4.25 -> 4.3)
    private Double calculateAverage(long total, int count) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
        // return Utils.roundRating((double) total / count);
    }

    // ---------------------------------------------------------------- co-speakers

    /**
     * Adds a co-speaker (at most 2, never the main speaker; adding one twice changes nothing). The talk row is locked
     * first, so concurrent adds to the same talk can't both see room for one more.
     */
    @Transactional
    public TalkDto addCoSpeaker(long talkId, long speakerId) {
        Long mainSpeaker = coSpeakerDao.lockTalk(talkId);
        if (mainSpeaker == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        if (!speakerDao.exists(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
        }
        if (mainSpeaker == speakerId) {
            // The main speaker is never a co-speaker of their own talk; asking for it changes nothing (PO-2).
            return getTalk(talkId);
        }
        List<Long> current = coSpeakerDao.findByTalk(talkId);
        if (!current.contains(speakerId)) {
            if (current.size() >= 2) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "a talk has at most 2 co-speakers");
            }
            coSpeakerDao.add(talkId, speakerId);
        }
        return getTalk(talkId);
    }

    /** Removes a co-speaker. Removing someone who isn't one is a 404 and changes nothing (co-speakers PO-1). */
    @Transactional
    public TalkDto removeCoSpeaker(long talkId, long speakerId) {
        if (coSpeakerDao.lockTalk(talkId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        if (coSpeakerDao.remove(talkId, speakerId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker " + speakerId + " is not a co-speaker of this talk");
        }
        return getTalk(talkId);
    }

    // ---------------------------------------------------------------- acceptance

    private static final Set<String> DECISIONS = set("ACCEPTED", "REJECTED", "WAITLISTED");

    /** The committee's decisions allowed from each status (workflow/specs/acceptance.md). */
    private static final Map<String, Set<String>> ALLOWED_DECISIONS = new HashMap<>();

    /** The statuses a speaker can withdraw from. */
    private static final Set<String> WITHDRAWABLE = set("SUBMITTED", "WAITLISTED", "ACCEPTED");

    static {
        ALLOWED_DECISIONS.put("SUBMITTED", DECISIONS);
        ALLOWED_DECISIONS.put("WAITLISTED", set("ACCEPTED", "REJECTED"));
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /**
     * The committee decides on a talk. The talk row is locked first, so concurrent decisions run one after another
     * and each sees the last one's result. Repeating the decision the talk already has changes nothing (PO-1).
     */
    @Transactional
    public TalkDto decide(long talkId, Map<String, Object> body) {
        Object value = body == null ? null : body.get("status");
        if (!(value instanceof String) || !DECISIONS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + DECISIONS);
        }
        String decision = (String) value;
        Map<String, Object> talk = talkDao.lockForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        String current = (String) talk.get("status");
        if (current.equals(decision)) {
            return getTalk(talkId);
        }
        if (!ALLOWED_DECISIONS.getOrDefault(current, Collections.emptySet()).contains(decision)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + current + " talk can't be " + decision);
        }
        if (decision.equals("ACCEPTED") && ratingDao.reviewerCount(talkId) < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a talk needs ratings from at least 2 reviewers to be accepted");
        }
        talkDao.updateStatus(talkId, decision);
        log.info("talk " + talkId + ": " + current + " -> " + decision);
        return getTalk(talkId);
    }

    /** The talk's own speaker withdraws it, unless it is rejected or already withdrawn. */
    @Transactional
    public TalkDto withdraw(long talkId, Map<String, Object> body) {
        Object value = body == null ? null : body.get("speakerId");
        boolean whole = value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger;
        if (!whole || ((Number) value).doubleValue() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId must be a positive whole number");
        }
        Map<String, Object> talk = talkDao.lockForUpdate(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        // A number too big for a speaker id is nobody's id: someone else (PO-3), not a bad request.
        if (value instanceof java.math.BigInteger || ((Number) talk.get("speaker_id")).longValue() != ((Number) value).longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "only the talk's speaker can withdraw it");
        }
        String current = (String) talk.get("status");
        if (!WITHDRAWABLE.contains(current)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "a " + current + " talk can't be withdrawn");
        }
        talkDao.updateStatus(talkId, "WITHDRAWN");
        log.info("talk " + talkId + ": " + current + " -> WITHDRAWN");
        return getTalk(talkId);
    }

    // ---------------------------------------------------------------- statistics

    public Map<String, Object> statistics() {
        List<TalkDto> talks = listTalks(null, null);
        Map<String, Integer> byFormat = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        List<Long> speakersWithTalks = new ArrayList<>();
        int minutes = 0;
        double ratingSum = 0;
        int rated = 0;
        for (TalkDto t : talks) {
            byFormat.put(t.format, byFormat.getOrDefault(t.format, 0) + 1);
            byLevel.put(t.level, byLevel.getOrDefault(t.level, 0) + 1);
            byStatus.put(t.status, byStatus.getOrDefault(t.status, 0) + 1);
            if (!speakersWithTalks.contains(t.speakerId) && speakerDao.findById(t.speakerId) != null) {
                speakersWithTalks.add(t.speakerId);
            }
            minutes += t.durationMinutes;
            if (t.averageRating != null) {
                ratingSum += t.averageRating;
                rated++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("speakers", speakerDao.count());
        stats.put("speakersWithTalks", speakersWithTalks.size());
        stats.put("talks", talks.size());
        stats.put("byFormat", byFormat);
        stats.put("byLevel", byLevel);
        stats.put("byStatus", byStatus);
        stats.put("totalHours", minutes / 60);
        stats.put("averageRating", rated == 0 ? null : Utils.roundRating(ratingSum / rated));
        stats.put("generated", Utils.now());
        return stats;
    }

    // ---------------------------------------------------------------- ratings

    @Transactional
    public Map<String, Object> rate(long talkId, long reviewerId, Map<String, Object> body) {
        if (reviewerId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewerId must be positive");
        }
        Object score = body.get("score");
        if (!(score instanceof Integer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be a whole number");
        }
        int s = (Integer) score;
        if (s < 1 || s > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be 1-5");
        }
        if (!talkDao.exists(talkId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
        }
        String now = Utils.now();
        ratingDao.save(talkId, reviewerId, s, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("talkId", talkId);
        result.put("reviewerId", reviewerId);
        result.put("score", s);
        result.put("ratedAtDisplay", Utils.usShort(Utils.parse(now)));
        return result;
    }
}
