package arksic.view;

import java.net.URL;
import javax.swing.ImageIcon;

/**
 * Loads the transport/library icon set from {@code resources/arksic/icons}.
 * Placeholder artwork for this scaffold — see
 * docs/implementation/scaffold-salvage.md for provenance and the note
 * about swapping these for original assets before a real release.
 */
public final class Icons {

    private Icons() {
    }

    public static ImageIcon load(String name) {
        URL resource = Icons.class.getResource("/arksic/icons/" + name);
        if (resource == null) {
            return new ImageIcon();
        }
        return new ImageIcon(resource);
    }

    public static ImageIcon play() {
        return load("play-button.png");
    }

    public static ImageIcon pause() {
        return load("pause.png");
    }

    public static ImageIcon stop() {
        return load("stop.png");
    }

    public static ImageIcon next() {
        return load("next.png");
    }

    public static ImageIcon previous() {
        return load("previous.png");
    }

    public static ImageIcon repeat() {
        return load("replay.png");
    }

    public static ImageIcon shuffle() {
        return load("equalizer.png");
    }

    public static ImageIcon volume() {
        return load("speaker.png");
    }

    public static ImageIcon volumeUp() {
        return load("plus.png");
    }

    public static ImageIcon volumeDown() {
        return load("minus.png");
    }

    public static ImageIcon playlist() {
        return load("list.png");
    }

    public static ImageIcon track() {
        return load("audio.png");
    }

    public static ImageIcon close() {
        return load("close.png");
    }
}
