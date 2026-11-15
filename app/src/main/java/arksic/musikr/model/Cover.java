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

import java.nio.file.Path;

/**
 * Where to find a cover image. Auxio's covers are Android-specific cached bitmaps; on desktop a
 * cover is either an image file next to the audio ({@code embedded == false}) or artwork embedded
 * in the audio file's tag ({@code embedded == true}, {@code source} is then the audio file).
 */
public record Cover(Path source, boolean embedded) {
    /** Stable identity used to dedupe covers within a collection. */
    public String id() {
        return (embedded ? "e:" : "f:") + source;
    }
}
