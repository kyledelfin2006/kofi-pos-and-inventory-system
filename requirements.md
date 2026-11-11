# Requirements Traceability

This file maps every required data structure and algorithm to **where** it lives in the code and **how** the running system uses it. All of it is implemented in this project; nothing here is missing.

> Rule from the brief: searching and sorting must be the student's own implementation. `Collections.sort()`, `List.sort()`, `Arrays.binarySearch()` and similar built-ins are **not** used for any of them. The hand-written algorithms are in [`algorithm/`](src/main/java/com/kofi/pos/algorithm/).

| # | Required concept | Required application | Status |
|---|---|---|---|
| 1 | ArrayList | Store and manage records | Done |
| 2 | Linked List | Organize and manage a collection of records | Done |
| 3 | Stack | Store recent activities or transactions | Done |
| 4 | Queue | Manage waiting lists or pending requests | Done |
| 5 | Linear search | Search for a particular record | Done |
| 6 | Binary search | Search within sorted records | Done |
| 7 | Sorting (at least two) | Insertion sort + selection sort | Done |
| 8 | Recursion | At least one recursive operation | Done |

---

## 1. ArrayList — store and manage records

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) lines 49–53.

```java
private final ArrayList<Product> products = new ArrayList<>();
private final ArrayList<Sale> sales = new ArrayList<>();
private final ArrayList<SaleLine> saleLines = new ArrayList<>();
private final ArrayList<StockMovement> movements = new ArrayList<>();
private final ArrayList<User> users = new ArrayList<>();
```

**How:** every persistent record type is held in an `ArrayList` while the app runs. CSV rows are loaded into them at startup (`applySnapshot`) and they are written back after every change (`saveAll`). Adding, replacing (`set`) and removing products, sales and stock movements all go through these lists (`saveProduct`, `deleteProduct`, `checkout`, `voidSale`, `moveStock`).

**Seen in the UI:** Products, Stock (movement history), Sales & Receipts tables.

## 2. Linked List — organize and manage a collection of records

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 54 — `LinkedList<CartLine> cart`; operations in the "cart" section (`addToCart`, `setCartQuantity`, `removeFromCart`, `clearCart`).

**How:** the shopping cart is a `LinkedList`, because items are appended at the end as the cashier scans them and are frequently removed from the middle (the *Remove* button on a line). `addToCart` walks the list for an existing line and either bumps its quantity or appends a new `CartLine`; `removeFromCart` unlinks every matching node.

**Seen in the UI:** Point of Sale → "Current sale" table.

## 3. Stack — recent activities or transactions

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 55 — `Stack<Activity> activities`. Pushes at `checkout`, `voidSale`, `moveStock`; rebuilt by `restoreActivities`; read by `recentActivities`.

**How:** each completed sale, voided sale and stock movement does `activities.push(...)`. `recentActivities()` reads the stack **from the top downward**, so the newest action is first (LIFO). On startup the stack is rebuilt from saved sales and movements, keeping the latest 20.

**Seen in the UI:** Overview → "Recent activity" table.

## 4. Queue — waiting list / pending requests

**Where:** [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java) line 56 — `Queue<RestockAlert> restockQueue = new ArrayDeque<>()`; maintained by `refreshRestockQueue`; exposed by `nextRestockAlert` (`peek`), `restockAlerts`, `pendingRestockCount`.

**How:** a product whose stock falls to or below its reorder level joins the **back** of the queue; products already waiting keep their position (first in, first out); products that have been restocked leave the queue. The dashboard's **Receive next restock** button takes the **front** of the queue (`peek`) and opens the Stock screen on that product.

**Seen in the UI:** Overview → "Restock queue".

## 5. Linear search — search for a particular record

**Where:**
- Algorithm: [`SearchAlgorithms.linearSearch`](src/main/java/com/kofi/pos/algorithm/SearchAlgorithms.java) (line 17).
- Used by: `PosService.sale(receipt)` (find a receipt by number, line 376) and `PosService.searchProducts(query)` (scans every product for a name/SKU match, line 144).
- Screens that call it: Point of Sale search box, Stock "Find product", Sales & Receipts and Products filters.

**How:** it starts at index 0 and tests each element in turn until one matches or the list ends — O(n). This is the right choice for unsorted data and for partial-text ("contains") matching.

## 6. Binary search — search within sorted records

**Where:**
- Algorithm: [`SearchAlgorithms.binarySearch`](src/main/java/com/kofi/pos/algorithm/SearchAlgorithms.java) (line 32).
- Used by: `PosService.binarySearchBySku` (line 164), which backs `findProduct(sku)` and `skuExists(sku, …)`.

**How:** products are first sorted by SKU with the hand-written insertion sort (binary search requires sorted input), then the search repeatedly compares the middle element with the key and discards the half that cannot contain it — O(log n). It is used for exact SKU lookups: adding to cart, stock movements and the duplicate-SKU check on the Products form.

## 7. Sorting algorithms — two implemented

Both are in [`SortAlgorithms.java`](src/main/java/com/kofi/pos/algorithm/SortAlgorithms.java). They take a `Comparator`, work on a copy, and return the sorted copy.

| Algorithm | Method | Used for | Triggered from |
|---|---|---|---|
| **Insertion sort** | `insertionSort` (line 18) | Products by name A–Z; sales newest-first; products by SKU (before binary search); activities by time | Products ▸ "Name A–Z · insertion sort"; Sales ▸ "Newest first · insertion sort"; Reports |
| **Selection sort** | `selectionSort` (line 36) | Products by stock low→high; sales by highest total | Products ▸ "Stock low to high · selection sort"; Sales ▸ "Highest total · selection sort" |

The sort choice is visible in the drop-down labels so the algorithm in use can be demonstrated live. The service methods that call them are `sortProductsByName`, `sortProductsByQuantity`, `sortSalesByDate`, `sortSalesByTotal` in [`PosService.java`](src/main/java/com/kofi/pos/service/PosService.java). The JavaFX tables have their built-in column sorting disabled (`setSortPolicy(ignored -> false)`) so only these algorithms control row order.

## 8. Recursion — at least one recursive operation

**Where:** [`RecursiveAlgorithms.sum`](src/main/java/com/kofi/pos/algorithm/RecursiveAlgorithms.java) (line 12) with the recursive helper `sumFrom` (line 20).

```java
if (index >= items.size()) return BigDecimal.ZERO;                  // base case
return amountOf.apply(items.get(index)).add(sumFrom(items, index + 1, amountOf)); // recursive case
```

**How:** `PosService.inventoryCostValue()` calls it to total *cost price × quantity on hand* across all products: the total for position *i* is that product's value plus the total of positions *i+1 … end*.

**Seen in the UI:** Overview → "Inventory at cost" and Reports → "Current inventory at cost".

---

## Other implemented requirements

### Reset data to default
- **Button:** Settings ▸ "Reset data" card ▸ **Reset data to default** (asks for confirmation).
- **Flow:** [`SettingsView.resetPanel`](src/main/java/com/kofi/pos/ui/view/SettingsView.java) → `PosService.resetToDefaults()` → `PosRepository.resetToDefaults()` → `CsvStore.restoreDefaults()`.
- **The default CSV:** the six files in [`src/main/resources/seed/`](src/main/resources/seed/) are the default dataset. Reset overwrites the live files in `data/` with them and reloads memory (including the cart, activity stack and restock queue). Edit the seed files to change what "default" means.
- **Result:** the 20 Kofi menu items (placeholder prices, stock 50 each), no sales or movements, store settings back to "Kofi", and the `admin / admin123` login.

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
