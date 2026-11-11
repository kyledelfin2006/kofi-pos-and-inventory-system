---
name: Kofi
description: A calm offline counter for sales and stock.
colors:
  primary: "#7b4827"
  primary-hover: "#693e21"
  primary-active: "#221b16"
  canvas: "#f5f4f2"
  surface: "#ffffff"
  sidebar: "#1a1411"
  text: "#1c1612"
  muted-text: "#ab6536"
  border: "#e4e0dc"
  error: "#b33535"
  warning: "#936619"
  disabled: "#c7c0b8"
typography:
  display:
    fontFamily: "Segoe UI, Arial, sans-serif"
    fontSize: "25px"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "Segoe UI, Arial, sans-serif"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.4
  label:
    fontFamily: "Segoe UI, Arial, sans-serif"
    fontSize: "12px"
    fontWeight: 600
rounded:
  sm: "6px"
  md: "7px"
  lg: "9px"
  pill: "16px"
spacing:
  xs: "6px"
  sm: "8px"
  md: "12px"
  lg: "18px"
  xl: "24px"
  xxl: "30px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
    padding: "9px 14px"
  button-secondary:
    backgroundColor: "#f1ede8"
    textColor: "#6a3e21"
    rounded: "{rounded.sm}"
    padding: "9px 14px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.sm}"
    padding: "8px 10px"
  navigation-active:
    backgroundColor: "#6c4022"
    textColor: "{colors.surface}"
    rounded: "{rounded.sm}"
    padding: "11px 13px"
---

## Overview

**Creative North Star: "A calm checkout counter"**

The interface keeps sale math and stock status easy to scan during everyday shop work. A coffee-brown accent marks actions and active navigation while a bright, quiet work surface keeps tables and form values legible. The same shell and control language carries across checkout, product maintenance, stock movements, receipts, reports, and settings.

**Key Characteristics:**
- Quiet light workspace framed by a near-black navigation rail.
- Money and quantity values use consistent, clearly grouped numerals.
- Errors, warnings, disabled fields, and success messages have distinct states.
- Familiar JavaFX controls support keyboard-first operation.

**The Counter Rule.** Let checkout totals and available stock carry the strongest emphasis on the POS screen.

## Colors

Coffee brown is reserved for primary actions and active states. The near-black rail anchors navigation. Neutral white and soft warm grey surfaces carry tables and forms; red and amber are reserved for errors and warnings.

**The Signal Rule.** Use accent color to identify an action or a state, never as decoration.

## Typography

Use the native sans-serif stack for every screen. Headings separate modules from field labels without theatrical scale changes. Keep currency and quantity values on a compact, readable scale.

**The Working Scale Rule.** Keep labels and table data compact; reserve the largest type for the current screen heading and key totals.

## Layout

Use a persistent left navigation rail and a simple top bar. The POS places the product search and results beside the active cart and payment fields. Product and stock work uses a table beside its editing form; detail screens keep receipt data close to the selected record.

**The Flow Rule.** Keep the next action beside the data it changes.

## Elevation & Depth

Prefer flat surfaces separated by thin neutral borders. The login panel may use a soft shadow to lift it from its background; other panels do not need decorative depth.

## Shapes

Use gently rounded corners for fields, buttons, and panels. Read-only fields use the same geometry with a muted fill to distinguish them from editable controls.

## Components

Buttons share one shape and clear primary, secondary, quiet, and destructive roles. Inputs use a neutral border, a visible brown focus state, and a red invalid state. Tables use restrained striping and a tinted selection row. Navigation uses light text on the dark rail with a stronger green active row.

## Do's and Don'ts

### Do:
- **Do** keep field errors directly beside the field that needs correction.
- **Do** use the primary brown for the action that advances the current task.
- **Do** retain a grey treatment for read-only values.

### Don't:
- **Don't** use accent colors to decorate inactive panels.
- **Don't** replace standard controls with custom interactions that slow checkout.
- **Don't** make historical receipt data editable.
