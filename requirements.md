# Requirements Traceability

This file maps every required data structure and algorithm to **where** it lives in the code and **how** the running system uses it. All of it is implemented in this project; nothing here is missing.

> Rule from the brief: searching and sorting must be the student's own implementation. `Collections.sort()`, `List.sort()`, `Arrays.binarySearch()` and similar built-ins are **not** used for any of them. The hand-written algorithms are in [`algorithm/`](src/main/java/com/kofi/pos/algorithm/).

| # | Required concept | Required application | Status |
|---|---|---|---|
| 1 | Stack | Store recent activities or transactions | Done |
| 2 | Queue | Manage waiting lists or pending requests | Done |
| 3 | Recursion | At least one recursive operation | Done |
| 4 | ArrayList | Store and manage records | Done |
| 5 | Linked List | Organize and manage a collection of records | Done |
| 6 | Linear search | Search for a particular record | Done |
| 7 | Binary search | Search within sorted records | Done |
| 8 | Sorting (at least two) | Insertion sort + selection sort | Done |

---

## 1. Stack — recent activities or transactions

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 55 — `Stack<Activity> activities`. Pushes at `checkout`, `voidSale`, `moveStock`; rebuilt by `restoreActivities`; read by `recentActivities`.

```java
private final Stack<Activity> activities = new Stack<>();   // declaration

activities.push(new Activity(now, "Sale completed", receipt + " · " + Money.of(total))); // checkout

public List<Activity> recentActivities() {                  // read newest-first
    ArrayList<Activity> recent = new ArrayList<>();
    for (int i = activities.size() - 1; i >= 0; i--) recent.add(activities.get(i)); // top of stack = last index
    return recent;
}
```

**Explanation:** `push` places the newest activity on top of the stack. `recentActivities()` walks from the top (last index) down to the bottom, so the most recent action is shown first — last in, first out.

**How:** each completed sale, voided sale and stock movement does `activities.push(...)`. `recentActivities()` reads the stack **from the top downward**, so the newest action is first (LIFO). On startup the stack is rebuilt from saved sales and movements, keeping the latest 20.

**How to use it in the app:**
1. Open **Point of sale**, add a product to the cart, enter *Cash tendered* and press **Complete sale**.
2. Open **Stock** and record a *Stock In* or *Stock Out* movement.
3. Open **Overview**. The **Recent activity** table lists what you just did, newest first, so the last action you made is the top row.
4. Void a sale from **Sales & Receipts** and return to Overview: "Sale voided" is now the top row.

**Seen in the UI:** Overview → "Recent activity" table.

## 2. Queue — waiting list / pending requests

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 56 — `Queue<RestockAlert> restockQueue = new ArrayDeque<>()`; maintained by `refreshRestockQueue`; exposed by `nextRestockAlert` (`peek`), `restockAlerts`, `pendingRestockCount`.

```java
private final Queue<RestockAlert> restockQueue = new ArrayDeque<>();   // declaration

for (RestockAlert alert : restockQueue) { ... refreshed.add(...); }     // refreshRestockQueue: existing alerts keep their order
for (Product product : products) { ... refreshed.add(...); }            // ...then newly low products join at the back
restockQueue.clear();
restockQueue.addAll(refreshed);

restockQueue.peek();                                                    // nextRestockAlert: look at the front
```

**Explanation:** alerts are added at the back (`add`) and read from the front (`peek`), so the product that ran low first is dealt with first — first in, first out. The queue is rebuilt on every stock change so recovered products drop out while waiting ones keep their place.

**How:** a product whose stock falls to or below its reorder level joins the **back** of the queue; products already waiting keep their position (first in, first out); products that have been restocked leave the queue. The dashboard's **Receive next restock** button takes the **front** of the queue (`peek`) and opens the Stock screen on that product.

