/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.util;

import arksic.musikr.tag.Date;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Port of the MessageDigest.update(...) extensions in Auxio's LangUtil.kt. Used to build the
 * stable, metadata-derived UIDs. The byte layout is kept identical to Auxio so UIDs stay stable.
 */
public final class Hasher {
    private final MessageDigest digest;

    public Hasher() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Hasher update(String s) {
        if (s != null) {
            digest.update(s.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } else {
            digest.update((byte) 0);
        }
        return this;
    }

    public Hasher update(Date d) {
        if (d != null) {
            digest.update(d.toString().getBytes(StandardCharsets.UTF_8));
        } else {
            digest.update((byte) 0);
        }
        return this;
    }

    public Hasher update(List<String> strings) {
        for (String s : strings) update(s);
        return this;
    }

    public Hasher update(Integer n) {
        if (n != null) {
            int v = n;
            digest.update(new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)});
        } else {
            digest.update((byte) 0);
        }
        return this;
    }

    /** Fold the SHA-256 digest into a UUID exactly as Auxio does (first 16 bytes, big-endian). */
    public UUID toUuid() {
        byte[] d = digest.digest();
        long hi = 0;
        long lo = 0;
        for (int i = 0; i < 8; i++) hi = (hi << 8) | (d[i] & 0xFF);
        for (int i = 8; i < 16; i++) lo = (lo << 8) | (d[i] & 0xFF);
        return new UUID(hi, lo);
    }
}
