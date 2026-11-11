package com.cc104.pos; // Root package: only the entry point lives here.

import com.cc104.pos.app.PosApp; // The real JavaFX application class.

/**
 * Plain entry point for IDE "Run" buttons, run.cmd and executable jars.
 *
 * <p>JavaFX refuses to start from a main class that extends {@code Application}
 * when JavaFX is on the classpath rather than the module path. This class does not
 * extend {@code Application}, so it avoids that check and simply starts {@link PosApp}.
 */
public final class Launcher {
    private Launcher() { } // Never instantiated; it only holds main().

    /** Program start: hands control to the JavaFX application. */
    public static void main(String[] args) {
        PosApp.main(args); // PosApp.main calls Application.launch(...), which opens the window.
    }
}
