package com.kofi.pos.service; // Business layer: all store rules live here, with no JavaFX and no file code.

import com.kofi.pos.algorithm.RecursiveAlgorithms; // Hand-written recursion.
import com.kofi.pos.algorithm.SearchAlgorithms;    // Hand-written linear + binary search.
import com.kofi.pos.algorithm.SortAlgorithms;      // Hand-written insertion + selection sort.
import com.kofi.pos.data.PosRepository;            // Storage contract (CSV is hidden behind it).
import com.kofi.pos.data.StoreData;                // Snapshot exchanged with storage.
import com.kofi.pos.model.Activity;                // Dashboard activity entry.
import com.kofi.pos.model.CartLine;                // One line in the cart.
import com.kofi.pos.model.Product;                 // Catalogue item.
import com.kofi.pos.model.RestockAlert;            // Low-stock alert.
import com.kofi.pos.model.Sale;                    // Receipt header.
import com.kofi.pos.model.SaleLine;                // Receipt line.
import com.kofi.pos.model.Settings;                // Store settings.
import com.kofi.pos.model.StockMovement;           // Stock audit row.
import com.kofi.pos.model.User;                    // Login account.
import com.kofi.pos.security.PasswordHasher;       // Password verification.
import com.kofi.pos.util.Money;                    // Two-decimal rounding.

import java.io.IOException;                  // Storage failure.
import java.math.BigDecimal;                 // Exact money arithmetic.
import java.time.LocalDate;                  // Date without time.
import java.time.LocalDateTime;              // Date with time.
import java.time.format.DateTimeFormatter;   // Formats the receipt-number date part.
import java.util.ArrayDeque;                 // Array-backed queue implementation.
import java.util.ArrayList;                  // Resizable array list.
import java.util.HashMap;                      // SKU -> default stock lookup.
import java.util.HashSet;                    // Fast "have I seen this?" set.
import java.util.LinkedList;                 // Doubly linked list.
import java.util.List;                       // Generic list interface.
import java.util.Locale;                     // Locale-independent text case changes.
import java.util.Optional;                   // "Maybe a value" wrapper.
import java.util.Queue;                      // First-in-first-out interface.
import java.util.Set;                        // Unique-values collection.
import java.util.Stack;                      // Last-in-first-out collection.

/**
 * In-memory application service for catalogue, checkout, stock, receipts, and reports.
 * CSV details stay behind {@link PosRepository}; the UI receives snapshots and business operations.
 *
 * <p>Data structures used (see requirements.md):
 * ArrayList = records, LinkedList = cart, Stack = recent activity, Queue = restock alerts.
 */