**How to use it in the app:**
1. Open **Stock** and record a *Stock Out* that takes a product to its reorder level or below. With the default data (stock 50, reorder level 10) take out 40 or more.
2. Do the same for a second product. Open **Overview**: **Restock queue** lists them in the order they ran low, and the heading shows the count.
3. Press **Receive next restock**. The Stock screen opens with the product at the **front** of the queue preselected. Record a *Stock In* that lifts it above its reorder level.
4. Return to Overview: that product has left the queue and the next one is at the front.

**Seen in the UI:** Overview → "Restock queue".

## 3. Recursion — at least one recursive operation

**Where:** [`RecursiveAlgorithms.sum`](src/main/java/com/kofi/pos/algorithm/RecursiveAlgorithms.java) (line 12) with the recursive helper `sumFrom` (line 20).

```java
if (index >= items.size()) return BigDecimal.ZERO;                  // base case
return amountOf.apply(items.get(index)).add(sumFrom(items, index + 1, amountOf)); // recursive case
```

```java
// PosService
public BigDecimal inventoryCostValue() {
    return Money.of(RecursiveAlgorithms.sum(products,
            p -> p.costPrice().multiply(BigDecimal.valueOf(p.qtyOnHand())))); // each product's stock cost
}
```

**Explanation:** the problem "sum the list from position *i*" is reduced to the smaller problem "sum the list from position *i+1*". The base case (no items left returns zero) stops the recursion, and each call then adds its own item's amount on the way back up.

**How:** `PosService.inventoryCostValue()` calls it to total *cost price × quantity on hand* across all products: the total for position *i* is that product's value plus the total of positions *i+1 … end*.

**How to use it in the app:**
1. Open **Overview** and read **Inventory at cost**. This is the recursive total of cost price × quantity for every product.
2. Open **Stock** and record a *Stock Out* of some units.
3. Return to Overview: **Inventory at cost** has dropped by the cost of those units. Reports ▸ **Current inventory at cost** shows the same figure.

**Seen in the UI:** Overview → "Inventory at cost" and Reports → "Current inventory at cost".

## 4. ArrayList — store and manage records

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) lines 49–53.

```java
private final ArrayList<Product> products = new ArrayList<>();
private final ArrayList<Sale> sales = new ArrayList<>();
private final ArrayList<SaleLine> saleLines = new ArrayList<>();
private final ArrayList<StockMovement> movements = new ArrayList<>();
private final ArrayList<User> users = new ArrayList<>();
```

**Explanation:** an `ArrayList` is a resizable array with fast indexed access, which suits record collections that are loaded once, read often and occasionally appended to or replaced.

**How:** every persistent record type is held in an `ArrayList` while the app runs. CSV rows are loaded into them at startup (`applySnapshot`) and they are written back after every change (`saveAll`). Adding, replacing (`set`) and removing products, sales and stock movements all go through these lists (`saveProduct`, `deleteProduct`, `checkout`, `voidSale`, `moveStock`).

**How to use it in the app:**
1. Open **Products** and press **New product**, fill in the form and press **Save product**. The record is appended to the list and appears in the table.
2. Select a row, change a value and press **Update product**. The record is replaced in place.
3. Select a row and press **Delete selected** (and confirm). The record is removed, or hidden if it has sales or stock history.
4. Every change is written back to the CSV files, so the data is the same after a restart.

**Seen in the UI:** Products, Stock (movement history), Sales & Receipts tables.

## 5. Linked List — organize and manage a collection of records

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 54 — `LinkedList<CartLine> cart`; operations in the "cart" section (`addToCart`, `setCartQuantity`, `removeFromCart`, `clearCart`).

```java
private final LinkedList<CartLine> cart = new LinkedList<>();   // declaration

cart.add(new CartLine(product, 1));                              // addToCart: append a new node at the tail
cart.set(i, new CartLine(product, quantity));                    // setCartQuantity: replace a line in place
cart.removeIf(line -> line.product().sku().equalsIgnoreCase(sku)); // removeFromCart: unlink matching nodes
cart.clear();                                                    // clearCart: empty the list
```

