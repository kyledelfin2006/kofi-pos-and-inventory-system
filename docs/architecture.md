# Developer Architecture

Kofi is a small JavaFX desktop app. Its code is split by responsibility: the application shell wires screens together, each screen owns its own controls, the service owns business rules and in-memory state, and the repository owns SQL persistence.

## Dependency map

```mermaid
flowchart TD
    Launcher --> App[PosApp: application shell]
    App --> Views[ui.view: module views]
    Views --> Common[ui.common: shared UI helpers]
    Views --> Service[PosService: business rules and runtime state]
    App --> Service
    Service --> Algo[algorithm: hand-written sorts, searches, recursion]
    Service --> Sec[security: PasswordHasher]
    Service --> Port[PosRepository: persistence contract]
    SqliteRepo[SqlitePosRepository: SQL mapping] -. implements .-> Port
    Service --> Models[Domain records]
    SqliteRepo --> Models
```

The dependency direction is intentional. `PosService` depends on the small `PosRepository` interface. `PosApp`, the composition root, chooses `SqlitePosRepository` and passes it into the service. The service and its algorithms operate on the same in-memory records regardless of persistence details.

## Source layout

| Location | Responsibility |
|---|---|
| `src/main/java/com/kofi/pos/Launcher.java` | Plain entry point (used by `run.cmd`, `javafx:run`, IDEs); starts `PosApp`. |
| `src/main/java/com/kofi/pos/app/PosApp.java` | JavaFX startup, sign-in, navigation, application shell, and screen wiring. |
| `src/main/java/com/kofi/pos/ui/view/` | One view per module plus the receipt dialog. |
| `src/main/java/com/kofi/pos/ui/common/` | Shared field/layout helpers (`UiSupport`) and the product table. |
| `src/main/java/com/kofi/pos/service/PosService.java` | In-memory collections (ArrayList, LinkedList, Stack, Queue), validation, product/sale/stock rules, reports, reset. |
| `src/main/java/com/kofi/pos/algorithm/` | Hand-written insertion/selection sort, linear/binary search, recursive sum. |
| `src/main/java/com/kofi/pos/security/PasswordHasher.java` | PBKDF2 password verification. |
| `src/main/java/com/kofi/pos/util/Money.java` | Single definition of money rounding. |
| `src/main/java/com/kofi/pos/model/` | Immutable product, sale, stock, settings, and activity records shared by layers. |
| `src/main/java/com/kofi/pos/data/PosRepository.java` | Narrow load/save/reset contract consumed by the service. |
| `src/main/java/com/kofi/pos/data/SqlitePosRepository.java` | Loads and saves the six record types in a local SQLite database, imports existing CSV data once, and restores defaults. |
| `src/main/resources/seed/seed.sql` | The default dataset used on first launch and by Settings > Reset data to default. |

## SOLID in this project

- **Single responsibility:** `PosApp` coordinates the app; each `*View` builds one module; `PosService` enforces store rules; `algorithm/` holds only algorithms; `PasswordHasher` only checks passwords; `SqlitePosRepository` maps records to database rows.
- **Open/closed:** alternate storage can be added as another `PosRepository`. The service does not need to know how its snapshot is persisted.
- **Liskov substitution:** any `PosRepository` implementation can replace `SqlitePosRepository` while honoring the same load/save contract.
- **Interface segregation:** the repository port exposes only the operations the service needs: load and save a `StoreData` snapshot.
- **Dependency inversion:** business rules depend on `PosRepository`; SQL-specific code sits behind that boundary. Wiring happens in `PosApp.start`.

The views receive the application service and small callbacks for navigation and notices. They do not read or write files. Domain records are immutable, so a stock change replaces a `Product` value instead of allowing UI code to mutate shared state.

## Main data flows

### Startup and sign-in

1. `PosApp.start` constructs `SqlitePosRepository` and `PosService`.
2. The service asks the repository for one `StoreData` snapshot.
3. The repository creates or opens `data/kofi-pos.db`, then converts the six SQL tables into model records.
4. The service initializes its collections, then rebuilds recent activity and the low-stock queue.
5. The login screen calls `PosService.authenticate`; password verification uses the stored salt and hash.

### Checkout

1. `PointOfSaleView` handles search, cart editing, tender input, and display validation.
2. The checkout action calls `PosService.checkout` with the cashier, discount, and tender.
3. The service validates the cart and money values, checks live stock again, then creates receipt lines using the current product name and price.
4. It reduces stock, records matching stock movements and dashboard activity, clears the cart, and refreshes low-stock alerts.
5. `saveAll` sends an immutable snapshot through `PosRepository`; the view then opens `ReceiptDialog`.

### Manual stock movement or sale void

Both actions validate at the service boundary, replace affected immutable `Product` records, append audit rows to the movement log, refresh the restock queue, and persist a snapshot. Voiding keeps the original sale and its lines, marks the sale voided, and restores sold units through new stock-in movements.

### Reset to default data

`SettingsView` asks for confirmation, then calls `PosService.resetToDefaults()`. The service asks `PosRepository.resetToDefaults()`, which replaces all six record types in one database transaction with the packaged defaults from `src/main/resources/seed/seed.sql`, and returns the restored snapshot. The service then replaces all in-memory collections (and empties the cart, activity stack and restock queue) via `applySnapshot`.

### SQL persistence

`SqlitePosRepository` maps products, users, sales, sale lines, stock movements, and settings to six SQLite tables. It stores money as decimal text, timestamps as ISO text, and list positions explicitly so loaded snapshots preserve their order. Saving and resetting run in transactions. On first startup only, any existing legacy CSV records are imported; those files are removed after the migration commits.

## Adding a change

1. Put shared business rules in `PosService`, not in a button handler.
2. Put new screen controls in the relevant `ui/view/*View` class (shared helpers go in `ui/common`).
3. Add or update a model record when the value is part of the store domain.
4. If persisted fields change, update the matching SQL schema, row mapping, and seed statement together.
5. Keep the repository interface narrow. Add a method only when the business service needs a new persistence operation.
6. Preserve the assignment-required collections and handwritten algorithms where they are exercised by the UI.

## Build and launch

Use JDK 17 or newer and Maven:

```powershell
mvn -DskipTests compile
mvn javafx:run
```

The Java sources target release 17. Maven is required for the normal developer workflow because it resolves JavaFX and launches the desktop app.
