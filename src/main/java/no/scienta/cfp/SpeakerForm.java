package no.scienta.cfp;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Request body for POST /speakers.
 */
public class SpeakerForm {

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    // At most 500 characters, counted as Unicode code points: @Size counts UTF-16 units, so an emoji (😀) counted 2
    // and 500 emojis were refused. See isBioAtMost500Characters.
    private String bio;

    @JsonIgnore
    @AssertTrue(message = "bio is at most 500 characters")
    public boolean isBioAtMost500Characters() {
        return bio == null || bio.codePointCount(0, bio.length()) <= 500;
    }

    // TODO twitter handle, see CFP-112
    // private String twitter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