**Explanation:** each `CartLine` (product + quantity) is one node in a doubly linked list. Appending at the tail is cheap, and removing a node from the middle only re-links its neighbours instead of shifting every later element as an array would.

**How:** the shopping cart is a `LinkedList`, because items are appended at the end as the cashier scans them and are frequently removed from the middle (the *Remove* button on a line). `addToCart` walks the list for an existing line and either bumps its quantity or appends a new `CartLine`; `removeFromCart` unlinks every matching node.

**How to use it in the app:**
1. Open **Point of sale** and tap a product (choose a size for drinks). A line is appended to **Current sale**.
2. Tap the same product again: the existing line's quantity goes up instead of a new line appearing.
3. Change the *Qty* box on a line, or press **Remove** on a line in the middle: the other lines keep their order.
4. Press **Clear cart** to empty the list.

**Seen in the UI:** Point of Sale → "Current sale" table.

## 6. Linear search — search for a particular record

**Where:**
- Algorithm: [`SearchAlgorithms.linearSearch`](src/main/java/com/kofi/pos/algorithm/SearchAlgorithms.java) (line 17).
- Used by: `PosService.sale(receipt)` (find a receipt by number, line 376) and `PosService.searchProducts(query)` (scans every product for a name/SKU match, line 144).
- Screens that call it: Point of Sale search box, Stock "Find product", Sales & Receipts and Products filters.

```java
public static <T> int linearSearch(List<T> items, Predicate<T> matches) {
    for (int i = 0; i < items.size(); i++) {       // visit every position in order
        if (matches.test(items.get(i))) return i;  // stop at the first match
    }
    return -1;                                     // no match found
}
```

**Explanation:** the loop checks each record from the front until the test passes, returning its index (or `-1`). No ordering is needed, so it works on any list.

**How:** it starts at index 0 and tests each element in turn until one matches or the list ends — O(n). This is the right choice for unsorted data and for partial-text ("contains") matching.

**How to use it in the app:** type part of a name or SKU (for example `latte` or `CRL`) in the **Point of sale** search box, the **Products** search box, or **Stock ▸ Find product**. The list narrows to every match.

## 7. Binary search — search within sorted records

**Where:**
- Algorithm: [`SearchAlgorithms.binarySearch`](src/main/java/com/kofi/pos/algorithm/SearchAlgorithms.java) (line 32).
- Used by: `PosService.binarySearchBySku` (line 164), which backs `findProduct(sku)` and `skuExists(sku, …)`.

```java
public static <T> int binarySearch(List<T> sortedItems, ToIntFunction<T> compareToKey) {
    int low = 0;
    int high = sortedItems.size() - 1;
    while (low <= high) {
        int mid = low + (high - low) / 2;                            // middle of the remaining range
        int result = compareToKey.applyAsInt(sortedItems.get(mid));  // compare middle with the key
        if (result == 0) return mid;                                 // found
        if (result < 0) low = mid + 1;                               // key is in the right half
        else high = mid - 1;                                         // key is in the left half
    }
    return -1;                                                       // not present
}
```

**Explanation:** each pass looks at the middle element and throws away the half that cannot contain the key, so the range shrinks quickly (O(log n)). The list must already be sorted, which is why `binarySearchBySku` sorts by SKU first.

**How:** products are first sorted by SKU with the hand-written insertion sort (binary search requires sorted input), then the search repeatedly compares the middle element with the key and discards the half that cannot contain it — O(log n). It is used for exact SKU lookups: adding to cart, stock movements and the duplicate-SKU check on the Products form.

**How to use it in the app:** add a product on the **Point of sale** screen, or type an existing SKU into the **Products** form. The system finds the record by exact SKU with binary search; on the form, a SKU that already exists shows "SKU already exists."

## 8. Sorting algorithms — two implemented

