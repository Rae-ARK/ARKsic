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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An ordered, de-duplicated set of covers belonging to an album/artist/genre/playlist. */
public record CoverCollection(List<Cover> covers) {
    public static CoverCollection from(Iterable<Cover> covers) {
        // Most-common-first matches Auxio's "pick the cover most songs share" behaviour.
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Cover> byId = new LinkedHashMap<>();
        for (Cover c : covers) {
            if (c == null) continue;
            counts.computeIfAbsent(c.id(), k -> new int[1])[0]++;
            byId.putIfAbsent(c.id(), c);
        }
        List<String> ids = new ArrayList<>(counts.keySet());
        ids.sort((a, b) -> Integer.compare(counts.get(b)[0], counts.get(a)[0]));
        List<Cover> out = new ArrayList<>();
        for (String id : ids) out.add(byId.get(id));
        return new CoverCollection(List.copyOf(out));
    }

    public Cover first() {
        return covers.isEmpty() ? null : covers.get(0);
    }
}
