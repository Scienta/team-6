package no.scienta.cfp.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import no.scienta.cfp.dao.ProgrammeExportDao;
import no.scienta.cfp.model.Programme;
import no.scienta.cfp.model.ProgrammeRow;

/// The programme export (workflow/specs/programme-export.md, DESIGN.md D2000–D2086): one list of rows, written as JSON
/// or as CSV, so both views always have the same rows in the same order (D2081).
@Service
public class ProgrammeExportService {

    static final String CSV_HEADER = "slot,start,end,room,talkId,title,format,speakers";

    private static final List<String> SLOT_ORDER = List.copyOf(Programme.SLOTS.keySet());

    /// Earliest start first (slot order is start order), then by room R1, R2, R3 (D2002).
    private static final Comparator<ProgrammeRow> DAY_ORDER = Comparator
            .comparingInt((ProgrammeRow r) -> SLOT_ORDER.indexOf(r.slot()))
            .thenComparingInt(r -> Programme.ROOMS.indexOf(r.room()));

    private final ProgrammeExportDao dao;

    public ProgrammeExportService(ProgrammeExportDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public List<ProgrammeRow> rows() {
        List<ProgrammeRow> rows = new ArrayList<>(dao.rows());
        rows.sort(DAY_ORDER);
        return rows;
    }

    /// RFC 4180 with Unix line endings (D2007–D2010): the header, one line per row, every line ending in `\n`. Its own
    /// read-only transaction: `rows()` is called from inside this class, so its annotation doesn't apply here.
    @Transactional(readOnly = true)
    public String csv() {
        StringBuilder out = new StringBuilder(CSV_HEADER).append('\n');
        for (ProgrammeRow r : rows()) {
            List<String> fields = List.of(r.slot(), r.start(), r.end(), r.room(), String.valueOf(r.talkId()), r.title(),
                    r.format(), String.join("; ", r.speakers()));
            out.append(String.join(",", fields.stream().map(ProgrammeExportService::field).toList())).append('\n');
        }
        return out.toString();
    }

    /// Quoted only when it has to be (a comma, a quote or a line break), with inner quotes doubled; otherwise as it is.
    static String field(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
