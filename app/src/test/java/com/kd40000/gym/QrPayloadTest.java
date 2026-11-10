package com.kd40000.gym;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.time.Instant;

public class QrPayloadTest {
    @Test public void matchesFixedHmacVector() throws Exception {
        // Deliberately synthetic credentials: do not commit real member data or keys.
        MemberConfig fake = new MemberConfig("1234567", "0", "9999",
            "0123456789abcdef", "test-key-only");
        assertEquals(
            "TAGV5202|1234567|0|9999|2026-01-02T03:04:05|||0123456789abcdef|"
                + "+lKQfZZR0qEtLlHzGqVhGrkIitu7dXCBhOrGDoGKeFg=",
            QrPayload.generate(fake, Instant.parse("2026-01-02T03:04:05Z"))
        );
    }

    @Test public void requiresCompleteConfiguration() {
        assertFalse(new MemberConfig("", "0", "1234", "0123456789abcdef", "x").isValid());
        assertFalse(new MemberConfig("123", "0", "1234", "too-short", "x").isValid());
    }
}
