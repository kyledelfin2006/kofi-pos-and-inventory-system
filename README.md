# Tindahan POS

An offline JavaFX point-of-sale and stock-management desktop application for the CC 104 final project. Runtime records are held in Java collections and saved locally as CSV; there is no SQL database or network service.

## Run

Install JDK 17 or newer, then run from the project directory:

```powershell
.\run.cmd
```

`run.cmd` calls Maven (`javafx:run`), so Maven recompiles any changed source files on every launch; code edits are picked up automatically. It uses `mvn` from your PATH if present; otherwise it falls back to a Maven copy cached in `%USERPROFILE%\.m2\wrapper`, and sets `JAVA_HOME` from `C:\Program Files\Java\jdk-*` when unset. Extra arguments are passed to Maven, e.g. `.\run.cmd -DskipTests compile`.

**Running from an IDE:** open the project as a Maven project (so the JavaFX dependencies load), then run `com.cc104.pos.Launcher` (also the `run.cmd` / `javafx:run` entry point), not `PosApp`. Running `PosApp` directly fails with "JavaFX runtime components are missing" because it extends `Application`.

If you see "Maven not found", download Maven from <https://maven.apache.org/download.cgi>, unzip it, and add its `bin` folder to PATH (there is no winget package for it). Then plain `mvn javafx:run` also works.

First launch copies the demo CSV files from `src/main/resources/seed/` to `data/`. Changes are saved in `data/` and survive restarts. Back up that folder to preserve the store's records. To return to the sample data at any time, use Settings > Reset data to default.

Demo sign-in: **admin** / **admin123**

## Developer guide

The app separates JavaFX screens, store rules, domain records, and CSV persistence. Start with these guides before changing a feature:

- [Architecture and SOLID decisions](docs/architecture.md)
- [Line-by-line code walkthrough](docs/code-walkthrough.md)
- [Requirements traceability: where each data structure and algorithm is used](requirements.md)

The short source map is:

- `Launcher` is the entry point; `app/PosApp` starts the app and wires the module views.
- `ui/view/*View` builds one screen and forwards user actions to `PosService`; `ui/common` holds shared UI helpers.
- `algorithm/` holds the hand-written sorts, searches and recursion; `security/` and `util/` hold password checking and money rounding.
- `PosService` owns in-memory store state and business rules.
- `PosRepository` defines persistence; `CsvPosRepository` maps records to CSV; `CsvStore` handles file and CSV mechanics.
- `model` contains immutable domain records.

Compile with `mvn -DskipTests compile`; launch with `mvn javafx:run`.

## Modules

- Dashboard: today's completed sales, transaction count, inventory at cost, pending low-stock queue, recent activity.
- Point of Sale: 250 ms name/SKU search, quantity editing, discount, cash, change, receipt creation.
- Products: add, view, update, delete/deactivate, search, and manual sort.
- Stock: stock in, stock out, downward adjustment, and movement history.
- Sales & Receipts: search and sort receipts, view line details, void with a reason, restore stock.
- Reports: date-filter completed sales and view inventory cost value.
- Settings: store name, address, receipt footer, and **Reset data to default** (restores the CSVs in `src/main/resources/seed/` after confirmation).

## CC 104 structures and algorithms

- `ArrayList`: products, sales, sale lines, stock movements, and users.
- `LinkedList`: editable POS cart lines.
- `Stack`: most recent activity entries.
- `Queue`: low-stock restock alerts, presented FIFO.
- Linear search: product and receipt filters.
- Binary search: exact SKU lookup against a manually SKU-sorted snapshot.
- Insertion sort: product name and newest-sale ordering.
- Selection sort: stock quantity and sale total ordering.
- Recursion: inventory valuation at cost.

