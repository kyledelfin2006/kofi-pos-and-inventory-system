# Implementation Plan — Offline POS and Stock Management

## Project fit

Build a JavaFX desktop point-of-sale and stock-management system for a small store. It works offline and uses a local SQLite database for persistence and SQL seed data; active records are loaded into Java collections while the program runs.

The assignment's employee attendance sentence is a sample project description, not a required feature for this POS project. The system will instead demonstrate the same required GUI, record operations, data structures, and algorithms using products, sales, and stock movements.

## Scope and screens

1. **Login** — authorized local user; trimmed username (30 characters), masked password, Enter submits.
2. **Dashboard** — sales and stock overview, low-stock/restock queue, and recent activity.
3. **POS** — debounced product search by SKU/name, cart, quantity-on-hand validation, discount, cash tendered, change, checkout, and printable-style receipt detail.
4. **Products** — searchable/sortable table and add, edit, clear, and delete/deactivate form.
5. **Stock** — active-product selector and Stock In, Stock Out, and Adjust movements with a movement history table.
6. **Sales / Receipt Detail** — searchable sales table, immutable receipt details, and void with a required reason.
7. **Reports** — date-bounded sales summary and product stock valuation.
8. **Settings** — store name, address, and receipt footer.

## Functional rules

- Product SKU is required, unique, uppercased, and at most 30 characters; name is required, trimmed, and at most 100. Category is optional; unit defaults to `pc` and is chosen from `pc, kg, g, L, mL, pack, box`.
- Cost and selling prices accept non-negative values with at most two decimal places. A selling price below cost warns but does not block saving. Reorder level defaults to 5. Quantity on hand is read-only in the product form.
- Product deletion removes an unused product with zero stock. If it has stock or sales/stock history, deletion deactivates it so inventory and receipts remain valid; inactive products cannot be sold or selected for new stock movements.
- POS quantity is a positive integer no greater than available stock. Discount is non-negative and no greater than subtotal. Tender must be at least the total. Subtotal, total, and change are read-only and recomputed after cart edits; invalid tender displays `₱0.00` change.
- Checkout saves a receipt, sale lines, stock decrements, and corresponding stock movements. Receipt numbers use `R-YYYYMMDD-####`. Voiding requires a reason (150 characters maximum), restores the sold quantities, records the reversal, and preserves the original sale as voided.
- Stock movement quantity is a positive integer. Stock In adds stock. Stock Out and Adjust decrease stock and cannot exceed on-hand quantity. Out and Adjust require a note (200 characters maximum); reference is optional (50 characters maximum).
- Report dates use `YYYY-MM-DD`, default to today, reject future dates, and require From ≤ To. Store name is required (80 characters); address (150) and receipt footer (200) are optional.
- Text formatters reject invalid numeric/date characters as the user types. Invalid fields show a red border and inline error; required fields are marked `*`, and the relevant save/checkout button stays disabled until valid. Inputs trim on focus loss, money displays as `₱#,##0.00` on blur, text areas show character counts, and read-only fields use a grey style. Enter submits the screen's primary action; Escape clears/cancels the current form or dialog.

## Assignment requirements mapped to the POS

| Requirement | POS use |
|---|---|
| Login, dashboard, navigation | Login and a JavaFX application shell with module navigation |
| Input forms, data table, search, sorting, buttons, notifications | Product and stock forms, POS search/cart, sales/products tables, sort choices, clear/save/delete/checkout actions, inline validation and confirmation messages |
| Add, view, update, delete | Product records; sales and stock movements are created and viewed, with sale voiding preserving the audit trail |
| Search | Handwritten linear search for name/SKU and table records |
| Sort | Handwritten insertion sort by product name and selection sort by on-hand quantity |
| ArrayList | In-memory product, sale, and stock movement records |
| Linked List | POS cart lines, where items are added, changed, and removed during checkout preparation |
| Stack | Recent activity entries, newest first |
| Queue | Pending low-stock restock alerts, processed in FIFO order when restocking is addressed |
| Binary search | Handwritten exact-SKU search over a SKU-sorted product snapshot for duplicate checks/lookups |
| Recursion | Recursive inventory valuation total over the product list |
| Persistence | Local SQLite database at `data/kofi-pos.db`; SQL seed resources initialize the six record types |

