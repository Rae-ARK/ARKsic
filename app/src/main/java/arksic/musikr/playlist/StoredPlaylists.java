/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr.playlist;

import arksic.musikr.model.Music;
import arksic.musikr.model.Song;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent playlist storage. Auxio uses a Room database; on desktop this is a small
 * tab-separated text file (one record per line) so we need no database dependency.
 *
 * <pre>
 * P  &lt;uid&gt;  &lt;escaped name&gt;
 * S  &lt;song uid&gt;
 * </pre>
 */
public final class StoredPlaylists {
    private final Path file;
    private final Map<Music.UID, Entry> entries = new LinkedHashMap<>();

    private static final class Entry {
        final Music.UID uid;
        String name;
        List<Music.UID> songs;

        Entry(Music.UID uid, String name, List<Music.UID> songs) {
            this.uid = uid;
            this.name = name;
            this.songs = songs;
        }
    }

    public StoredPlaylists(Path file) {
        this.file = file;
        load();
    }

    /** An in-memory-only store, used by tests. */
    public static StoredPlaylists inMemory() {
        return new StoredPlaylists(null);
    }

    public synchronized PlaylistHandle create(String name, List<Song> songs) {
        Music.UID uid = Music.UID.auxio(Music.UID.Item.PLAYLIST);
        entries.put(uid, new Entry(uid, name, uids(songs)));
        save();
        return new Handle(uid);
    }

    public synchronized List<PlaylistFile> read() {
        List<PlaylistFile> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            List<SongPointer> pointers = new ArrayList<>();
            for (Music.UID u : e.songs) pointers.add(new SongPointer(u));
            out.add(new PlaylistFile(e.name, pointers, new Handle(e.uid)));
        }
        return out;
    }

    private static List<Music.UID> uids(List<Song> songs) {
        List<Music.UID> l = new ArrayList<>();
        for (Song s : songs) l.add(s.uid());
        return l;
    }

    private final class Handle implements PlaylistHandle {
        private final Music.UID uid;

        Handle(Music.UID uid) {
            this.uid = uid;
        }

        @Override public Music.UID uid() { return uid; }

        @Override
        public void rename(String name) {
            synchronized (StoredPlaylists.this) {
                Entry e = entries.get(uid);
                if (e != null) { e.name = name; save(); }
            }
        }

        @Override
        public void add(List<Song> songs) {
            synchronized (StoredPlaylists.this) {
                Entry e = entries.get(uid);
                if (e != null) { e.songs.addAll(uids(songs)); save(); }
            }
        }

        @Override
        public void rewrite(List<Song> songs) {
            synchronized (StoredPlaylists.this) {
                Entry e = entries.get(uid);
                if (e != null) { e.songs = uids(songs); save(); }
            }
        }

        @Override
        public void delete() {
            synchronized (StoredPlaylists.this) {
                entries.remove(uid);
                save();
            }
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unesc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) return;
        try {
            Entry cur = null;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", 3);
                if (p[0].equals("P") && p.length == 3) {
                    Music.UID uid = Music.UID.fromString(p[1]);
                    if (uid == null) { cur = null; continue; }
                    cur = new Entry(uid, unesc(p[2]), new ArrayList<>());
                    entries.put(uid, cur);
                } else if (p[0].equals("S") && p.length >= 2 && cur != null) {
                    Music.UID u = Music.UID.fromString(p[1]);
                    if (u != null) cur.songs.add(u);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void save() {
        if (file == null) return;
        List<String> lines = new ArrayList<>();
        for (Entry e : entries.values()) {
            lines.add("P\t" + e.uid + "\t" + esc(e.name));
            for (Music.UID u : e.songs) lines.add("S\t" + u);
        }
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
