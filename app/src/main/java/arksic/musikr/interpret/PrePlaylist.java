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

import arksic.musikr.playlist.PlaylistHandle;
import arksic.musikr.playlist.SongPointer;
import arksic.musikr.tag.Name;
import java.util.List;

/** Pre-graph playlist data. Port of PrePlaylist.kt and PlaylistInterpreter.kt. */
public final class PrePlaylist {
    private PrePlaylist() {}

    public interface Info {
        Name.Known name();

        String rawName();

        PlaylistHandle handle();
    }

    public record Pre(Name.Known name, String rawName, PlaylistHandle handle, List<SongPointer> songPointers)
            implements Info {}

    public record Post(Name.Known name, String rawName, PlaylistHandle handle) implements Info {}

    public static Pre interpret(Naming naming, arksic.musikr.playlist.PlaylistFile file) {
        return new Pre(naming.name(file.name(), null), file.name(), file.handle(), file.songPointers());
    }

    public static Post interpret(Naming naming, String name, PlaylistHandle handle) {
        return new Post(naming.name(name, null), name, handle);
    }
}
