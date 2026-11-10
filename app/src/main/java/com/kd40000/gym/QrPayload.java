package com.kd40000.gym;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Pure-Java payload generator; no network requests, device IDs or Android APIs. */
public final class QrPayload {
    private static final DateTimeFormatter UTC_TIMESTAMP =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private QrPayload() { }

    public static String generate(MemberConfig config, Instant instant) throws Exception {
        if (!config.isValid()) {
            throw new IllegalArgumentException("Membership configuration is incomplete");
        }
        String unsigned = "TAGV5202|" + config.memberId + "|" + config.superclubId
            + "|" + config.clubId + "|" + UTC_TIMESTAMP.format(instant)
            + "|||" + config.tagx + "|";

        // The configured hex-looking key is the literal UTF-8 HMAC key;
        // it must NOT be hex-decoded.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(config.signingKey.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
            mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        return unsigned + signature;
    }
}
