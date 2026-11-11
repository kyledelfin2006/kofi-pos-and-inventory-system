package com.cc104.pos.model; // Domain-model package.

/** Store identity and receipt footer shown in the desktop application. */
public record Settings(
        String storeName,      // Shown in the sidebar, window title and receipts.
        String storeAddress,   // Printed on the receipt header.
        String receiptFooter) { } // Printed at the bottom of receipts, e.g. "Salamat po!".
