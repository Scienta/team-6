package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkshopController {

    private final WorkshopService service;

    public WorkshopController(WorkshopService service) {
        this.service = service;
    }

    /// 201 with the new registration, or 200 with the one this email already has (PO-3).
    @PostMapping("/workshops/{id}/registrations")
    public ResponseEntity<Registration> register(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        WorkshopService.Result result = service.register(id, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.registration());
    }

    @GetMapping("/workshops/{id}/registrations")
    public List<Registration> registrations(@PathVariable("id") Long id) {
        return service.registrations(id);
    }
}
