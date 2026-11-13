package com.kofi.pos.ui.common; // Shared UI helpers.

import com.kofi.pos.model.Product; // Needed to pick a product's picture.
import javafx.scene.image.Image;   // JavaFX picture type.
import javafx.scene.image.ImageView; // Node that displays a picture.

import java.io.InputStream;        // Reads a resource or file.
import java.nio.file.Files;        // File checks.
import java.nio.file.Path;         // File location.
import java.util.HashMap;          // Cache of already loaded pictures.
import java.util.Map;              // Map interface.

/**
 * Loads pictures for the UI. A missing picture NEVER breaks a screen: products fall back to a
 * category placeholder, and every request is cached so each file is read only once.
 *
 * <p>Where to put real pictures:
 * <ul>
 *   <li>Product photos: {@code assets/products/<file>} next to the app (or in resources under /assets/products),
 *       then put the file name in the product's {@code image} column. With the column blank, the file named after
 *       the product is used ({@code spanish-latte.png} for "Spanish Latte"), so every size shares one icon.</li>
 *   <li>Branding: {@code src/main/resources/assets/branding/}.</li>
 * </ul>
 */
public final class AssetLoader {
    public static final String LOGO = "/assets/branding/kofi-logo.png";           // Cup + register + box mark.
    public static final String LOGO_TEXT = "/assets/branding/kofi-logo-text.png"; // "Kofi" wordmark.
    private static final String PRODUCTS = "/assets/products/";                   // Resource folder for product pictures.
    private static final Map<String, Image> CACHE = new HashMap<>();              // path+size -> loaded Image.

    private AssetLoader() { } // Utility class.

    /** Loads a classpath resource such as {@link #LOGO}; returns null when it does not exist. */
    public static Image resource(String path, double width) {
        return CACHE.computeIfAbsent(path + "@" + width, key -> {
            try (InputStream in = AssetLoader.class.getResourceAsStream(path)) { // Look inside the app resources.
                // width, 0 height, preserve ratio, smooth scaling.
                return in == null ? null : new Image(in, width, 0, true, true);
            } catch (Exception e) {
                return null; // Unreadable picture: behave as if it were missing.
            }
        });
    }

    /** The product's own picture if one exists, otherwise the icon for its name, otherwise a category placeholder. */
    public static Image product(Product product, double size) {
        return product(product.image(), product.name(), product.category(), size);
    }

    /** Same lookup from loose values, so a form can preview the icon for a name that is still being typed. */
    public static Image product(String imageFile, String name, String category, double size) {
        String file = imageFile == null ? "" : imageFile;
        String key = "product:" + file + ":" + slug(name) + ":" + category + "@" + size;
        Image cached = CACHE.get(key);
        if (cached != null) return cached;
        Image image = null;
        if (!file.isBlank()) {                                            // A file name was set for this product.
            Path external = Path.of("assets", "products", file);          // 1) loose file next to the app.
            if (Files.isRegularFile(external)) {
                image = new Image(external.toUri().toString(), size, size, true, true);
            } else {
                image = resource(PRODUCTS + file, size);                  // 2) packaged resource.
            }
        }
        if (image == null && !slug(name).isEmpty()) image = resource(PRODUCTS + slug(name) + ".png", size); // 2b) icon named after the product.
        if (image == null) { // 3) placeholder by category.
            String placeholder = "Food".equalsIgnoreCase(category) ? "placeholder-food.png" : "placeholder-coffee.png";
            image = resource(PRODUCTS + placeholder, size);
        }
        if (image != null) CACHE.put(key, image);
        return image;
    }

    /** File-name form of a product name: "Café Latte" becomes "cafe-latte". */
    static String slug(String name) {
        String plain = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", ""); // Drop accents.
        return plain.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /** A ready-made ImageView, or an empty one when the picture is unavailable. */
    public static ImageView view(Image image, double width) {
        ImageView view = new ImageView(image); // null image is allowed: shows nothing.
        view.setFitWidth(width);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }

    /** A square, softly rounded picture of the given size: the shape every product icon is shown in. */
    public static ImageView icon(Image image, double size) {
        ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(true);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(size, size); // Rounded-corner mask.
        clip.setArcWidth(size * 0.36);
        clip.setArcHeight(size * 0.36);
        view.setClip(clip);
        return view;
    }
}
