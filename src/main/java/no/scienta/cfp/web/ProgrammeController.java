package no.scienta.cfp.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import no.scienta.cfp.service.ConferenceService;

/** Programme scheduling (workflow/specs/scheduling.md). Path variables named explicitly, as in TalkController. */
@RestController
public class ProgrammeController {

    private final ConferenceService service;

    public ProgrammeController(ConferenceService service) {
        this.service = service;
    }

    @PutMapping("/programme/{room}/{slot}")
    public Map<String, Object> schedule(@PathVariable("room") String room, @PathVariable("slot") String slot,
                                        @RequestBody(required = false) Map<String, Object> body) {
        return service.schedule(room, slot, body);
    }

    @GetMapping("/programme/{room}/{slot}")
    public Map<String, Object> get(@PathVariable("room") String room, @PathVariable("slot") String slot) {
        return service.programmeEntry(room, slot);
    }

    @DeleteMapping("/programme/{room}/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unschedule(@PathVariable("room") String room, @PathVariable("slot") String slot) {
        service.unschedule(room, slot);
    }
}
