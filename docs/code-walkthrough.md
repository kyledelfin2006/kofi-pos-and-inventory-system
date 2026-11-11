# Code Walkthrough

This guide follows the main execution paths; every Java file additionally carries a comment on each line. Each numbered item follows the code in order, from a JavaFX event through the business rule and down to persistence.

## Startup and navigation

1. [`PosApp.start`](../src/main/java/com/cc104/pos/app/PosApp.java) is the composition root. It creates the CSV adapter, injects it into `PosService`, and opens sign-in.
2. [`PosService` construction](../src/main/java/com/cc104/pos/service/PosService.java) loads the repository snapshot into its `ArrayList` collections.
3. The constructor finds the next stock movement ID, rebuilds the recent activity `Stack`, then refreshes the FIFO restock `Queue`.
4. [`showLogin`](../src/main/java/com/cc104/pos/app/PosApp.java) trims and bounds the username, then calls `authenticate` when the user submits.
5. [`authenticate`](../src/main/java/com/cc104/pos/service/PosService.java) finds the local account and compares a PBKDF2 hash using `MessageDigest.isEqual`.
6. [`showShell`](../src/main/java/com/cc104/pos/app/PosApp.java) creates the sidebar, top bar, and module content host.
7. [`showModule`](../src/main/java/com/cc104/pos/app/PosApp.java) selects a view class, gives it only the collaborators it needs, wraps the view in a `ScrollPane`, and updates the active navigation style.

## Product editing

1. [`ProductView.build`](../src/main/java/com/cc104/pos/ui/view/ProductView.java) creates search, sort, table, and edit form controls.
2. The form's `validate` callback checks required fields, number formats, and duplicate SKUs while the user types.
3. On save, the view sends raw field values to [`PosService.saveProduct`](../src/main/java/com/cc104/pos/service/PosService.java); it does not edit the service's product collection directly.
4. The service trims and normalizes the SKU/name/category, checks limits and prices, then rejects a duplicate SKU.
5. A new product starts with zero stock. An edit preserves its existing quantity and active state.
6. A SKU change is rejected if a cart line, sale line, or movement refers to the old SKU, keeping old records connected.
7. The service updates the restock queue and cart product references, then calls `saveAll`.
8. `saveAll` creates a `StoreData` snapshot and delegates it to [`PosRepository.save`](../src/main/java/com/cc104/pos/data/PosRepository.java).

## Checkout

1. [`PointOfSaleView.build`](../src/main/java/com/cc104/pos/ui/view/PointOfSaleView.java) creates the searchable catalogue, editable cart, payment fields, and checkout button.
2. Search waits 250 ms before refreshing product results, avoiding a table rebuild for each keystroke.
3. Adding a row calls `addToCart`; changing quantity calls `setCartQuantity`; both are business operations on `PosService`.
4. `updateCartTotals` recomputes subtotal, total, change, and button validity from the current service cart.
5. The checkout event passes cashier, discount, and tender to [`PosService.checkout`](../src/main/java/com/cc104/pos/service/PosService.java).
6. The service rejects an empty cart, invalid money, a discount above subtotal, or insufficient cash.
7. It rechecks every cart line against current active status and stock to catch a stale cart.
8. It creates immutable `SaleLine` records at the current price, then a completed `Sale` with receipt number and change.
9. It decrements inventory and appends one stock-out movement per sale line.
10. It records dashboard activity, clears the cart, refreshes restock alerts, and persists the full snapshot.
11. The view clears payment state, refreshes its table, and opens [`ReceiptDialog`](../src/main/java/com/cc104/pos/ui/view/ReceiptDialog.java).

## Stock movements

1. [`StockView.build`](../src/main/java/com/cc104/pos/ui/view/StockView.java) displays the current movement history and builds a form for a selected active product.
2. The form previews quantity/note validity for immediate feedback, but the service validates again before changing stock.
3. [`PosService.moveStock`](../src/main/java/com/cc104/pos/service/PosService.java) checks active product, movement type, positive quantity, required note, text limits, and available stock.
4. It replaces the immutable product record with its new quantity and appends a `StockMovement` and dashboard `Activity`.
5. It refreshes low-stock alerts and persists the new snapshot.

## Voiding a receipt

1. [`SalesView.build`](../src/main/java/com/cc104/pos/ui/view/SalesView.java) filters and sorts receipts, then renders the selected receipt details.
2. The void action calls [`PosService.voidSale`](../src/main/java/com/cc104/pos/service/PosService.java) with the selected receipt and required reason.
3. The service checks the reason, receipt existence, and current receipt status.
4. It restores each sold quantity and writes a new stock-in movement with the void reason.
5. It replaces the sale record with a voided copy, refreshes activity and restock state, and saves.
6. The view reloads the receipt detail. Historical sale lines remain unchanged for audit purposes.

## Reset to default data

1. [`SettingsView.resetPanel`](../src/main/java/com/cc104/pos/ui/view/SettingsView.java) shows a confirmation dialog, then calls `PosService.resetToDefaults`.
2. The service calls `PosRepository.resetToDefaults`, implemented by `CsvPosRepository`, which calls `CsvStore.restoreDefaults`.
3. `restoreDefaults` copies each `/seed/*.csv` resource over the matching file in `data/` (`REPLACE_EXISTING`), then the repository reads them back into a `StoreData` snapshot.
4. The service's `applySnapshot` clears and refills every collection and rebuilds the activity stack and restock queue.
5. The view shows a notice and reloads the Settings screen and sidebar.

## CSV read/write

1. [`CsvPosRepository.load`](../src/main/java/com/cc104/pos/data/CsvPosRepository.java) calls `CsvStore.initialize` before reading data.
2. Each CSV read skips its header and maps each remaining row into one typed model record.
3. [`CsvStore.initialize`](../src/main/java/com/cc104/pos/data/CsvStore.java) creates `data/` and copies a packaged seed only when that file is absent.
4. [`CsvStore.read`](../src/main/java/com/cc104/pos/data/CsvStore.java) walks the file one character at a time. `quoted` tracks CSV quote state; `field` accumulates a cell; commas and line endings end cells/rows only outside quoted values; doubled quotes become one quote.
5. [`CsvPosRepository.save`](../src/main/java/com/cc104/pos/data/CsvPosRepository.java) maps typed values back into the established CSV column order.
6. [`CsvStore.write`](../src/main/java/com/cc104/pos/data/CsvStore.java) serializes the header and rows, writes a temporary sibling file, then replaces the target (atomic move when supported).

## Assignment collections and algorithms

| Requirement | Implementation |
|---|---|
| `ArrayList` | Product, sale, sale-line, movement, and user collections in `PosService`. |
| `LinkedList` | Mutable cart line ordering while the cashier edits the sale. |
| `Stack` | Recent activities, returned newest-first for the dashboard. |
| `Queue` | FIFO restock alerts returned to the dashboard. |
| Linear search | `SearchAlgorithms.linearSearch`; product search and receipt lookup. |
| Binary search | `SearchAlgorithms.binarySearch`; exact SKU lookup against a manually SKU-sorted product snapshot. |
| Insertion sort | `SortAlgorithms.insertionSort`; product name, sale date, and SKU ordering. |
| Selection sort | `SortAlgorithms.selectionSort`; product stock and sale total ordering. |
| Recursion | `RecursiveAlgorithms.sum`; inventory cost valuation in `inventoryCostValue()`. |

Full traceability is in [requirements.md](../requirements.md).

For the package map and dependency direction, see [architecture.md](architecture.md).
