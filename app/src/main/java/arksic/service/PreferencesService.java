package arksic.service;

import arksic.model.Preferences;
import java.util.prefs.BackingStoreException;

/**
 * Owns the on-disk settings (last folder, volume, theme, window size),
 * backed by the JDK's {@link java.util.prefs.Preferences} store so no
 * extra dependency or hand-rolled file format is needed. Flushes on every
 * change, per docs/foundation/architecture.md#ownership.
 */
public final class PreferencesService {

    private static final String KEY_LAST_FOLDER = "lastLibraryFolder";
    private static final String KEY_VOLUME = "volumePercent";
    private static final String KEY_THEME = "theme";
    private static final String KEY_WINDOW_WIDTH = "windowWidth";
    private static final String KEY_WINDOW_HEIGHT = "windowHeight";

    private final java.util.prefs.Preferences backingStore;
    private Preferences current;

    public PreferencesService() {
        this(java.util.prefs.Preferences.userNodeForPackage(PreferencesService.class));
    }

    /** Package-visible constructor so tests can inject an isolated node instead of the real user store. */
    PreferencesService(java.util.prefs.Preferences backingStore) {
        this.backingStore = backingStore;
        this.current = load();
    }

    public Preferences current() {
        return current;
    }

    public void save(Preferences preferences) {
        this.current = preferences;
        backingStore.put(KEY_LAST_FOLDER, preferences.lastLibraryFolder() == null ? "" : preferences.lastLibraryFolder());
        backingStore.putInt(KEY_VOLUME, preferences.volumePercent());
        backingStore.put(KEY_THEME, preferences.theme());
        backingStore.putInt(KEY_WINDOW_WIDTH, preferences.windowWidth());
        backingStore.putInt(KEY_WINDOW_HEIGHT, preferences.windowHeight());
        flush();
    }

    private Preferences load() {
        Preferences defaults = Preferences.defaults();
        String folder = backingStore.get(KEY_LAST_FOLDER, "");
        return new Preferences(
                folder.isEmpty() ? null : folder,
                backingStore.getInt(KEY_VOLUME, defaults.volumePercent()),
                backingStore.get(KEY_THEME, defaults.theme()),
                backingStore.getInt(KEY_WINDOW_WIDTH, defaults.windowWidth()),
                backingStore.getInt(KEY_WINDOW_HEIGHT, defaults.windowHeight()));
    }

    private void flush() {
        try {
            backingStore.flush();
        } catch (BackingStoreException e) {
            // Settings staying unsaved for one change isn't fatal to playback;
            // next successful save will still include this value.
        }
    }
}
