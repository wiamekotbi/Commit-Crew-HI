# Issue #50: Customer order summary

Implements the read-only summary described by R-PRC-02 / UC-PRC-02 in the Phase III report.

## Customer interface

In **My orders**, delivered or refunded orders have a **Summary** link to
`/order-summary.html?orderId=...`. The page uses the existing HTML/JavaScript frontend,
dashboard styles, session cookie, and API helper. This checkout contains no React application.

The page shows original item names, quantities, unit prices, line subtotals, the saved
pricing breakdown, and the delivery timestamp. Refund entries appear separately without
subtracting from or replacing the original charge. Loading, retry, access errors, missing
orders, and unavailable summaries are handled explicitly.

## Endpoint

`GET /api/orders/{orderId}/summary`

The caller must have a valid `BAKEWAY_SESSION_ID` belonging to a customer. Ownership
comes from the server-side session; the endpoint accepts no customer ID.

| Status | Meaning |
|---|---|
| 200 | Own delivered/refunded order, with recorded charge and delivery event |
| 401 | Missing or invalid session |
| 403 | Authenticated role is not CUSTOMER |
| 404 | Order does not exist or belongs to another customer, as required by UC-PRC-02 flow 3a |
| 409 | Not delivered, billing integration missing, charge missing, or delivery timestamp missing |

The response contains `orderId`, `status`, `items`, `chargeId`, `pricing`, `deliveredAt`,
and `refunds`. `pricing` reuses the existing `PriceBreakdown` record. Each item contains
`itemName`, `quantity`, `unitPrice`, and `subtotal`.

The existing console filter logs success and error responses as `VIEW_ORDER_SUMMARY`.
The summary service performs no database writes.

## Integration dependency: issue #51 and billing ledger

This checkout has pricing calculation but no persisted charge/refund model or ledger.
Issue #50 does not create those records, recalculate a charge on read, or modify delivery logic.
Until billing is connected, eligible orders return HTTP 409 with an explicit unavailable message.
This is not yet a working end-to-end billing demonstration.

The billing integration must supply one Spring bean implementing `OrderSummaryBilling`:

- `findCharge(orderId)` returns the existing charge ID and its original saved `PriceBreakdown`,
  or `Optional.empty()` if no charge exists. It must not calculate or create a charge.
- `findRefunds(orderId)` returns existing separate refund records, or an empty list when none
  are available. Each record contains `refundId`, `amount`, `refundedAt`, and `reason`.
  It must not replace or modify the original charge.

`PricingService.calculatePrice` remains the calculation used when the charging integration
creates a charge. The summary deliberately does not invoke it: configuration could change
after delivery, and a receipt must retain its original fees and total.

The delivery timestamp is read from the earliest recorded ORDER transition into `delivered`,
using `EventRepository`'s chronological history. Current time, order creation time, refund
time, and the order's last-update time are never substituted for delivery time.

The current order enum has no `refunded` state. The reader and frontend accept that status
when the refund feature supplies it; this issue does not introduce the refund transition.
Refunded-reader tests use an order test double and do not claim the refund lifecycle exists.

## Verification

From `TheBakeway/backend`:

```bash
./mvnw '-Dtest=OrderSummaryServiceTest,OrderSummaryControllerTest,CommandNamesTest' test
node --test src/test/js/order-summary.test.cjs
./mvnw test
```

Backend tests cover original order-line snapshots after menu edits, saved pricing with and
without surcharge, separate refunds, actual delivery-event timestamps, ownership, roles,
missing orders, undelivered orders, missing billing/charge/timestamp, endpoint responses,
and console logging. Endpoint tests exercise the real summary service with mocked persistence
and billing. They do not prove live database or ledger integration.

Frontend tests execute the production dashboard helper and summary script with a simulated DOM
and HTTP responses. They cover loading, amounts, refunds, errors, session handling, retry,
HTML escaping, and summary links from customer history. They are not visual browser tests.

The latest focused run passed 68 backend tests covering summary, pricing, and console behavior,
and all 15 frontend tests. The broader run excluding `TheBakewayApplicationTests` passed 440
backend tests. The database-context test previously errored because the database environment
variables were unset; it was not rerun against the shared database. A successful live receipt
and full database-backed suite still require the billing adapter and configured database.
