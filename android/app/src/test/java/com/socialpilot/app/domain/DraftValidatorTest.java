package com.socialpilot.app.domain;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class DraftValidatorTest {
    @Test public void trimsTopic() { assertEquals("Topic", DraftValidator.normalize("  Topic  ")); }
    @Test(expected = IllegalArgumentException.class) public void rejectsBlank() { DraftValidator.normalize("  "); }
    @Test(expected = IllegalArgumentException.class) public void rejectsNull() { DraftValidator.normalize(null); }
}