public final class PosService {
    private static final BigDecimal ZERO = Money.ZERO; // Shared 0.00.
    private static final List<String> UNITS = List.of("cup", "order", "pc", "pack", "box", "kg", "g", "L", "mL"); // Allowed units.
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT); // 20261112.

    private final PosRepository repository;                          // Where data is loaded from / saved to.
    private final ArrayList<Product> products = new ArrayList<>();   // ARRAYLIST: product records.
    private final ArrayList<Sale> sales = new ArrayList<>();         // ARRAYLIST: receipt headers.
    private final ArrayList<SaleLine> saleLines = new ArrayList<>(); // ARRAYLIST: receipt lines.
    private final ArrayList<StockMovement> movements = new ArrayList<>(); // ARRAYLIST: stock audit log.
    private final ArrayList<User> users = new ArrayList<>();         // ARRAYLIST: login accounts.
    private final LinkedList<CartLine> cart = new LinkedList<>();    // LINKED LIST: items being sold right now.
    private final Stack<Activity> activities = new Stack<>();        // STACK: recent activity, newest on top.
    private final Queue<RestockAlert> restockQueue = new ArrayDeque<>(); // QUEUE: pending restocks, oldest first.
    private Settings settings;     // Current store settings.
    private long movementId = 1;   // Next id to give a stock movement.

    /** Loads one store snapshot and rebuilds the activity and low-stock views. */
    public PosService(PosRepository repository) throws IOException {
        this.repository = repository;          // Remember the storage.
        applySnapshot(repository.load());      // Fill all collections from the stored data.
    }

    /** RESET: restores the packaged default CSV data and reloads every in-memory collection from it. */
    public void resetToDefaults() throws IOException {
        applySnapshot(repository.resetToDefaults()); // Storage restores defaults; we replace our state with them.
    }

    /** Replaces all in-memory state with {@code data} (used on startup and on reset). */
    private void applySnapshot(StoreData data) {
        products.clear();  products.addAll(data.products());   // Reload products.
        sales.clear();     sales.addAll(data.sales());         // Reload sales.
        saleLines.clear(); saleLines.addAll(data.saleLines()); // Reload sale lines.
        movements.clear(); movements.addAll(data.movements()); // Reload movements.
        users.clear();     users.addAll(data.users());         // Reload users.
        settings = data.settings();                            // Reload settings.
        cart.clear();                                          // A reset or fresh load starts with an empty cart.
        activities.clear();                                    // Drop old activity before rebuilding it.
        restockQueue.clear();                                  // Drop old alerts before rebuilding them.
        movementId = 1;                                        // Recompute the next movement id below.
        for (StockMovement movement : movements) movementId = Math.max(movementId, movement.id() + 1); // Highest id + 1.
        restoreActivities();     // Rebuild the activity stack from sales and movements.
        refreshRestockQueue();   // Rebuild the low-stock queue.
    }

    /** Kept for existing callers: rounds a value to 2 decimals (delegates to {@link Money}). */
    public static BigDecimal money(BigDecimal value) {
        return Money.of(value);
    }

    // ------------------------------------------------------------------ authentication

    /** Checks the entered credentials against the locally stored salted password hashes. */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false; // Nothing typed.
        String normalized = username.trim();                      // Ignore stray spaces.
        for (User user : users) {                                 // Check each account.
            if (user.username().equalsIgnoreCase(normalized)
                    && PasswordHasher.verify(password, user.salt(), user.passwordHash())) return true; // Name and password match.
        }
        return false; // No account matched.
    }

    // ------------------------------------------------------------------ read-only views

    /** Active (not deleted) products only. */
    public List<Product> activeProducts() {
        ArrayList<Product> result = new ArrayList<>(); // Result list.
        for (Product product : products) if (product.active()) result.add(product); // Keep active ones.
        return result;
    }

    /** SORT #1 (insertion sort): receipts newest first. */
    public List<Sale> sortSalesByDate() {
        return SortAlgorithms.insertionSort(sales, (a, b) -> b.soldAt().compareTo(a.soldAt())); // Reverse chronological.
    }

    /** SORT #2 (selection sort): receipts by highest total first. */
    public List<Sale> sortSalesByTotal() {
        return SortAlgorithms.selectionSort(sales, (a, b) -> b.total().compareTo(a.total())); // Largest total first.
    }

    public List<StockMovement> movements() { return new ArrayList<>(movements); } // Copy so the UI cannot modify our list.
    public List<CartLine> cart() { return new ArrayList<>(cart); }                // Copy of the cart.

    /** Recent activity, newest first, read from the stack from top to bottom. */
    public List<Activity> recentActivities() {
        ArrayList<Activity> recent = new ArrayList<>();
        for (int i = activities.size() - 1; i >= 0; i--) recent.add(activities.get(i)); // Top of stack is the last index.
        return recent;
    }

    public Settings settings() { return settings; }                                    // Current settings.
    public List<String> units() { return UNITS; }                                      // Allowed units.
    public Optional<RestockAlert> nextRestockAlert() { return Optional.ofNullable(restockQueue.peek()); } // Front of queue.
    public int pendingRestockCount() { return restockQueue.size(); }                   // Alerts waiting.
    public List<RestockAlert> restockAlerts() { return new ArrayList<>(restockQueue); } // Alerts in FIFO order.

    // ------------------------------------------------------------------ searching and product sorting

    /** Substring search by name or SKU over active products (a linear scan of the ArrayList). */
    public List<Product> searchProducts(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT); // Normalised search text.
        ArrayList<Product> found = new ArrayList<>();
        for (Product product : products) {                                           // LINEAR SEARCH: check every product.
            if (product.active() && (needle.isEmpty() || product.displayName().toLowerCase(Locale.ROOT).contains(needle)
                    || product.sku().toLowerCase(Locale.ROOT).contains(needle))) found.add(product); // Keep matches.
        }
        return found;
    }

    /** True when another product (not {@code exceptSku}) already uses {@code sku}. */
    public boolean skuExists(String sku, String exceptSku) {
        Product found = binarySearchBySku(sku); // Fast lookup.
        return found != null && (exceptSku == null || !found.sku().equalsIgnoreCase(exceptSku));
    }

    /** Finds one product by exact SKU (case-insensitive), or null. */
    public Product findProduct(String sku) { return binarySearchBySku(sku); }

    /** BINARY SEARCH: sort by SKU first (binary search needs sorted data), then halve the range each step. */
    private Product binarySearchBySku(String sku) {
        if (sku == null) return null;                       // Nothing to look for.
        List<Product> sorted = SortAlgorithms.insertionSort(products, // Sort a copy by SKU, ignoring case.
                (a, b) -> a.sku().compareToIgnoreCase(b.sku()));
        String key = sku.trim();                            // The SKU we are looking for.
        int index = SearchAlgorithms.binarySearch(sorted, p -> p.sku().compareToIgnoreCase(key)); // Search the middle repeatedly.
        return index < 0 ? null : sorted.get(index);        // -1 means not found.
    }

    /** Products A-Z by name (insertion sort). */
    public List<Product> sortProductsByName() {
        return SortAlgorithms.insertionSort(products, (a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
    }

    /** Products with the lowest stock first (selection sort). */
    public List<Product> sortProductsByQuantity() {
        return SortAlgorithms.selectionSort(products, (a, b) -> Integer.compare(a.qtyOnHand(), b.qtyOnHand()));
    }

    // ------------------------------------------------------------------ product maintenance

    /** Validates and creates or updates a product while preserving its current stock balance. */
    public void saveProduct(String originalSku, String sku, String name, String category, String variant, int sizeOz,
                            String unit, BigDecimal cost, BigDecimal price, int reorderLevel) throws IOException {
        String cleanSku = sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT); // SKUs are stored in upper case.
        String cleanName = name == null ? "" : name.trim();                        // Trim spaces.
        String cleanCategory = category == null ? "" : category.trim();            // Category is optional.
        if (cleanSku.isEmpty() || cleanSku.length() > 30) throw new IllegalArgumentException("SKU is required and limited to 30 characters.");
        if (cleanName.isEmpty() || cleanName.length() > 100) throw new IllegalArgumentException("Product name is required and limited to 100 characters.");
        String cleanVariant = variant == null ? "" : variant.trim(); // Hot / Iced / blank.
        if (sizeOz < 0 || sizeOz > 99) throw new IllegalArgumentException("Size must be between 0 and 99 oz.");
        if (reorderLevel < 0) throw new IllegalArgumentException("Reorder level cannot be negative.");
        if (!UNITS.contains(unit)) throw new IllegalArgumentException("Choose a valid unit.");
        validatePrice(cost, "Cost price");       // Non-negative, at most 2 decimals.
        validatePrice(price, "Selling price");   // Same rule.
        if (skuExists(cleanSku, originalSku)) throw new IllegalArgumentException("That SKU already exists.");

        if (originalSku == null) { // Creating a new product: starts with zero stock.
            products.add(new Product(cleanSku, cleanName, cleanCategory, cleanVariant, sizeOz, unit, Money.of(cost), Money.of(price), reorderLevel, 0, true, ""));
        } else {                   // Editing an existing product.
            Product existing = findProduct(originalSku); // Locate the current record.
            if (existing == null) throw new IllegalArgumentException("Product no longer exists.");
            if (!existing.sku().equalsIgnoreCase(cleanSku) && cartContains(existing.sku())) {
                throw new IllegalArgumentException("SKU cannot change while this product is in the cart.");
            }
            if (!existing.sku().equalsIgnoreCase(cleanSku) && hasHistory(existing.sku())) {
                throw new IllegalArgumentException("SKU cannot change after a sale or stock movement.");
            }
            // Replace the old record with an updated copy, keeping stock and active flag.
            products.set(products.indexOf(existing), new Product(cleanSku, cleanName, cleanCategory, cleanVariant, sizeOz, unit,
                    Money.of(cost), Money.of(price), reorderLevel, existing.qtyOnHand(), existing.active(), existing.image()));
        }
        refreshCartProductRefs(); // Cart lines must point at the updated product values.
        refreshRestockQueue();    // Reorder level may have changed.
        saveAll();                // Persist.
    }

    /** True when the cart already holds this SKU. */
    private boolean cartContains(String sku) {
        for (CartLine line : cart) if (line.product().sku().equalsIgnoreCase(sku)) return true;
        return false;
    }

    /** Re-points each cart line at the latest Product record after an edit. */
    private void refreshCartProductRefs() {
        for (int i = 0; i < cart.size(); i++) {
            CartLine line = cart.get(i);                         // Existing line.
            Product current = findProduct(line.product().sku()); // Latest product data.
            if (current != null) cart.set(i, new CartLine(current, line.quantity())); // Swap in the fresh product.
        }
    }

    /** Deletes a product; if it has stock or history it is only deactivated so old receipts stay valid. */
    public void deleteProduct(String sku) throws IOException {
        Product product = findProduct(sku);
        if (product == null) return;                                 // Already gone.
        if (hasHistory(sku) || product.qtyOnHand() > 0) {
            products.set(products.indexOf(product), product.withActive(false)); // Soft delete.
        } else {
            products.remove(product);                                // Hard delete: nothing refers to it.
        }
        refreshRestockQueue();
        saveAll();
    }

    /** True when any sale line or stock movement mentions this SKU. */
    private boolean hasHistory(String sku) {
        for (SaleLine line : saleLines) if (line.sku().equalsIgnoreCase(sku)) return true;
        for (StockMovement movement : movements) if (movement.sku().equalsIgnoreCase(sku)) return true;
        return false;
    }

    // ------------------------------------------------------------------ cart (LinkedList)

    /** Adds one unit of a product to the cart, or increases the quantity if it is already there. */
    public void addToCart(String sku) {
        Product product = findProduct(sku);
        if (product == null || !product.active()) throw new IllegalArgumentException("Choose an active product.");
        if (product.qtyOnHand() == 0) throw new IllegalArgumentException("This product is out of stock.");
        for (int i = 0; i < cart.size(); i++) {                       // Is the product already in the cart?
            CartLine line = cart.get(i);
            if (line.product().sku().equalsIgnoreCase(sku)) {
                setCartQuantity(sku, line.quantity() + 1);            // Yes: bump the quantity.
                return;
            }
        }
        cart.add(new CartLine(product, 1));                           // No: append a new line at the tail of the list.
    }

    /** Sets the quantity of an existing cart line after checking it against stock. */
    public void setCartQuantity(String sku, int quantity) {
        Product product = findProduct(sku);
        if (product == null || quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (quantity > product.qtyOnHand()) throw new IllegalArgumentException("Only " + product.qtyOnHand() + " in stock");
        for (int i = 0; i < cart.size(); i++) {
            CartLine line = cart.get(i);
            if (line.product().sku().equalsIgnoreCase(sku)) {
                cart.set(i, new CartLine(product, quantity));         // Replace the line with the new quantity.
                return;
            }
        }
        throw new IllegalArgumentException("Cart line was not found.");
    }

    public void removeFromCart(String sku) {
        cart.removeIf(line -> line.product().sku().equalsIgnoreCase(sku)); // Unlink every matching node.
    }

    public void clearCart() { cart.clear(); } // Empty the linked list.

    /** Sum of every cart line (price x quantity). */
    public BigDecimal cartSubtotal() {
        BigDecimal subtotal = ZERO;
        for (CartLine line : cart) subtotal = subtotal.add(Money.of(line.product().sellingPrice().multiply(BigDecimal.valueOf(line.quantity()))));
        return Money.of(subtotal);
    }

    /** Subtotal minus discount; returns 0.00 when the discount is invalid. */
    public BigDecimal total(BigDecimal discount) {
        BigDecimal cleanDiscount = Money.of(discount);
        if (cleanDiscount.signum() < 0 || cleanDiscount.compareTo(cartSubtotal()) > 0) return ZERO;
        return Money.of(cartSubtotal().subtract(cleanDiscount));
    }

    /** Change to hand back, or 0.00 when the tender is too small. */
    public BigDecimal change(BigDecimal discount, BigDecimal tender) {
        BigDecimal total = total(discount);
        if (tender == null || tender.compareTo(total) < 0 || total.signum() == 0) return ZERO;
        return Money.of(tender.subtract(total));
    }

    // ------------------------------------------------------------------ checkout and voids

    /** Records one receipt, decrements stock, and adds the corresponding movement audit rows. */
    public Sale checkout(String cashier, BigDecimal discount, BigDecimal tender) throws IOException {
        if (cart.isEmpty()) throw new IllegalArgumentException("Add a product before checkout.");
        validatePrice(discount, "Discount");
        validatePrice(tender, "Cash tendered");
        BigDecimal subtotal = cartSubtotal();
        BigDecimal cleanDiscount = Money.of(discount);
        if (cleanDiscount.compareTo(subtotal) > 0) throw new IllegalArgumentException("Discount cannot exceed subtotal.");
        BigDecimal total = Money.of(subtotal.subtract(cleanDiscount));
        if (tender.compareTo(total) < 0) throw new IllegalArgumentException("Cash tendered must be at least the total.");

        LocalDateTime now = LocalDateTime.now();                // One timestamp for the whole sale.
        String receipt = nextReceiptNumber(now.toLocalDate());  // e.g. R-20261112-0003.
        ArrayList<SaleLine> lines = new ArrayList<>();          // Price-locked lines for this receipt.
        for (CartLine cartLine : cart) {                        // Re-check every line against live stock.
            Product product = findProduct(cartLine.product().sku());
            if (product == null || !product.active() || cartLine.quantity() > product.qtyOnHand()) {
                throw new IllegalArgumentException("Stock changed. Review the cart before checkout.");
            }
            BigDecimal lineTotal = Money.of(product.sellingPrice().multiply(BigDecimal.valueOf(cartLine.quantity())));
            lines.add(new SaleLine(receipt, product.sku(), product.displayName(), product.sellingPrice(), cartLine.quantity(), lineTotal));
        }

        Sale sale = new Sale(receipt, now, cashier, subtotal, cleanDiscount, total, Money.of(tender), Money.of(tender.subtract(total)), "COMPLETED", "");
        for (SaleLine line : lines) {                           // Apply stock changes only after all checks passed.
            Product product = findProduct(line.sku());
            products.set(products.indexOf(product), product.withQuantity(product.qtyOnHand() - line.quantity()));
            movements.add(new StockMovement(nextMovementId(), now, product.sku(), product.displayName(), "Stock Out",
                    line.quantity(), "POS sale", receipt));
        }
        sales.add(sale);                                        // Save the receipt header.
        saleLines.addAll(lines);                                // Save the receipt lines.
        activities.push(new Activity(now, "Sale completed", receipt + " · " + Money.of(total))); // STACK push.
        cart.clear();                                           // Cart is done.
        refreshRestockQueue();                                  // Sold items may now be low.
        saveAll();                                              // Persist.
        return sale;
    }

    /** Builds the next daily receipt number by counting today's existing receipts. */
    private String nextReceiptNumber(LocalDate date) {
        String prefix = "R-" + RECEIPT_DATE.format(date) + "-";
        int next = 1;
        for (Sale sale : sales) {
            if (sale.receiptNo().startsWith(prefix)) next++;
        }
        return prefix + String.format(Locale.ROOT, "%04d", next); // Pad to four digits.
    }

    /** All lines belonging to a receipt. */
    public List<SaleLine> saleLines(String receipt) {
        ArrayList<SaleLine> lines = new ArrayList<>();
        for (SaleLine line : saleLines) if (line.receiptNo().equalsIgnoreCase(receipt)) lines.add(line);
        return lines;
    }

    /** One receipt by number, or null. */
    public Sale sale(String receipt) {
        // LINEAR SEARCH over the sales list (receipts are stored in time order, not by receipt number).
        int index = SearchAlgorithms.linearSearch(sales, s -> s.receiptNo().equalsIgnoreCase(receipt));
        return index < 0 ? null : sales.get(index);
    }

    /** Marks a completed sale void and restores its quantities through logged stock movements. */
    public void voidSale(String receipt, String reason) throws IOException {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 150) throw new IllegalArgumentException("Void reason is required and limited to 150 characters.");
        Sale sale = sale(receipt);
        if (sale == null) throw new IllegalArgumentException("Receipt was not found.");
        if (sale.status().equals("VOID")) throw new IllegalArgumentException("This receipt is already voided.");
        for (SaleLine line : saleLines(receipt)) {              // Put each sold quantity back on the shelf.
            Product product = findProduct(line.sku());
            if (product != null) products.set(products.indexOf(product), product.withQuantity(product.qtyOnHand() + line.quantity()));
            movements.add(new StockMovement(nextMovementId(), LocalDateTime.now(), line.sku(), line.productName(),
                    "Stock In", line.quantity(), "Voided sale: " + cleanReason, receipt));
        }
        int index = sales.indexOf(sale);
        sales.set(index, sale.voided(cleanReason));             // Replace with the VOID copy.
        activities.push(new Activity(LocalDateTime.now(), "Sale voided", receipt + " · " + cleanReason)); // STACK push.
        refreshRestockQueue();
        saveAll();
    }

    // ------------------------------------------------------------------ stock

    /** Applies a manual stock movement and refreshes the low-stock queue. */
    public void moveStock(String sku, String type, int quantity, String reason, String reference) throws IOException {
        Product product = findProduct(sku);
        if (product == null || !product.active()) throw new IllegalArgumentException("Choose an active product.");
        if (!Set.of("Stock In", "Stock Out", "Adjust").contains(type)) throw new IllegalArgumentException("Choose a movement type.");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be a positive integer.");
        String note = reason == null ? "" : reason.trim();
        String ref = reference == null ? "" : reference.trim();
        if (!type.equals("Stock In") && note.isEmpty()) throw new IllegalArgumentException("A note is required for Stock Out and Adjust.");
        if (note.length() > 200 || ref.length() > 50) throw new IllegalArgumentException("Check the note and reference character limits.");
        int newQuantity = product.qtyOnHand() + (type.equals("Stock In") ? quantity : -quantity); // In adds, others subtract.
        if (newQuantity < 0) throw new IllegalArgumentException("Only " + product.qtyOnHand() + " in stock");

        products.set(products.indexOf(product), product.withQuantity(newQuantity));
        LocalDateTime now = LocalDateTime.now();
        movements.add(new StockMovement(nextMovementId(), now, product.sku(), product.displayName(), type, quantity, note, ref));
        activities.push(new Activity(now, type, product.displayName() + " · " + quantity)); // STACK push.
        refreshRestockQueue();
        saveAll();
    }

    /**
     * Puts every product's stock back to its starting level from the default data (the seed file), so stock that
     * has been sold or adjusted returns to the default. Sales, prices and history are kept: each change is logged
     * as a movement ("Stock In" when stock goes up, "Adjust" when it goes down). Products that are not in the
     * default data (ones the user created) have no default level and are left alone.
     */
    public void restoreDefaultStock() throws IOException {
        HashMap<String, Integer> defaults = new HashMap<>();              // SKU -> default stock.
        for (Product product : repository.defaultProducts()) defaults.put(product.sku().toUpperCase(Locale.ROOT), product.qtyOnHand());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < products.size(); i++) {                       // Visit every product by position.
            Product product = products.get(i);
            Integer target = defaults.get(product.sku().toUpperCase(Locale.ROOT));
            if (target == null || !product.active() || product.qtyOnHand() == target) continue; // No default, hidden, or already there.
            int difference = target - product.qtyOnHand();                // Positive = stock must go up.
            movements.add(new StockMovement(nextMovementId(), now, product.sku(), product.displayName(),
                    difference > 0 ? "Stock In" : "Adjust", Math.abs(difference), "Default stock restored", ""));
            products.set(i, product.withQuantity(target));                // Replace the record with the restored copy.
        }
        activities.push(new Activity(now, "Default stock restored", "Stock returned to the default levels")); // STACK push.
        refreshRestockQueue();                                            // Levels changed, so alerts may have too.
        saveAll();
    }

    private long nextMovementId() { return movementId++; } // Returns the current id, then increments it.

    /**
     * QUEUE maintenance: products already queued keep their place (first in, first out);
     * products that recovered are dropped; newly low products join at the back.
     */
    private void refreshRestockQueue() {
        ArrayDeque<RestockAlert> refreshed = new ArrayDeque<>();   // The new queue being built.
        HashSet<String> queuedSkus = new HashSet<>();              // SKUs already placed in it.
        for (RestockAlert alert : restockQueue) {                  // 1) Walk the old queue front to back.
            Product current = findProduct(alert.sku());
            if (current != null && current.active() && current.qtyOnHand() <= current.reorderLevel()) {
                refreshed.add(new RestockAlert(current.sku(), current.displayName(), current.qtyOnHand(), current.reorderLevel())); // Keep, with fresh numbers.
                queuedSkus.add(current.sku().toUpperCase(Locale.ROOT));
            }
        }
        for (Product product : products) {                         // 2) Append any newly low products.
            String key = product.sku().toUpperCase(Locale.ROOT);
            if (product.active() && product.qtyOnHand() <= product.reorderLevel() && !queuedSkus.contains(key)) {
                refreshed.add(new RestockAlert(product.sku(), product.displayName(), product.qtyOnHand(), product.reorderLevel()));
            }
        }
        restockQueue.clear();            // Swap the live queue's contents for the rebuilt one.
        restockQueue.addAll(refreshed);
    }

    // ------------------------------------------------------------------ reports

    /** RECURSION: total cost value of all stock = sum of (cost x quantity), computed recursively. */
    public BigDecimal inventoryCostValue() {
        return Money.of(RecursiveAlgorithms.sum(products,
                p -> p.costPrice().multiply(BigDecimal.valueOf(p.qtyOnHand())))); // Each product's stock cost.
    }

    /** Sum of completed sales between two dates, inclusive. */
    public BigDecimal salesTotal(LocalDate from, LocalDate to) {
        BigDecimal sum = ZERO;
        for (Sale sale : sales) {
            LocalDate date = sale.soldAt().toLocalDate();
            if (sale.status().equals("COMPLETED") && !date.isBefore(from) && !date.isAfter(to)) sum = sum.add(sale.total());
        }
        return Money.of(sum);
    }

    /** Number of completed sales between two dates, inclusive. */
    public int salesCount(LocalDate from, LocalDate to) {
        int count = 0;
        for (Sale sale : sales) {
            LocalDate date = sale.soldAt().toLocalDate();
            if (sale.status().equals("COMPLETED") && !date.isBefore(from) && !date.isAfter(to)) count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ settings and helpers

    /** Validates and saves the store name, address and receipt footer. */
    public void updateSettings(Settings value) throws IOException {
        String storeName = value.storeName() == null ? "" : value.storeName().trim();
        String address = value.storeAddress() == null ? "" : value.storeAddress().trim();
        String footer = value.receiptFooter() == null ? "" : value.receiptFooter().trim();
        if (storeName.isEmpty() || storeName.length() > 80) throw new IllegalArgumentException("Store name is required and limited to 80 characters.");
        if (address.length() > 150 || footer.length() > 200) throw new IllegalArgumentException("Check the address and footer character limits.");
        settings = new Settings(storeName, address, footer);
        saveAll();
    }

    /** Money must be present, non-negative, and have at most two decimal places. */
    private static void validatePrice(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + " must be a non-negative amount with up to two decimal places.");
        }
    }

    /** Rebuilds the activity stack from saved sales and movements (oldest pushed first, newest ends on top). */
    private void restoreActivities() {
        ArrayList<Activity> history = new ArrayList<>();
        for (Sale sale : sales) history.add(new Activity(sale.soldAt(), sale.status().equals("VOID") ? "Sale voided" : "Sale completed", sale.receiptNo()));
        for (StockMovement movement : movements) history.add(new Activity(movement.occurredAt(), movement.type(),
                movement.productName() + " · " + movement.quantity()));
        List<Activity> ordered = SortAlgorithms.insertionSort(history, (a, b) -> a.occurredAt().compareTo(b.occurredAt())); // Oldest first.
        int first = Math.max(0, ordered.size() - 20);                    // Keep only the latest 20.
        for (int i = first; i < ordered.size(); i++) activities.push(ordered.get(i)); // Push in time order.
    }

    /** Persists a snapshot so the repository never holds mutable service collections. */
    private void saveAll() throws IOException {
        repository.save(new StoreData(products, sales, saleLines, movements, users, settings));
    }
}
