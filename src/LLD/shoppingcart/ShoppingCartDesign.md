# Shopping Cart System - Design & Implementation

## Overview
This document describes a small, extensible Shopping Cart system focusing on OOP principles, validations, and basic concurrency handling.

## Components
- Product: immutable product model (id, name, price)
- CartItem: product + quantity
- Cart: holds CartItems for a single user/session. Not thread-safe.
- ProductCatalog: abstraction for product lookup and stock management
- InMemoryProductCatalog: simple concurrent in-memory implementation
- CartService: thread-safe service managing carts and coordinating with ProductCatalog

## Key behaviors
- addItem(productId, quantity)
- viewCart()
- removeItem(productId)
- checkout()

## Concurrency
- CartService uses per-cart locks (one Object per cartId) for synchronization to avoid global locking.
- ProductCatalog uses AtomicInteger for stock counts and CAS for reservation.

## Edge cases handled
- Adding out-of-stock items -> throws IllegalStateException
- Cart exceeding item limit -> throws IllegalStateException
- Checkout with empty cart -> throws IllegalStateException

## Extensibility
- ProductCatalog is an interface: replace with DB-backed or distributed inventory service.
- CartService can be extended to support discounts, promotions, and events.
- For distributed systems, use optimistic reservation or a dedicated inventory service.

## How to run/demo
Place the sources under `src/LLD/shoppingcart`. Compile and run a demo (not included) or integrate with tests.

## Notes
This implementation is focused on clarity and interview-readiness rather than production-ready features like distributed locks, idempotency, or eventual consistency.
