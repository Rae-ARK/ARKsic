package arksic.musikr;

import arksic.musikr.graph.MusicGraph;
import arksic.musikr.interpret.Interpretation;
import arksic.musikr.interpret.ParsedTags;
import arksic.musikr.interpret.RawSong;
import arksic.musikr.interpret.TagInterpreter;
import arksic.musikr.model.Library;
import arksic.musikr.model.LibraryImpl;
import arksic.musikr.playlist.StoredPlaylists;
import arksic.musikr.tag.Date;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test helper: builds a Library from synthetic tags with no audio files involved. */
public final class TestLibrary {
    private TestLibrary() {}

    public static final class Spec {
        final ParsedTags.Builder b = new ParsedTags.Builder();
        String dir = "Music/Unsorted";
        String file;

        public Spec title(String t) { b.name = t; return this; }
        public Spec album(String a) { b.albumName = a; return this; }
        public Spec artists(String... a) { b.artistNames = List.of(a); return this; }
        public Spec albumArtists(String... a) { b.albumArtistNames = List.of(a); return this; }
        public Spec genres(String... g) { b.genreNames = List.of(g); return this; }
        public Spec track(int t) { b.track = t; return this; }
        public Spec disc(int d) { b.disc = d; return this; }
        public Spec date(String d) { b.date = Date.from(d); return this; }
        public Spec durationMs(long ms) { b.durationMs = ms; return this; }
        public Spec dir(String d) { this.dir = d; return this; }
        public Spec file(String f) { this.file = f; return this; }
        public Spec albumMbid(String m) { b.albumMusicBrainzId = m; return this; }
        public Spec replayGain(Float track, Float album) { b.replayGainTrack = track; b.replayGainAlbum = album; return this; }
    }

    public static Spec song() { return new Spec(); }

    public static Library of(Spec... specs) {
        return of(Interpretation.defaults(), StoredPlaylists.inMemory(), specs);
    }

    public static Library of(Interpretation interp, StoredPlaylists stored, Spec... specs) {
        TagInterpreter interpreter = new TagInterpreter(interp);
        MusicGraph.Builder graph = MusicGraph.builder();
        List<RawSong> raws = new ArrayList<>();
        int n = 0;
        for (Spec s : specs) {
            String file = s.file != null ? s.file : (s.b.name != null ? s.b.name : "song" + n) + "-" + n + ".mp3";
            n++;
            if (s.b.durationMs == 0) s.b.durationMs = 180_000;
            raws.add(new RawSong(Path.of(s.dir, file), 1000, 1000L * n, 1000L * n, 320, 44100, s.b.build(), null));
        }
        for (RawSong r : raws) graph.add(interpreter.interpret(r));
        for (var f : stored.read()) graph.add(arksic.musikr.interpret.PrePlaylist.interpret(interp.naming(), f));
        return LibraryImpl.create(graph.build(), stored, interp.naming());
    }
}
