package com.cc104.pos.model;

/** Store identity and receipt footer shown in the desktop application. */
public record Settings(String storeName, String storeAddress, String receiptFooter) { }
