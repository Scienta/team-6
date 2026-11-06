package no.scienta.cfp.model;

import java.util.List;

/// One scheduled talk in the programme export, fields in the spec's order (D2005): the talk and its speakers as they are
/// now (D2003), speakers by name with the main speaker first, then co-speakers in the order they were added (D2004).
public record ProgrammeRow(String slot, String start, String end, String room, long talkId, String title, String format,
                           List<String> speakers) {
}