Both are in [`SortAlgorithms.java`](src/main/java/com/kofi/pos/algorithm/SortAlgorithms.java). They take a `Comparator`, work on a copy, and return the sorted copy.

**Insertion sort** — take the next value and slide it left past every larger value until it fits:

```java
public static <T> List<T> insertionSort(List<T> source, Comparator<? super T> order) {
    ArrayList<T> sorted = new ArrayList<>(source);       // sort a copy
    for (int i = 1; i < sorted.size(); i++) {
        T value = sorted.get(i);                         // element being inserted
        int j = i - 1;
        while (j >= 0 && order.compare(sorted.get(j), value) > 0) {
            sorted.set(j + 1, sorted.get(j));            // shift larger neighbour right
            j--;
        }
        sorted.set(j + 1, value);                        // drop value into the gap
    }
    return sorted;
}
```

**Explanation:** the left part of the list is always sorted; each new element is shifted into its correct place within it. Stable, and fast on nearly sorted data.

**Selection sort** — repeatedly find the smallest remaining element and swap it into place:

```java
public static <T> List<T> selectionSort(List<T> source, Comparator<? super T> order) {
    ArrayList<T> sorted = new ArrayList<>(source);       // sort a copy
    for (int i = 0; i < sorted.size() - 1; i++) {
        int best = i;                                    // best candidate for slot i
        for (int j = i + 1; j < sorted.size(); j++) {
            if (order.compare(sorted.get(j), sorted.get(best)) < 0) best = j;
        }
        T temp = sorted.get(i);                          // swap slot i with the best element
        sorted.set(i, sorted.get(best));
        sorted.set(best, temp);
    }
    return sorted;
}
```

**Explanation:** for each position the inner loop scans the rest of the list for the "first" element under the supplied `Comparator`, then swaps it into that position. Only n−1 swaps, but always O(n²) comparisons.

| Algorithm | Method | Used for | Triggered from |
|---|---|---|---|
| **Insertion sort** | `insertionSort` (line 18) | Products by name A–Z; sales newest-first; products by SKU (before binary search); activities by time | Products ▸ "Name A–Z · insertion sort"; Sales ▸ "Newest first · insertion sort"; Reports |
| **Selection sort** | `selectionSort` (line 36) | Products by stock low→high; sales by highest total | Products ▸ "Stock low to high · selection sort"; Sales ▸ "Highest total · selection sort" |

The sort choice is visible in the drop-down labels so the algorithm in use can be demonstrated live. The service methods that call them are `sortProductsByName`, `sortProductsByQuantity`, `sortSalesByDate`, `sortSalesByTotal` in [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java). The JavaFX tables have their built-in column sorting disabled (`setSortPolicy(ignored -> false)`) so only these algorithms control row order.

**How to use it in the app:** on **Products**, switch the sort drop-down between *Name A–Z · insertion sort* and *Stock low to high · selection sort*. On **Sales & Receipts**, switch between *Newest first · insertion sort* and *Highest total · selection sort*. The table re-orders using the named algorithm.

---

## Other implemented requirements

### Reset data to default
- **Button:** Settings ▸ "Reset data" card ▸ **Reset data to default** (asks for confirmation).
- **Flow:** [`SettingsView.resetPanel`](src/main/java/com/kofi/pos/ui/view/SettingsView.java) → `PosService.resetToDefaults()` → `PosRepository.resetToDefaults()` → `CsvStore.restoreDefaults()`.
- **The default CSV:** the six files in [`src/main/resources/seed/`](src/main/resources/seed/) are the default dataset. Reset overwrites the live files in `data/` with them and reloads memory (including the cart, activity stack and restock queue). Edit the seed files to change what "default" means.
- **Result:** the 20 Kofi menu items (placeholder prices, stock 50 each), no sales or movements, store settings back to "Kofi", and the `admin / admin123` login.

### Messages — success, error and confirmation notifications
The system always tells the user what happened. There are four kinds of message:

