package arksic.model;

/**
 * The full set of persisted settings owned by {@code PreferencesService}.
 * Immutable — changing a setting produces a new {@code Preferences} value
 * that the service then flushes to disk.
 */
public record Preferences(
        String lastLibraryFolder,
        int volumePercent,
        String theme,
        int windowWidth,
        int windowHeight) {

    public static final String DEFAULT_THEME = "retro-dark";
    public static final int DEFAULT_WINDOW_WIDTH = 480;
    public static final int DEFAULT_WINDOW_HEIGHT = 360;

    public static Preferences defaults() {
        return new Preferences(null, 80, DEFAULT_THEME, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
    }

    public Preferences withLastLibraryFolder(String folder) {
        return new Preferences(folder, volumePercent, theme, windowWidth, windowHeight);
    }

    public Preferences withVolumePercent(int volume) {
        return new Preferences(lastLibraryFolder, volume, theme, windowWidth, windowHeight);
    }

    public Preferences withTheme(String newTheme) {
        return new Preferences(lastLibraryFolder, volumePercent, newTheme, windowWidth, windowHeight);
    }

    public Preferences withWindowSize(int width, int height) {
        return new Preferences(lastLibraryFolder, volumePercent, theme, width, height);
    }
}
