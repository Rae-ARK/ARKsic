/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.tag;

import java.util.List;
import java.util.function.Function;

/**
 * The type of release an Album is considered (EP, single, compilation, ...). Derived from the
 * MusicBrainz Release Group Type specification: https://musicbrainz.org/doc/Release_Group/Type
 */
public sealed interface ReleaseType {
    /** What kind of performance this release is. Null means "plain". */
    Refinement refinement();

    enum Refinement {
        LIVE,
        REMIX
    }

    record Album(Refinement refinement) implements ReleaseType {}

    record EP(Refinement refinement) implements ReleaseType {}

    record Single(Refinement refinement) implements ReleaseType {}

    record Compilation(Refinement refinement) implements ReleaseType {}

    record Soundtrack() implements ReleaseType {
        public Refinement refinement() { return null; }
    }

    record Mix() implements ReleaseType {
        public Refinement refinement() { return null; }
    }

    record Mixtape() implements ReleaseType {
        public Refinement refinement() { return null; }
    }

    record Demo() implements ReleaseType {
        public Refinement refinement() { return null; }
    }

    /** Parse a release type from MusicBrainz-formatted values, or null if there are none. */
    static ReleaseType parse(List<String> types) {
        if (types.isEmpty()) return null;
        String primary = types.get(0);
        if (primary.equalsIgnoreCase("album")) return parseSecondary(types, 1, Album::new);
        if (primary.equalsIgnoreCase("ep")) return parseSecondary(types, 1, EP::new);
        if (primary.equalsIgnoreCase("single")) return parseSecondary(types, 1, Single::new);
        // Orphan secondary types map to Album.
        return parseSecondary(types, 0, Album::new);
    }

    private static ReleaseType parseSecondary(
            List<String> types, int index, Function<Refinement, ReleaseType> convert) {
        String secondary = index < types.size() ? types.get(index) : null;
        if ("compilation".equalsIgnoreCase(secondary)) {
            String third = index + 1 < types.size() ? types.get(index + 1) : null;
            return parseSecondaryImpl(third, Compilation::new);
        }
        return parseSecondaryImpl(secondary, convert);
    }

    private static ReleaseType parseSecondaryImpl(String type, Function<Refinement, ReleaseType> convert) {
        if (type == null) return convert.apply(null);
        if (type.equalsIgnoreCase("soundtrack")) return new Soundtrack();
        if (type.equalsIgnoreCase("mixtape/street")) return new Mixtape();
        if (type.equalsIgnoreCase("dj-mix")) return new Mix();
        if (type.equalsIgnoreCase("live")) return convert.apply(Refinement.LIVE);
        if (type.equalsIgnoreCase("remix")) return convert.apply(Refinement.REMIX);
        if (type.equalsIgnoreCase("demo")) return new Demo();
        return convert.apply(null);
    }
}
