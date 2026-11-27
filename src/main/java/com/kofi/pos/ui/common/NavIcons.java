package com.kofi.pos.ui.common; // Shared UI helpers.

import javafx.scene.Group;               // Makes the scaled icon report its scaled size to layout.
import javafx.scene.Node;                // Return type.
import javafx.scene.shape.SVGPath;       // Vector outline, drawn with CSS stroke colours.

import java.util.Map;                    // Module name to outline.

/** Small outline icons for the sidebar, drawn as vector paths so no image files or libraries are needed. */
public final class NavIcons {
    private NavIcons() { } // Utility class.

    private static final Map<String, String> PATHS = Map.of(
            "Overview", "M3 3h8v8H3z M13 3h8v8h-8z M3 13h8v8H3z M13 13h8v8h-8z",
            "Point of Sale", "M3 4h2.2l2.3 10h10l2-7.5H6 M9.5 19.5h.01 M16.5 19.5h.01",
            "Products", "M12 3l8 4.5v9L12 21l-8-4.5v-9z M4 7.5l8 4.5 8-4.5 M12 12v9",
            "Stock", "M12 3v12 M7 10l5 5 5-5 M4 20h16",
            "Sales & Receipts", "M6 3h12v18l-3-2-3 2-3-2-3 2z M9 8h6 M9 12h6",
            "Reports", "M4 20V10 M10 20V4 M16 20v-7 M3 20h18",
            "Settings", "M4 7h10 M18 7h2 M4 17h2 M10 17h10 M16 4v6 M8 14v6",
            "Exit", "M10 4H5v16h5 M15 8l4 4-4 4 M19 12H9");

    /** Returns the icon for a sidebar entry, about 17px square, or an empty group for an unknown name. */
    public static Node of(String name) {
        SVGPath path = new SVGPath();
        path.setContent(PATHS.getOrDefault(name, ""));
        path.getStyleClass().add("nav-icon");
        path.setScaleX(0.72);
        path.setScaleY(0.72);
        return new Group(path); // Group so layout sees the scaled size.
    }
}
