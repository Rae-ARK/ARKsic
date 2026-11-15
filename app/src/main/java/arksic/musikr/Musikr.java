/*
 * ARKsic - Java port of logic from Auxio (https://github.com/OxygenCobalt/Auxio)
 * Original Kotlin copyright (c) 2023 Auxio Project, licensed GPL-3.0-or-later.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package arksic.musikr;

import arksic.musikr.graph.MusicGraph;
import arksic.musikr.interpret.Interpretation;
import arksic.musikr.interpret.PrePlaylist;
import arksic.musikr.interpret.RawSong;
import arksic.musikr.interpret.TagInterpreter;
import arksic.musikr.metadata.TagReader;
import arksic.musikr.model.Library;
import arksic.musikr.model.LibraryImpl;
import arksic.musikr.playlist.StoredPlaylists;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

/**
 * The library pipeline: explore the folders, extract tags, interpret, build the graph, create the
 * library. Port of Musikr.kt's flow (Explore -> Extract -> Evaluate), minus Android storage
 * (SAF/MediaStore) and the on-disk tag cache. Rescans therefore re-read every file; a cache is
 * the obvious next step.
 */
public final class Musikr {
    private static final List<String> EXTENSIONS = List.of(".mp3", ".flac", ".wav", ".wave");

    private Musikr() {}

    /** Result of a scan, so the UI can mention files that couldn't be read. */
    public record Result(Library library, List<Path> failed) {}

    /**
     * @param roots folders to scan recursively
     * @param progress called with the running count of files extracted (from worker threads)
     */
    public static Result run(
            List<Path> roots, Interpretation interpretation, StoredPlaylists playlists, IntConsumer progress)
            throws IOException, InterruptedException {
        List<Path> files = explore(roots);

        // Extract in parallel. jaudiotagger reads are I/O + parse bound; a few threads is plenty.
        int threads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "arksic-extract");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        List<Future<RawSong>> futures = new ArrayList<>();
        try {
            for (Path p : files) {
                futures.add(pool.submit(() -> {
                    try {
                        return TagReader.read(p);
                    } finally {
                        if (progress != null) progress.accept(done.incrementAndGet());
                    }
                }));
            }
            TagInterpreter interpreter = new TagInterpreter(interpretation);
            MusicGraph.Builder graph = MusicGraph.builder();
            List<Path> failed = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    graph.add(interpreter.interpret(futures.get(i).get()));
                } catch (java.util.concurrent.ExecutionException e) {
                    failed.add(files.get(i));
                }
            }
            if (playlists != null) {
                for (var file : playlists.read()) {
                    graph.add(PrePlaylist.interpret(interpretation.naming(), file));
                }
            }
            return new Result(LibraryImpl.create(graph.build(), playlists, interpretation.naming()), failed);
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Path> explore(List<Path> roots) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return EXTENSIONS.stream().anyMatch(n::endsWith);
                        })
                        .sorted()
                        .forEach(out::add);
            }
        }
        return out;
    }
}
