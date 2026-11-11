package com.cc104.pos.service;

import com.cc104.pos.data.PosRepository;
import com.cc104.pos.data.StoreData;
import com.cc104.pos.model.Activity;
import com.cc104.pos.model.CartLine;
import com.cc104.pos.model.Product;
import com.cc104.pos.model.RestockAlert;
import com.cc104.pos.model.Sale;
import com.cc104.pos.model.SaleLine;
import com.cc104.pos.model.Settings;
import com.cc104.pos.model.StockMovement;
import com.cc104.pos.model.User;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * In-memory application service for catalogue, checkout, stock, receipts, and reports.
 * CSV details stay behind {@link PosRepository}; the UI receives snapshots and business operations.
 */
public final class PosService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final List<String> UNITS = List.of("pc", "kg", "g", "L", "mL", "pack", "box");
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

    private final PosRepository repository;
    private final ArrayList<Product> products = new ArrayList<>();
    private final ArrayList<Sale> sales = new ArrayList<>();
    private final ArrayList<SaleLine> saleLines = new ArrayList<>();
    private final ArrayList<StockMovement> movements = new ArrayList<>();
    private final ArrayList<User> users = new ArrayList<>();
    private final LinkedList<CartLine> cart = new LinkedList<>();
    private final Stack<Activity> activities = new Stack<>();
    private final Queue<RestockAlert> restockQueue = new ArrayDeque<>();
    private Settings settings;
    private long movementId = 1;

    /** Loads one store snapshot and rebuilds the activity and low-stock views. */
    public PosService(PosRepository repository) throws IOException {
        this.repository = repository;
        StoreData data = repository.load();
        products.addAll(data.products());
        sales.addAll(data.sales());
        saleLines.addAll(data.saleLines());
        movements.addAll(data.movements());
        users.addAll(data.users());
        settings = data.settings();
        for (StockMovement movement : movements) movementId = Math.max(movementId, movement.id() + 1);
        restoreActivities();
        refreshRestockQueue();
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Checks the entered credentials against the locally stored salted password hashes. */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String normalized = username.trim();
        for (User user : users) {
            if (user.username().equalsIgnoreCase(normalized) && verify(password, user.salt(), user.passwordHash())) return true;
        }
        return false;
    }

    private static boolean verify(String password, String salt, String hash) {
        try {
            byte[] expected = decodeHex(hash);
            byte[] actual = derive(password.toCharArray(), java.util.Base64.getDecoder().decode(salt));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
        Arrays.fill(password, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static byte[] decodeHex(String text) {
        if (text == null || text.length() % 2 != 0 || !HEX.matcher(text).matches()) throw new IllegalArgumentException();
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    public List<Product> activeProducts() {
        ArrayList<Product> result = new ArrayList<>();
        for (Product product : products) if (product.active()) result.add(product);
        return result;
    }
    public List<Sale> sortSalesByDate() {
        ArrayList<Sale> sorted = new ArrayList<>(sales);
        for (int i = 1; i < sorted.size(); i++) {
            Sale value = sorted.get(i);
            int j = i - 1;
            while (j >= 0 && sorted.get(j).soldAt().isBefore(value.soldAt())) {
                sorted.set(j + 1, sorted.get(j--));
            }
            sorted.set(j + 1, value);
        }
        return sorted;
    }
    public List<Sale> sortSalesByTotal() {
        ArrayList<Sale> sorted = new ArrayList<>(sales);
        for (int i = 0; i < sorted.size() - 1; i++) {
            int largest = i;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).total().compareTo(sorted.get(largest).total()) > 0) largest = j;
            }
            Sale value = sorted.get(i);
            sorted.set(i, sorted.get(largest));
            sorted.set(largest, value);
        }
        return sorted;
    }
    public List<StockMovement> movements() { return new ArrayList<>(movements); }
    public List<CartLine> cart() { return new ArrayList<>(cart); }
    public List<Activity> recentActivities() {
        ArrayList<Activity> recent = new ArrayList<>();
        for (int i = activities.size() - 1; i >= 0; i--) recent.add(activities.get(i));
        return recent;
    }
    public Settings settings() { return settings; }
    public List<String> units() { return UNITS; }
    public Optional<RestockAlert> nextRestockAlert() { return Optional.ofNullable(restockQueue.peek()); }
    public int pendingRestockCount() { return restockQueue.size(); }
    public List<RestockAlert> restockAlerts() { return new ArrayList<>(restockQueue); }

    public List<Product> searchProducts(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Product> found = new ArrayList<>();
        for (Product product : products) {
            if (product.active() && (needle.isEmpty() || product.name().toLowerCase(Locale.ROOT).contains(needle)
                    || product.sku().toLowerCase(Locale.ROOT).contains(needle))) found.add(product);
        }
        return found;
    }

    public boolean skuExists(String sku, String exceptSku) {
        Product found = binarySearchBySku(sku);
        return found != null && (exceptSku == null || !found.sku().equalsIgnoreCase(exceptSku));
    }

    public Product findProduct(String sku) { return binarySearchBySku(sku); }

    private Product binarySearchBySku(String sku) {
        if (sku == null) return null;
        ArrayList<Product> sorted = new ArrayList<>(products);
        insertionSortBySku(sorted);
        String key = sku.trim();
        int low = 0, high = sorted.size() - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            int compare = sorted.get(mid).sku().compareToIgnoreCase(key);
            if (compare == 0) return sorted.get(mid);
            if (compare < 0) low = mid + 1;
            else high = mid - 1;
        }
        return null;
    }

    public List<Product> sortProductsByName() {
        ArrayList<Product> sorted = new ArrayList<>(products);
        for (int i = 1; i < sorted.size(); i++) {
            Product value = sorted.get(i);
            int j = i - 1;
            while (j >= 0 && sorted.get(j).name().compareToIgnoreCase(value.name()) > 0) {
                sorted.set(j + 1, sorted.get(j--));
            }
            sorted.set(j + 1, value);
        }
        return sorted;
    }

    public List<Product> sortProductsByQuantity() {
        ArrayList<Product> sorted = new ArrayList<>(products);
        for (int i = 0; i < sorted.size() - 1; i++) {
            int smallest = i;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (sorted.get(j).qtyOnHand() < sorted.get(smallest).qtyOnHand()) smallest = j;
            }
            Product value = sorted.get(i);
            sorted.set(i, sorted.get(smallest));
            sorted.set(smallest, value);
        }
        return sorted;
    }

    private static void insertionSortBySku(ArrayList<Product> values) {
        for (int i = 1; i < values.size(); i++) {
            Product value = values.get(i);
            int j = i - 1;
            while (j >= 0 && values.get(j).sku().compareToIgnoreCase(value.sku()) > 0) {
                values.set(j + 1, values.get(j--));
            }
            values.set(j + 1, value);
        }
    }

    /** Validates and creates or updates a product while preserving its current stock balance. */
    public void saveProduct(String originalSku, String sku, String name, String category, String unit,
                            BigDecimal cost, BigDecimal price, int reorderLevel) throws IOException {
        String cleanSku = sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
        String cleanName = name == null ? "" : name.trim();
        String cleanCategory = category == null ? "" : category.trim();
        if (cleanSku.isEmpty() || cleanSku.length() > 30) throw new IllegalArgumentException("SKU is required and limited to 30 characters.");
        if (cleanName.isEmpty() || cleanName.length() > 100) throw new IllegalArgumentException("Product name is required and limited to 100 characters.");
        if (reorderLevel < 0) throw new IllegalArgumentException("Reorder level cannot be negative.");
        if (!UNITS.contains(unit)) throw new IllegalArgumentException("Choose a valid unit.");
        validatePrice(cost, "Cost price");
        validatePrice(price, "Selling price");
        if (skuExists(cleanSku, originalSku)) throw new IllegalArgumentException("That SKU already exists.");

        if (originalSku == null) {
            products.add(new Product(cleanSku, cleanName, cleanCategory, unit, money(cost), money(price), reorderLevel, 0, true));
        } else {
            Product existing = findProduct(originalSku);
            if (existing == null) throw new IllegalArgumentException("Product no longer exists.");
            if (!existing.sku().equalsIgnoreCase(cleanSku) && cartContains(existing.sku())) {
                throw new IllegalArgumentException("SKU cannot change while this product is in the cart.");
            }
            if (!existing.sku().equalsIgnoreCase(cleanSku) && hasHistory(existing.sku())) {
                throw new IllegalArgumentException("SKU cannot change after a sale or stock movement.");
            }
            products.set(products.indexOf(existing), new Product(cleanSku, cleanName, cleanCategory, unit,
                    money(cost), money(price), reorderLevel, existing.qtyOnHand(), existing.active()));
        }
        refreshCartProductRefs();
        refreshRestockQueue();
        saveAll();
    }

    private boolean cartContains(String sku) {
        for (CartLine line : cart) if (line.product().sku().equalsIgnoreCase(sku)) return true;
        return false;
    }

    private void refreshCartProductRefs() {
        for (int i = 0; i < cart.size(); i++) {
            CartLine line = cart.get(i);
            Product current = findProduct(line.product().sku());
            if (current != null) cart.set(i, new CartLine(current, line.quantity()));
        }
    }

    public void deleteProduct(String sku) throws IOException {
        Product product = findProduct(sku);
        if (product == null) return;
        if (hasHistory(sku) || product.qtyOnHand() > 0) {
            products.set(products.indexOf(product), product.withActive(false));
        } else {
            products.remove(product);
        }
        refreshRestockQueue();
        saveAll();
    }

    private boolean hasHistory(String sku) {
        for (SaleLine line : saleLines) if (line.sku().equalsIgnoreCase(sku)) return true;
        for (StockMovement movement : movements) if (movement.sku().equalsIgnoreCase(sku)) return true;
        return false;
    }

    public void addToCart(String sku) {
        Product product = findProduct(sku);
        if (product == null || !product.active()) throw new IllegalArgumentException("Choose an active product.");
        if (product.qtyOnHand() == 0) throw new IllegalArgumentException("This product is out of stock.");
        for (int i = 0; i < cart.size(); i++) {
            CartLine line = cart.get(i);
            if (line.product().sku().equalsIgnoreCase(sku)) {
                setCartQuantity(sku, line.quantity() + 1);
                return;
            }
        }
        cart.add(new CartLine(product, 1));
    }

    public void setCartQuantity(String sku, int quantity) {
        Product product = findProduct(sku);
        if (product == null || quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1.");
        if (quantity > product.qtyOnHand()) throw new IllegalArgumentException("Only " + product.qtyOnHand() + " in stock");
        for (int i = 0; i < cart.size(); i++) {
            CartLine line = cart.get(i);
            if (line.product().sku().equalsIgnoreCase(sku)) {
                cart.set(i, new CartLine(product, quantity));
                return;
            }
        }
        throw new IllegalArgumentException("Cart line was not found.");
    }

    public void removeFromCart(String sku) {
        cart.removeIf(line -> line.product().sku().equalsIgnoreCase(sku));
    }
    public void clearCart() { cart.clear(); }

    public BigDecimal cartSubtotal() {
        BigDecimal subtotal = ZERO;
        for (CartLine line : cart) subtotal = subtotal.add(money(line.product().sellingPrice().multiply(BigDecimal.valueOf(line.quantity()))));
        return money(subtotal);
    }

    public BigDecimal total(BigDecimal discount) {
        BigDecimal cleanDiscount = money(discount);
        if (cleanDiscount.signum() < 0 || cleanDiscount.compareTo(cartSubtotal()) > 0) return ZERO;
        return money(cartSubtotal().subtract(cleanDiscount));
    }

    public BigDecimal change(BigDecimal discount, BigDecimal tender) {
        BigDecimal total = total(discount);
        if (tender == null || tender.compareTo(total) < 0 || total.signum() == 0) return ZERO;
        return money(tender.subtract(total));
    }

    /** Records one receipt, decrements stock, and adds the corresponding movement audit rows. */
    public Sale checkout(String cashier, BigDecimal discount, BigDecimal tender) throws IOException {
        if (cart.isEmpty()) throw new IllegalArgumentException("Add a product before checkout.");
        validatePrice(discount, "Discount");
        validatePrice(tender, "Cash tendered");
        BigDecimal subtotal = cartSubtotal();
        BigDecimal cleanDiscount = money(discount);
        if (cleanDiscount.compareTo(subtotal) > 0) throw new IllegalArgumentException("Discount cannot exceed subtotal.");
        BigDecimal total = money(subtotal.subtract(cleanDiscount));
        if (tender.compareTo(total) < 0) throw new IllegalArgumentException("Cash tendered must be at least the total.");

        LocalDateTime now = LocalDateTime.now();
        String receipt = nextReceiptNumber(now.toLocalDate());
        ArrayList<SaleLine> lines = new ArrayList<>();
        for (CartLine cartLine : cart) {
            Product product = findProduct(cartLine.product().sku());
            if (product == null || !product.active() || cartLine.quantity() > product.qtyOnHand()) {
                throw new IllegalArgumentException("Stock changed. Review the cart before checkout.");
            }
            BigDecimal lineTotal = money(product.sellingPrice().multiply(BigDecimal.valueOf(cartLine.quantity())));
            lines.add(new SaleLine(receipt, product.sku(), product.name(), product.sellingPrice(), cartLine.quantity(), lineTotal));
        }

        Sale sale = new Sale(receipt, now, cashier, subtotal, cleanDiscount, total, money(tender), money(tender.subtract(total)), "COMPLETED", "");
        for (SaleLine line : lines) {
            Product product = findProduct(line.sku());
            products.set(products.indexOf(product), product.withQuantity(product.qtyOnHand() - line.quantity()));
            movements.add(new StockMovement(nextMovementId(), now, product.sku(), product.name(), "Stock Out",
                    line.quantity(), "POS sale", receipt));
        }
        sales.add(sale);
        saleLines.addAll(lines);
        activities.push(new Activity(now, "Sale completed", receipt + " · " + money(total)));
        cart.clear();
        refreshRestockQueue();
        saveAll();
        return sale;
    }

    private String nextReceiptNumber(LocalDate date) {
        String prefix = "R-" + RECEIPT_DATE.format(date) + "-";
        int next = 1;
        for (Sale sale : sales) {
            if (sale.receiptNo().startsWith(prefix)) next++;
        }
        return prefix + String.format(Locale.ROOT, "%04d", next);
    }

    public List<SaleLine> saleLines(String receipt) {
        ArrayList<SaleLine> lines = new ArrayList<>();
        for (SaleLine line : saleLines) if (line.receiptNo().equalsIgnoreCase(receipt)) lines.add(line);
        return lines;
    }

    public Sale sale(String receipt) {
        for (Sale sale : sales) if (sale.receiptNo().equalsIgnoreCase(receipt)) return sale;
        return null;
    }

    /** Marks a completed sale void and restores its quantities through logged stock movements. */
    public void voidSale(String receipt, String reason) throws IOException {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty() || cleanReason.length() > 150) throw new IllegalArgumentException("Void reason is required and limited to 150 characters.");
        Sale sale = sale(receipt);
        if (sale == null) throw new IllegalArgumentException("Receipt was not found.");
        if (sale.status().equals("VOID")) throw new IllegalArgumentException("This receipt is already voided.");
        for (SaleLine line : saleLines(receipt)) {
            Product product = findProduct(line.sku());
            if (product != null) products.set(products.indexOf(product), product.withQuantity(product.qtyOnHand() + line.quantity()));
            movements.add(new StockMovement(nextMovementId(), LocalDateTime.now(), line.sku(), line.productName(),
                    "Stock In", line.quantity(), "Voided sale: " + cleanReason, receipt));
        }
        int index = sales.indexOf(sale);
        sales.set(index, sale.voided(cleanReason));
        activities.push(new Activity(LocalDateTime.now(), "Sale voided", receipt + " · " + cleanReason));
        refreshRestockQueue();
        saveAll();
    }

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
        int newQuantity = product.qtyOnHand() + (type.equals("Stock In") ? quantity : -quantity);
        if (newQuantity < 0) throw new IllegalArgumentException("Only " + product.qtyOnHand() + " in stock");

        products.set(products.indexOf(product), product.withQuantity(newQuantity));
        LocalDateTime now = LocalDateTime.now();
        movements.add(new StockMovement(nextMovementId(), now, product.sku(), product.name(), type, quantity, note, ref));
        activities.push(new Activity(now, type, product.name() + " · " + quantity));
        refreshRestockQueue();
        saveAll();
    }

    private long nextMovementId() { return movementId++; }

    private void refreshRestockQueue() {
        ArrayDeque<RestockAlert> refreshed = new ArrayDeque<>();
        HashSet<String> queuedSkus = new HashSet<>();
        for (RestockAlert alert : restockQueue) {
            Product current = findProduct(alert.sku());
            if (current != null && current.active() && current.qtyOnHand() <= current.reorderLevel()) {
                refreshed.add(new RestockAlert(current.sku(), current.name(), current.qtyOnHand(), current.reorderLevel()));
                queuedSkus.add(current.sku().toUpperCase(Locale.ROOT));
            }
        }
        for (Product product : products) {
            String key = product.sku().toUpperCase(Locale.ROOT);
            if (product.active() && product.qtyOnHand() <= product.reorderLevel() && !queuedSkus.contains(key)) {
                refreshed.add(new RestockAlert(product.sku(), product.name(), product.qtyOnHand(), product.reorderLevel()));
            }
        }
        restockQueue.clear();
        restockQueue.addAll(refreshed);
    }

    /** Uses the assignment's recursive calculation to total on-hand inventory cost. */
    public BigDecimal inventoryCostValue() { return inventoryCostValue(0); }
    private BigDecimal inventoryCostValue(int index) {
        if (index >= products.size()) return ZERO;
        Product product = products.get(index);
        return money(product.costPrice().multiply(BigDecimal.valueOf(product.qtyOnHand())).add(inventoryCostValue(index + 1)));
    }

    public BigDecimal salesTotal(LocalDate from, LocalDate to) {
        BigDecimal sum = ZERO;
        for (Sale sale : sales) {
            LocalDate date = sale.soldAt().toLocalDate();
            if (sale.status().equals("COMPLETED") && !date.isBefore(from) && !date.isAfter(to)) sum = sum.add(sale.total());
        }
        return money(sum);
    }

    public int salesCount(LocalDate from, LocalDate to) {
        int count = 0;
        for (Sale sale : sales) {
            LocalDate date = sale.soldAt().toLocalDate();
            if (sale.status().equals("COMPLETED") && !date.isBefore(from) && !date.isAfter(to)) count++;
        }
        return count;
    }

    public void updateSettings(Settings value) throws IOException {
        String storeName = value.storeName() == null ? "" : value.storeName().trim();
        String address = value.storeAddress() == null ? "" : value.storeAddress().trim();
        String footer = value.receiptFooter() == null ? "" : value.receiptFooter().trim();
        if (storeName.isEmpty() || storeName.length() > 80) throw new IllegalArgumentException("Store name is required and limited to 80 characters.");
        if (address.length() > 150 || footer.length() > 200) throw new IllegalArgumentException("Check the address and footer character limits.");
        settings = new Settings(storeName, address, footer);
        saveAll();
    }

    private static void validatePrice(BigDecimal value, String label) {
        if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + " must be a non-negative amount with up to two decimal places.");
        }
    }

    private void restoreActivities() {
        ArrayList<Activity> history = new ArrayList<>();
        for (Sale sale : sales) history.add(new Activity(sale.soldAt(), sale.status().equals("VOID") ? "Sale voided" : "Sale completed", sale.receiptNo()));
        for (StockMovement movement : movements) history.add(new Activity(movement.occurredAt(), movement.type(),
                movement.productName() + " · " + movement.quantity()));
        for (int i = 1; i < history.size(); i++) {
            Activity value = history.get(i);
            int j = i - 1;
            while (j >= 0 && history.get(j).occurredAt().isAfter(value.occurredAt())) {
                history.set(j + 1, history.get(j--));
            }
            history.set(j + 1, value);
        }
        int first = Math.max(0, history.size() - 20);
        for (int i = first; i < history.size(); i++) activities.push(history.get(i));
    }

    /** Persists a snapshot so the repository never holds mutable service collections. */
    private void saveAll() throws IOException {
        repository.save(new StoreData(products, sales, saleLines, movements, users, settings));
    }
}
