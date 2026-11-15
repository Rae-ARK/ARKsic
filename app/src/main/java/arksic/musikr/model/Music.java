/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.model;

import arksic.musikr.tag.Name;
import arksic.musikr.util.Hasher;
import arksic.musikr.util.Parse;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Abstract music data: identification and naming common to all music items. Port of Music.kt
 * (the Parcelable/Room bits are dropped; they were Android-specific).
 */
public interface Music {
    /** A unique identifier for this music item. */
    UID uid();

    /** The name of the music item. */
    Name name();

    /**
     * A unique identifier for a piece of music, derived from internal metadata or the MusicBrainz
     * ID spec. Treat as a black box that can only be read, written, and compared.
     */
    final class UID {
        public enum Item {
            // Int codes are legacy from when Item was MusicType in Auxio; kept for parse compat.
            SONG(0xA10B, 's'),
            ALBUM(0xA10A, 'a'),
            ARTIST(0xA109, 'r'),
            GENRE(0xA108, 'g'),
            PLAYLIST(0xA107, 'p');

            final int intCode;
            final char micro;

            Item(int intCode, char micro) {
                this.intCode = intCode;
                this.micro = micro;
            }
        }

        private enum Format {
            AUXIO("org.oxycblt.auxio", 'a'),
            MUSICBRAINZ("org.musicbrainz", 'm');

            final String namespace;
            final char micro;

            Format(String namespace, char micro) {
                this.namespace = namespace;
                this.micro = micro;
            }
        }

        private final Format format;
        private final Item item;
        private final UUID uuid;
        private final int hash;

        private UID(Format format, Item item, UUID uuid) {
            this.format = format;
            this.item = item;
            this.uuid = uuid;
            this.hash = 31 * (31 * format.hashCode() + item.hashCode()) + uuid.hashCode();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UID u && format == u.format && item == u.item && uuid.equals(u.uuid);
        }

        @Override
        public String toString() {
            return "u" + format.micro + item.micro + uuid;
        }

        /** Random Auxio-style UID, for items with no stable metadata. */
        public static UID auxio(Item item) {
            return new UID(Format.AUXIO, item, UUID.randomUUID());
        }

        /** Auxio-style UID hashed from stable metadata. */
        public static UID auxio(Item item, Consumer<Hasher> updates) {
            Hasher h = new Hasher();
            updates.accept(h);
            return new UID(Format.AUXIO, item, h.toUuid());
        }

        public static UID musicBrainz(Item item, UUID mbid) {
            return new UID(Format.MUSICBRAINZ, item, mbid);
        }

        /** Parse a UID's string form; null if invalid. */
        public static UID fromString(String uid) {
            if (uid == null) return null;
            if (uid.startsWith("u")) {
                if (uid.length() < 4) return null;
                Format f = null;
                for (Format x : Format.values()) if (x.micro == uid.charAt(1)) f = x;
                Item it = null;
                for (Item x : Item.values()) if (x.micro == uid.charAt(2)) it = x;
                if (f == null || it == null) return null;
                UUID u = Parse.toUuidOrNull(uid.substring(3));
                return u == null ? null : new UID(f, it, u);
            }
            String[] split = uid.split(":", 2);
            if (split.length != 2) return null;
            Format f = null;
            for (Format x : Format.values()) if (x.namespace.equals(split[0])) f = x;
            if (f == null) return null;
            String[] ids = split[1].split("-", 2);
            if (ids.length != 2) return null;
            int code;
            try {
                code = Integer.parseInt(ids[0], 16);
            } catch (NumberFormatException e) {
                return null;
            }
            Item it = null;
            for (Item x : Item.values()) if (x.intCode == code) it = x;
            UUID u = Parse.toUuidOrNull(ids[1]);
            return it == null || u == null ? null : new UID(f, it, u);
        }
    }
}
