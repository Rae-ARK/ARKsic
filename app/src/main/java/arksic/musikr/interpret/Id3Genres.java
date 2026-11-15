/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.interpret;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ID3 genre-tag grokking (ID3v1 numeric genres, ID3v2.3 "(17)(RX)Name" forms). Port of ID3.kt. */
public final class Id3Genres {
    private Id3Genres() {}

    /** Parse ID3-style genre values into names. Never returns null for a non-null input. */
    public static List<String> parseNames(List<String> raw) {
        if (raw.size() == 1) {
            String s = raw.get(0);
            String v1 = parseV1(s);
            if (v1 != null) return List.of(v1);
            List<String> v2 = parseV2(s);
            return v2; // may be null: caller falls back to separator splitting
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            String v1 = parseV1(s);
            out.add(v1 != null ? v1 : s);
        }
        return out;
    }

    private static String parseV1(String s) {
        Integer numeric = null;
        try {
            numeric = Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            // fall through
        }
        if (numeric == null) {
            // CR and RX aren't technically ID3v1 but are formatted like a plain number.
            if ("CR".equals(s)) return "Cover";
            if ("RX".equals(s)) return "Remix";
            return null;
        }
        return numeric >= 0 && numeric < TABLE.length ? TABLE[numeric] : null;
    }

    private static final Pattern V2 = Pattern.compile("((?:\\((\\d+|RX|CR)\\))*)(.+)?");

    private static List<String> parseV2(String s) {
        Matcher m = V2.matcher(s);
        if (!m.matches()) return null;
        Set<String> genres = new LinkedHashSet<>();

        // Case 1: IDs in the format (INT|RX|CR); parse them as ID3v1 tags.
        String ids = m.group(1);
        if (ids != null && !ids.isEmpty()) {
            String inner = ids.substring(1, ids.length() - 1);
            for (String id : inner.split("\\)\\(")) {
                String g = parseV1(id);
                if (g != null) genres.add(g);
            }
        }

        // Case 2: a normal name string; only escaped "((genre" needs care.
        String name = m.group(3);
        if (name != null && !name.isEmpty()) {
            genres.add(name.startsWith("((") ? name.substring(1) : name);
        }

        // If parsing changed nothing, report that.
        if (genres.size() == 1 && genres.iterator().next().equals(s)) return null;
        return new ArrayList<>(genres);
    }

    private static final String[] TABLE = {
        "Blues",
        "Classic Rock",
        "Country",
        "Dance",
        "Disco",
        "Funk",
        "Grunge",
        "Hip-Hop",
        "Jazz",
        "Metal",
        "New Age",
        "Oldies",
        "Other",
        "Pop",
        "R&B",
        "Rap",
        "Reggae",
        "Rock",
        "Techno",
        "Industrial",
        "Alternative",
        "Ska",
        "Death Metal",
        "Pranks",
        "Soundtrack",
        "Euro-Techno",
        "Ambient",
        "Trip-Hop",
        "Vocal",
        "Jazz+Funk",
        "Fusion",
        "Trance",
        "Classical",
        "Instrumental",
        "Acid",
        "House",
        "Game",
        "Sound Clip",
        "Gospel",
        "Noise",
        "AlternRock",
        "Bass",
        "Soul",
        "Punk",
        "Space",
        "Meditative",
        "Instrumental Pop",
        "Instrumental Rock",
        "Ethnic",
        "Gothic",
        "Darkwave",
        "Techno-Industrial",
        "Electronic",
        "Pop-Folk",
        "Eurodance",
        "Dream",
        "Southern Rock",
        "Comedy",
        "Cult",
        "Gangsta",
        "Top 40",
        "Christian Rap",
        "Pop/Funk",
        "Jungle",
        "Native American",
        "Cabaret",
        "New Wave",
        "Psychadelic",
        "Rave",
        "Showtunes",
        "Trailer",
        "Lo-Fi",
        "Tribal",
        "Acid Punk",
        "Acid Jazz",
        "Polka",
        "Retro",
        "Musical",
        "Rock & Roll",
        "Hard Rock",
        "Folk",
        "Folk-Rock",
        "National Folk",
        "Swing",
        "Fast Fusion",
        "Bebob",
        "Latin",
        "Revival",
        "Celtic",
        "Bluegrass",
        "Avantgarde",
        "Gothic Rock",
        "Progressive Rock",
        "Psychedelic Rock",
        "Symphonic Rock",
        "Slow Rock",
        "Big Band",
        "Chorus",
        "Easy Listening",
        "Acoustic",
        "Humour",
        "Speech",
        "Chanson",
        "Opera",
        "Chamber Music",
        "Sonata",
        "Symphony",
        "Booty Bass",
        "Primus",
        "Porn Groove",
        "Satire",
        "Slow Jam",
        "Club",
        "Tango",
        "Samba",
        "Folklore",
        "Ballad",
        "Power Ballad",
        "Rhythmic Soul",
        "Freestyle",
        "Duet",
        "Punk Rock",
        "Drum Solo",
        "A capella",
        "Euro-House",
        "Dance Hall",
        "Goa",
        "Drum & Bass",
        "Club-House",
        "Hardcore",
        "Terror",
        "Indie",
        "Britpop",
        "Negerpunk",
        "Polsk Punk",
        "Beat",
        "Christian Gangsta",
        "Heavy Metal",
        "Black Metal",
        "Crossover",
        "Contemporary Christian",
        "Christian Rock",
        "Merengue",
        "Salsa",
        "Thrash Metal",
        "Anime",
        "JPop",
        "Synthpop",
        "Abstract",
        "Art Rock",
        "Baroque",
        "Bhangra",
        "Big Beat",
        "Breakbeat",
        "Chillout",
        "Downtempo",
        "Dub",
        "EBM",
        "Eclectic",
        "Electro",
        "Electroclash",
        "Emo",
        "Experimental",
        "Garage",
        "Global",
        "IDM",
        "Illbient",
        "Industro-Goth",
        "Jam Band",
        "Krautrock",
        "Leftfield",
        "Lounge",
        "Math Rock",
        "New Romantic",
        "Nu-Breakz",
        "Post-Punk",
        "Post-Rock",
        "Psytrance",
        "Shoegaze",
        "Space Rock",
        "Trop Rock",
        "World Music",
        "Neoclassical",
        "Audiobook",
        "Audio Theatre",
        "Neue Deutsche Welle",
        "Podcast",
        "Indie Rock",
        "G-Funk",
        "Dubstep",
        "Garage Rock",
        "Psybient",
        "Future Garage"
    };
}
