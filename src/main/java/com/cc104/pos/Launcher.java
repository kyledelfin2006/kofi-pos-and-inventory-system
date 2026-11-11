package com.cc104.pos;

/**
 * Plain entry point for IDE "Run" buttons and executable jars.
 *
 * <p>JavaFX refuses to start from a main class that extends {@code Application}
 * when JavaFX is on the classpath rather than the module path. This class does not
 * extend {@code Application}, so it avoids that check and simply starts {@link PosApp}.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        PosApp.main(args);
    }
}
