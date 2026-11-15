package arksic.playback;


import arksic.list.ListSettings;
import arksic.musikr.MusicRepository;
import arksic.musikr.TestLibrary;
import arksic.musikr.model.Library;
import arksic.musikr.model.MusicParent;
import arksic.musikr.model.Song;
import arksic.playback.state.PlaybackCommandFactory;
import arksic.playback.state.PlaybackStateManager;
import arksic.playback.state.QueueChange;
import arksic.playback.state.ShuffleMode;
import java.util.ArrayList;
import java.util.List;

/** A wired-up playback stack over a fake engine and a synthetic library. */
final class PlaybackFixture {
    static final String[] NAMES = {"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel"};

    final Library library;
    /** Songs in name order, which is also the default "all songs" queue order. */
    final List<Song> songs;
    final PlaybackSettings settings = new PlaybackSettings();
    final ListSettings listSettings = new ListSettings();
    final MusicRepository repository = new MusicRepository();
    final FakeAudioEngine engine = new FakeAudioEngine();
    final PlaybackStateManager manager = new PlaybackStateManager();
    final QueuePlaybackStateHolder holder;
    final PlaybackCommandFactory factory;
    final Recorder events = new Recorder();
    int saveRequests;

    PlaybackFixture() {
        this(NAMES.length);
    }

    PlaybackFixture(int count) {
        this(buildLibrary(count, null));
    }

    PlaybackFixture(Library library) {
        this.library = library;
        this.songs = arksic.list.Sort.byNameAscending().songs(library.songs());
        this.repository.library(library);
        this.holder = new QueuePlaybackStateHolder(manager, engine, settings, () -> saveRequests++);
        this.factory = new PlaybackCommandFactory(manager, settings, listSettings, repository);
        holder.attach();
        manager.addListener(events);
    }

    static Library buildLibrary(int count, String omit) {
        TestLibrary.Spec[] specs = new TestLibrary.Spec[count];
        for (int i = 0; i < count; i++) {
            specs[i] = TestLibrary.song().title(NAMES[i]).album("Album").artists("Artist").durationMs(180_000);
        }
        if (omit == null) return TestLibrary.of(specs);
        List<TestLibrary.Spec> kept = new ArrayList<>();
        for (TestLibrary.Spec s : specs) if (!specTitle(s).equals(omit)) kept.add(s);
        return TestLibrary.of(kept.toArray(new TestLibrary.Spec[0]));
    }

    private static String specTitle(TestLibrary.Spec s) {
        return s.title();
    }

    Song song(String name) {
        return songs.stream().filter(s -> s.name().raw().equals(name)).findFirst().orElseThrow();
    }

    List<Song> songs(String... names) {
        List<Song> l = new ArrayList<>();
        for (String n : names) l.add(song(n));
        return l;
    }

    // --- starting playback ---

    void play(String start, ShuffleMode shuffle) {
        manager.play(factory.songFromAll(start == null ? null : song(start), shuffle));
    }

    void playUnshuffled(String start) {
        play(start, ShuffleMode.OFF);
    }

    void playShuffled(String start) {
        play(start, ShuffleMode.ON);
    }

    void play(String start, boolean shuffled) {
        play(start, shuffled ? ShuffleMode.ON : ShuffleMode.OFF);
    }

    // --- observation ---

    List<String> queue() {
        return names(manager.queue());
    }

    static List<String> names(List<Song> songs) {
        return songs.stream().map(s -> s.name().raw()).toList();
    }

    String current() {
        Song s = manager.currentSong();
        return s == null ? null : s.name().raw();
    }

    MusicParent parent() {
        return manager.parent();
    }

    /** Records everything the manager tells its listeners. */
    static final class Recorder implements PlaybackStateManager.Listener {
        final List<String> log = new ArrayList<>();
        final List<QueueChange> changes = new ArrayList<>();
        int newPlaybacks;
        int indexMoves;
        int sessionEnds;
        int reorders;

        @Override public void onIndexMoved(int index) { indexMoves++; log.add("index:" + index); }

        @Override
        public void onQueueChanged(List<Song> queue, int index, QueueChange change) {
            changes.add(change);
            log.add("queue:" + change.type() + ":" + change.instructions());
        }

        @Override
        public void onQueueReordered(List<Song> queue, int index, boolean isShuffled) {
            reorders++;
            log.add("reordered:" + isShuffled);
        }

        @Override
        public void onNewPlayback(MusicParent parent, List<Song> queue, int index, boolean isShuffled) {
            newPlaybacks++;
            log.add("new:" + index + ":" + isShuffled);
        }

        @Override public void onSessionEnded() { sessionEnds++; log.add("ended"); }

        QueueChange lastChange() { return changes.isEmpty() ? null : changes.get(changes.size() - 1); }
    }
}