**1. Success (top-bar notice)** — [`PosApp.showNotice`](src/main/java/com/kofi/pos/app/PosApp.java) shows a green message in the top bar. Every screen receives it as a `Consumer<String> notice` callback and calls it after an action succeeds.

```java
private void showNotice(String message) {
    if (statusLabel != null) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().remove("status-error");
        statusLabel.getStyleClass().add("status-success");   // green styling
    }
}

// a screen reporting success (StockView):
service.moveStock(...);
notice.accept("Stock movement saved");
```

Other success messages: "Store settings saved", "Data reset to default".

**2. Error (inline message on the form)** — when the service rejects an input it throws `IllegalArgumentException` (or saving throws `IOException`). The screen catches it and puts the message in an error label next to the form, so the user can fix the value without losing their place.

```java
try {
    service.updateSettings(new Settings(name.getText(), address.getText(), footer.getText()));
    notice.accept("Store settings saved");
    settingsSaved.run();
} catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
```

Examples of messages the user sees: "Choose an active product.", "This product is out of stock.", "Only 5 in stock", "Add a product before checkout."

**3. Confirmation (pop-up dialog)** — before a destructive action the system asks first with a JavaFX `Alert` of type `CONFIRMATION`. Nothing happens unless the user presses OK. Used when deleting a product, resetting data to default, and exiting with an unfinished cart.

```java
Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
        "This permanently erases the current data and restores the default data. Continue?",
        ButtonType.CANCEL, ButtonType.OK);
confirm.initOwner(stage);
confirm.setTitle("Reset data");
confirm.setHeaderText("Reset all data to default?");
if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return; // user backed out
```

**4. Fatal error (pop-up dialog)** — `PosApp.showFatal` shows an `Alert` of type `ERROR` for an unrecoverable startup problem (for example the data files cannot be loaded), then closes the app.

**Explanation:** success is a non-blocking notification, errors appear right where the user is working, confirmations block until the user decides, and fatal errors stop the app. Business rules live in `PosService`, which only throws the message text; the screens decide how to display it.

### Reset inventory to 0
- **Button:** Stock ▸ "Reset inventory" card ▸ **Reset inventory to 0** (asks for confirmation).
- **Flow:** [`StockView.resetPanel`](src/main/java/com/kofi/pos/ui/view/StockView.java) → `PosService.resetInventoryToZero()`.
- **Result:** every product's stock becomes 0. Products, prices, sales and users are kept. Each product that had stock gets an *Adjust* movement for the removed quantity (reason "Inventory reset to 0"), so the movement history still adds up. An "Inventory reset" entry is pushed on the activity stack, the open cart is cleared, and because every product is now at or below its reorder level the restock queue refills with all of them. This differs from **Reset data to default** on the Settings screen, which restores the original sample data (stock 50 each) and erases sales and history.

### Launcher
The app is still started through [`Launcher`](src/main/java/com/kofi/pos/Launcher.java): `pom.xml` sets it as the `javafx:run` main class, so `run.cmd`, `mvn javafx:run` and IDE runs all go through it.

## Code layout (where each concern lives)

```
com.kofi.pos
├── Launcher                     entry point (run this)
├── app/PosApp                   window, login, sidebar, navigation, wiring
├── ui/
│   ├── view/                    one class per screen + receipt dialog
│   └── common/                  shared UI helpers and the product table
├── service/PosService           business rules and the in-memory data structures
├── algorithm/                   hand-written sorting, searching, recursion
├── security/PasswordHasher      PBKDF2 password check
├── util/Money                   money rounding
├── data/                        PosRepository (interface), CsvPosRepository, StoreData; csv/ = CsvStore, CsvTable + one *Csv class per file
└── model/                       immutable records: Product, Sale, SaleLine, StockMovement, ...
```

See [docs/architecture.md](docs/architecture.md) for the dependency rules and [docs/code-walkthrough.md](docs/code-walkthrough.md) for the execution paths. Every Java file also carries a comment on each line explaining what it does.
