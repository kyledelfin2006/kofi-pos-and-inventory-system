# Tindahan POS

An offline JavaFX point-of-sale and stock-management desktop application for the CC 104 final project. Runtime records are held in Java collections and saved locally as CSV; there is no SQL database or network service.

## Run

Install JDK 17 or newer and Maven, then run from the project directory:

```powershell
mvn javafx:run
```

First launch copies the demo CSV files from `src/main/resources/seed/` to `data/`. Changes are saved in `data/` and survive restarts. Back up that folder to preserve the store's records.

Demo sign-in: **admin** / **admin123**

## Modules

- Dashboard: today's completed sales, transaction count, inventory at cost, pending low-stock queue, recent activity.
- Point of Sale: 250 ms name/SKU search, quantity editing, discount, cash, change, receipt creation.
- Products: add, view, update, delete/deactivate, search, and manual sort.
- Stock: stock in, stock out, downward adjustment, and movement history.
- Sales & Receipts: search and sort receipts, view line details, void with a reason, restore stock.
- Reports: date-filter completed sales and view inventory cost value.
- Settings: store name, address, and receipt footer.

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