Sorting and searching required for the assignment will use explicit loops/recursion rather than `Collections.sort`, `List.sort`, or built-in search utilities. JavaFX may still use observable lists for table display.

## Local SQL data

`src/main/resources/seed/seed.sql` supplies the default records. On first launch, SQLite creates `data/kofi-pos.db` with tables for products, users, sales, sale lines, stock movements, and settings. Money values are stored as decimal text to preserve exact cents; original list order is stored with each record. Saves update all six record types in one transaction. Existing installations migrate their six legacy CSV files once, then remove those files after the database transaction commits. Seed products are the Kofi menu: 16 coffee SKUs (4 drinks x Hot 8/12 oz, Iced 16/22 oz) and 4 food items, with placeholder prices and a starting stock of 50 each. The seed login is `admin` / `admin123`; SQLite stores its salted PBKDF2 password hash, not the plaintext password.

## Current project structure

- Maven project targeting Java 17 with JavaFX Controls 21.0.6; no additional runtime libraries.
- `src/main/java/com/kofi/pos/Launcher.java` — plain entry point; `app/PosApp.java` — JavaFX sign-in, navigation, and module wiring.
- `src/main/java/com/kofi/pos/ui/view/*View.java` — one JavaFX view per module; `ui/common` (`UiSupport`, `ProductTables`) holds shared UI pieces.
- `src/main/java/com/kofi/pos/algorithm/` — hand-written sorts, searches, recursion; `security/` and `util/` — password check and money rounding.
- `src/main/java/com/kofi/pos/service/PosService.java` — in-memory store state, business validation, and use cases (including reset to default data).
- `src/main/java/com/kofi/pos/model/` — immutable domain records shared by the service, views, and persistence adapter.
- `src/main/java/com/kofi/pos/data/PosRepository.java` — persistence port implemented by `SqlitePosRepository`.
- `src/main/resources/seed/seed.sql` — initial products, demo user, and store settings.
- `src/main/resources/style.css` — shared JavaFX look and control states.
- `docs/architecture.md` and `docs/code-walkthrough.md` — dependency map, SOLID choices, and the main code paths explained in order.

Keep screens in Java code unless a view is substantially clearer in FXML. Avoid adding a database, ORM, state-management framework, or extra UI library.

## Implementation sequence

1. Establish the runnable JavaFX/Maven skeleton.
2. Add model records, SQL seed data, database loading/saving, and the in-memory service collections.
3. Implement handwritten searches, both sorts, recursive valuation, product CRUD, inventory movement rules, restock queue, and activity stack.
4. Build login, dashboard/navigation, product, stock, and settings screens.
5. Build POS checkout, receipt detail/void, reports, and field-level validation/shortcuts.
6. Compile the project and launch it where Maven is available, then review the requirement checklist against the finished screens and data flows.

## Current implementation status

- Before this SQLite integration, the Java sources compiled with `javac --release 17` against the locally cached JavaFX Controls 21.0.6 jars.
- This SQLite update could not be compiled here: the Maven launcher returned `Access is denied`, and the local Java compiler did not return.
- SQL seed data, all seven modules, local persistence, and the assignment data-structure/algorithm mappings are implemented.

## Completion checklist

- [x] JavaFX desktop entry point, login, and offline-first SQLite startup are implemented.
- [x] SQL seed data initializes the local database; saved edits persist across restarts.
- [x] Product add/view/update/delete-or-deactivate/search/sort are implemented.
- [x] Stock movements validate and update on-hand quantities.
- [x] POS validates cart, discount, tender, generates receipts, and updates stock.
- [x] Receipt detail and void preserve audit history and restore stock.
- [x] Reports, settings, validation, counters, read-only styling, and keyboard behavior are implemented.
- [x] ArrayList, LinkedList, Stack, Queue, linear search, binary search, two handwritten sorts, and recursion are used in system flows.
- [x] The GUI includes clear actions, notifications, navigation, and an exit action.
- [ ] Launch and exercise the GUI end-to-end; Maven is unavailable in the current environment.
