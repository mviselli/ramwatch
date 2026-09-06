package com.ramwatch;

/**
 * Entry point for the packaged app.
 *
 * <p>A launcher whose main class extends {@link javafx.application.Application} only starts
 * when JavaFX is on the module path, and the macOS bundle carries it as ordinary jars instead.
 * Going through a class that extends nothing sidesteps that check, which is this class's whole
 * reason to exist: it hands straight over to {@link RamWatchApp}.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class Launcher {

    /** Not meant to be instantiated. */
    private Launcher() {}

    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed through to {@link RamWatchApp#main(String[])}
     */
    public static void main(String[] args) {
        RamWatchApp.main(args);
    }
}
