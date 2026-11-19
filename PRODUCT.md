# Product

<!-- impeccable:product-schema 1 -->

## Platform

JavaFX desktop

## Stack

Java with JavaFX as requested; Maven is the build choice for this empty repository.

## Users

Cashiers and store owners at a small local shop. Cashiers record sales quickly; owners maintain products and stock, review receipts, and see basic reports.

## Product Purpose

An offline point-of-sale and stock-management desktop application that lets a small shop sell products, track inventory movements, and review sales without an external database. Success means a cashier can complete an accurately totaled sale and the owner can maintain inventory and inspect the resulting records.

## Positioning

Sales and stock movements are handled together in one offline Java desktop application, with records held in Java collections and persisted locally in SQLite.

## Operating Context

The system is intended for a single store and local desktop use. It uses Philippine pesos and must continue to work without network access. The project is a CC 104 final project and must demonstrate the required collections, searches, sorts, and recursive operation in real system flows.

## Capabilities and Constraints

The current direction is an offline JavaFX POS with login, dashboard, navigation, POS search and checkout, product management, stock movements, receipt detail and voiding, reports, and store settings. Runtime records stay in Java collections and are persisted in a local SQLite database. The provided textbox rules define validation, keyboard behavior, money formatting, field limits, and read-only fields. The assignment also requires add/view/update/delete/search/sort, ArrayList, LinkedList, Stack, Queue, linear search, binary search, two handwritten sorting algorithms, and recursion.

## Evidence on Hand

The user supplied the complete CC 104 project requirements and POS textbox MVP. No real store data, logo, or receipt samples were supplied; SQL seed records are clearly fictional demonstration data.

## Product Principles

- Keep common checkout work direct and keyboard-friendly.
- Keep sale, receipt, and stock history consistent.
- Keep validation visible at the field that needs attention.
- Keep local data easy to back up as one database file.
- Make required data structures and algorithms part of actual workflows.
