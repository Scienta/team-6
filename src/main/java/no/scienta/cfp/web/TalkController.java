package no.scienta.cfp.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import no.scienta.cfp.model.TalkDto;
import no.scienta.cfp.model.TalkForm;
import no.scienta.cfp.service.ConferenceService;

@RestController
public class TalkController {

    private final ConferenceService service;

    public TalkController(ConferenceService service) {
        this.service = service;
    }

    @PostMapping("/talks")
    @ResponseStatus(HttpStatus.CREATED)
    public TalkDto submit(@RequestBody TalkForm form) {
        return service.submitTalk(form);
    }

    @GetMapping("/talks/{id}")
    public TalkDto get(@PathVariable Long id) {
        return service.getTalk(id);
    }

    @GetMapping("/talks")
    public List<TalkDto> list(@RequestParam(required = false) String sort,
                              @RequestParam(required = false) Long speakerId) {
        return service.listTalks(sort, speakerId);
    }

    @PutMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto addCoSpeaker(@PathVariable Long id, @PathVariable Long speakerId) {
        return service.addCoSpeaker(id, speakerId);
    }

    @DeleteMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto removeCoSpeaker(@PathVariable Long id, @PathVariable Long speakerId) {
        return service.removeCoSpeaker(id, speakerId);
    }

    // Path variables named explicitly: Spring 6+ (Boot 3/4) can't infer names without the -parameters compiler flag.
    @PutMapping("/talks/{id}/status")
    public TalkDto decide(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.decide(id, body);
    }

    @PostMapping("/talks/{id}/withdraw")
    public TalkDto withdraw(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        return service.withdraw(id, body);
    }

    @PutMapping("/talks/{id}/ratings/{reviewerId}")
    public Map<String, Object> rate(@PathVariable Long id, @PathVariable Long reviewerId,
                                    @RequestBody Map<String, Object> body) {
        return service.rate(id, reviewerId, body);
    }
}
