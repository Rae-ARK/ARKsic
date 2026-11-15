/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.playback.persist;

import arksic.musikr.model.Library;
import arksic.musikr.model.Music;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import arksic.playback.state.PlaybackStateManager;
import arksic.playback.state.RepeatMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves/restores the queue between sessions (Auxio: PersistenceRepository + Room database). Here
 * it's one small text file, keyed by song UID so it survives library rescans.
 *
 * <pre>
 * position &lt;ms&gt; / repeat &lt;mode&gt; / index &lt;n&gt; / song &lt;uid&gt; / parent &lt;uid or -&gt;
 * mapping &lt;i,i,i&gt;      (empty when not shuffled)
 * heap &lt;uid&gt;           (one line per heap entry)
 * </pre>
 */
public final class PlaybackPersistence {
    private final Path file;

    public PlaybackPersistence(Path file) {
        this.file = file;
    }

    public void save(PlaybackStateManager.SavedState s) {
        if (s == null) return;
        List<String> lines = new ArrayList<>();
        lines.add("position " + s.positionMs());
        lines.add("repeat " + s.repeatMode().name());
        lines.add("index " + s.index());
        lines.add("song " + s.songUid());
        lines.add("parent " + (s.parent() != null ? s.parent().uid() : "-"));
        StringBuilder m = new StringBuilder("mapping ");
        for (int i = 0; i < s.shuffledMapping().size(); i++) {
            if (i > 0) m.append(',');
            m.append(s.shuffledMapping().get(i));
        }
        lines.add(m.toString());
        for (Song song : s.heap()) lines.add("heap " + song.uid());
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save playback state: " + e);
        }
    }

    /** Read the saved state, resolving UIDs against {@code library}. Missing songs become null. */
    public PlaybackStateManager.SavedState read(Library library) {
        if (!Files.isRegularFile(file)) return null;
        try {
            long position = 0;
            RepeatMode repeat = RepeatMode.NONE;
            int index = -1;
            Music.UID songUid = null;
            MusicParent parent = null;
            List<Integer> mapping = new ArrayList<>();
            List<Song> heap = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int sp = line.indexOf(' ');
                if (sp < 0) continue;
                String key = line.substring(0, sp);
                String val = line.substring(sp + 1).trim();
                switch (key) {
                    case "position" -> position = Long.parseLong(val);
                    case "repeat" -> repeat = RepeatMode.valueOf(val);
                    case "index" -> index = Integer.parseInt(val);
                    case "song" -> songUid = Music.UID.fromString(val);
                    case "parent" -> parent = resolveParent(library, Music.UID.fromString(val));
                    case "mapping" -> {
                        if (!val.isEmpty()) for (String p : val.split(",")) mapping.add(Integer.parseInt(p.trim()));
                    }
                    case "heap" -> heap.add(library.findSong(Music.UID.fromString(val)));
                    default -> { }
                }
            }
            if (songUid == null || heap.isEmpty()) return null;
            return new PlaybackStateManager.SavedState(position, repeat, parent, heap, mapping, index, songUid);
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read playback state: " + e);
            return null;
        }
    }

    private static MusicParent resolveParent(Library lib, Music.UID uid) {
        if (uid == null) return null;
        MusicParent p = lib.findAlbum(uid);
        if (p == null) p = lib.findArtist(uid);
        if (p == null) p = lib.findGenre(uid);
        if (p == null) p = lib.findPlaylist(uid);
        return p;
    }
}
