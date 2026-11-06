package no.scienta.cfp.model;

/// A workshop registration as the API shows it: the email as the attendee typed it.
public record Registration(long id, long workshopId, String name, String email) {
}
