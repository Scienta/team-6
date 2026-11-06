package no.scienta.cfp.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A talk as the API returns it. Also used internally all over the place.
 */
public class TalkDto {

    public Long id;
    public String title;
    public String format;
    public String level;
    public Long speakerId;
    public Integer durationMinutes;
    public String status;
    // The legacy reporting tools read these two as plain strings (Talks spec). The formats are explicit so they don't
    // depend on the JSON library's defaults: always milliseconds, and the offset written +00:00, never Z.
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'", timezone = "UTC")
    public Date submittedAt;
    // The UTC day of submittedAt, yyyy-MM-dd, computed as a string (Utils.utcDay). A java.sql.Date here is midnight in
    // the JVM's default zone, which shifts the day as soon as anything formats it in UTC.
    public String submittedOn;
    public BigDecimal averageRating;  // scale 2, written as 4.00 (ratings-cr PO-2)
    public int ratingCount;
    public List<Long> coSpeakerIds = new ArrayList<>();
}
