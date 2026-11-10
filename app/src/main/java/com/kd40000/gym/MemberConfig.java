package com.kd40000.gym;

import java.util.Locale;

public final class MemberConfig {
    public final String memberId;
    public final String superclubId;
    public final String clubId;
    public final String tagx;
    public final String signingKey;

    public MemberConfig(String memberId, String superclubId, String clubId,
                        String tagx, String signingKey) {
        this.memberId = memberId.trim();
        this.superclubId = superclubId.trim();
        this.clubId = clubId.trim();
        this.tagx = tagx.trim().toLowerCase(Locale.ROOT);
        this.signingKey = signingKey.trim();
    }

    public boolean isValid() {
        return memberId.matches("[0-9]+")
            && superclubId.matches("[0-9]+")
            && clubId.matches("[0-9]+")
            && tagx.matches("[0-9a-f]{16}")
            && !signingKey.isEmpty();
    }
}
