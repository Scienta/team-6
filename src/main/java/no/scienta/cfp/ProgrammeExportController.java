package no.scienta.cfp;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgrammeExportController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ProgrammeExportService service;

    public ProgrammeExportController(ProgrammeExportService service) {
        this.service = service;
    }

    /// The programme as JSON. `406` when the client won't take JSON, e.g. `Accept: text/csv` (PO-3).
    @GetMapping(value = "/programme", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ProgrammeRow> programme() {
        return service.rows();
    }

    /// The programme as CSV, `text/csv;charset=UTF-8` whatever the client asks for (D2006, PO-2), as a download
    /// (PO-4). No `produces =`: that would make Spring answer `Accept: application/json` with `406` (D2080).
    @GetMapping("/programme.csv")
    public ResponseEntity<String> programmeCsv() {
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=programme.csv")
                .body(service.csv());
    }
}
