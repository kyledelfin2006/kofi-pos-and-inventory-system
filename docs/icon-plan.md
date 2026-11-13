# Kofi product icons — plan

## The artwork

Seven square illustrations (1024–2048 px) in `assets/`. Each is a drawing on a soft cream tile with a darker circle behind it, a brown outline and a few sparkles. The tile colour changes per item (latte beige, sandwich yellow), so the **whole square is the icon**: it is never cropped to a circle or recoloured, it is only scaled and given a small corner radius.

| Item | Source file | Packaged as | Used by |
|---|---|---|---|
| Spanish Latte | `Spanish_Latte_Icon.png` | `spanish-latte.png` | 4 sizes |
| Caramel Latte | `Caramel_Latte_Icon.png` | `caramel-latte.png` | 4 sizes |
| Café Latte | `Cafe_Latte_Icon.png` | `cafe-latte.png` | 4 sizes |
| Americano | `Americano_Icon.png` | `americano.png` | 4 sizes |
| Chicken Fillet with Rice | `Chicken_Fillet_And_Rice_Icon.png` | `chicken-fillet-with-rice.png` | 1 |
| Carbonara | `Carbona_Icon.png` | `carbonara.png` | 1 |
| Chicken Sandwich | `Chicken_Sandwich_Icon.png` | `chicken-sandwich.png` | 1 |
| French Fries | — none yet — | `placeholder-food.png` | 1 |

Packaged copies are 320 px so they stay sharp at the largest size we show (about 150 px on a high-DPI screen) without loading 2 MB images.

## How a product finds its icon

1. The product's `image` column, if filled (file in `assets/products/` next to the app, or in resources). This stays the override for custom products.
2. Otherwise the icon named after the product: `Café Latte` → `cafe-latte.png` (lower case, accents removed, spaces to hyphens). All sizes and temperatures of one drink share one icon.
3. Otherwise the Coffee or Food placeholder. A missing picture never breaks a screen.

Because step 2 uses the name, the existing `data/` files and the seed CSV need no change. Adding a new menu item later only needs a new `<name>.png`.

## Where icons appear

| Screen | Treatment | Why |
|---|---|---|
| **Point of sale** | Replace the product table with a **tile grid**. Each tile: icon, name, price (or "from ₱90"), stock badge. Category filter chips above (All, Coffee, Food). | Cashiers recognise a picture faster than they read a row; large tap targets are the POS standard. |
| **Point of sale — variants** | Drinks have 4 variants (Hot 8 / Hot 12 / Iced 16 / Iced 22). One tile per drink; clicking it opens a small size picker with the price on each button. Food adds straight to the cart. | 20 products become 7 tiles, so nothing needs scrolling. |
| **Point of sale — cart** | 32 px icon beside each line. | The cashier can confirm the order at a glance. |
| **Products** | 40 px icon in the table, and a larger preview beside the edit form. | The table is for maintenance, so it stays a compact table; the preview shows what the cashier will see. |
| **Stock** | Icon in the product picker and in the movement history. | Prevents moving stock on the wrong size or item. |
| **Sales & Receipts** | Not used. | A receipt is a financial record; pictures add noise. |
| **Dashboard** | Icon in the restock queue rows. | Spots which item is running low without reading SKUs. |

## Tile behaviour (POS and inventory conventions)

- **One click adds one unit.** Keyboard: type in the search box, Enter adds the first match, as today.
- **Stock is always visible.** A tile shows nothing extra when stock is healthy, an amber "Low · 8 left" when at or below the reorder level, and a greyed, unclickable "Out of stock" when 0. Colour is never the only signal; the text is always there.
- **Search and chips combine.** Typing filters the grid by name or SKU using the existing linear search; a chip narrows to one category.
- **Cart feedback.** Adding shows the existing green notice in the top bar and the cart line appears at once.
- **Empty states say what to do.** "No products match 'xyz'. Clear the search to see everything."
- **Focus is visible.** Tiles are buttons, reachable with Tab, activated with Space or Enter.

## Visual rules

- Tile: white card, 1 px border `#e4e0dc`, 9 px radius, icon on top filling the card width, then name (13 px, 600), then price (15 px, 700, tabular).
- Icon corners rounded 7 px; no drop shadow on the icon itself.
- Hover: border changes to the Kofi brown `#7b4827`. Selected/pressed: slightly darker background. No bouncing or scaling.
- Low-stock badge uses the existing warning colour `#936619` on a pale amber; out-of-stock uses the disabled grey.
- Grid is a wrapping flow of fixed-width tiles (150 px) so it fills the available width at any window size.

## Build order (one commit each)

1. This plan and the source artwork.
2. Packaged icons and `AssetLoader` lookup by name.
3. Product table thumbnails and the Products form preview.
4. Reusable tile grid and size picker components.
5. Point of sale screen uses the tile grid, with category chips.
6. Cart, Stock and Dashboard thumbnails.
7. Docs update.
