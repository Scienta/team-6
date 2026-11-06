package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import no.scienta.cfp.model.SpeakerForm;

/** Speakers: "A bio is at most 500 characters", counted as Unicode code points (an emoji is 1, not 2). */
class SpeakerFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void bioLimitCountsCharactersNotUtf16Units() {
        assertEquals(0, violations(repeat("a", 500)));
        assertEquals(1, violations(repeat("a", 501)));
        assertEquals(0, violations(repeat("😀", 500)));   // 500 x 😀 = 1000 UTF-16 units
        assertEquals(1, violations(repeat("😀", 501)));
        assertEquals(0, violations(repeat("a", 499) + "😀"));
        assertEquals(0, violations(null));
    }

    private int violations(String bio) {
        SpeakerForm form = new SpeakerForm();
        form.setName("Ada");
        form.setEmail("ada@example.com");
        form.setBio(bio);
        return validator.validate(form).size();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
