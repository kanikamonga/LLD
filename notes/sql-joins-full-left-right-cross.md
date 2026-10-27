# SQL Joins: FULL, LEFT, RIGHT, and CROSS

## Overview

A join combines rows from two tables. Outer joins preserve unmatched rows from one or both sides, filling columns from the missing side with `NULL`.

Examples use:

```text
customers(customer_id, name)
orders(order_id, customer_id, amount, status)
```

## LEFT JOIN

`LEFT JOIN` (or `LEFT OUTER JOIN`) returns every row from the left table and matching rows from the right table. If there is no match, right-table columns are `NULL`.

```sql
SELECT c.customer_id, c.name, o.order_id, o.amount
FROM customers c
LEFT JOIN orders o
  ON o.customer_id = c.customer_id;
```

### Real-world use

List all customers, including customers who have not placed an order. This is useful for customer reports, onboarding follow-ups, and identifying inactive accounts.

To find only customers without orders:

```sql
SELECT c.customer_id, c.name
FROM customers c
LEFT JOIN orders o
  ON o.customer_id = c.customer_id
WHERE o.order_id IS NULL;
```

## RIGHT JOIN

`RIGHT JOIN` (or `RIGHT OUTER JOIN`) returns every row from the right table and matching rows from the left table. If there is no match, left-table columns are `NULL`.

```sql
SELECT c.customer_id, c.name, o.order_id, o.amount
FROM customers c
RIGHT JOIN orders o
  ON o.customer_id = c.customer_id;
```

### Real-world use

Keep every order in a report, including orders that have no matching customer record. This can help audit orphaned records after a data migration.

`RIGHT JOIN` can usually be rewritten as a `LEFT JOIN` by swapping the table order. Many teams prefer `LEFT JOIN` consistently for readability.

## FULL JOIN

`FULL JOIN` (or `FULL OUTER JOIN`) returns all rows from both tables. Matching rows are combined; unmatched rows from either side are included with `NULL` values for columns from the other side.

```sql
SELECT c.customer_id, c.name, o.order_id, o.amount
FROM customers c
FULL OUTER JOIN orders o
  ON o.customer_id = c.customer_id;
```

### Real-world use

Reconcile two datasets—for example, compare customer records with imported order records to find both customers without orders and orders without valid customers.

### Database support

`FULL OUTER JOIN` is not supported natively by every database. In MySQL, for example, one approach is to combine a left join with unmatched rows from the right side:

```sql
SELECT c.customer_id, c.name, o.order_id, o.amount
FROM customers c
LEFT JOIN orders o
  ON o.customer_id = c.customer_id

UNION ALL

SELECT c.customer_id, c.name, o.order_id, o.amount
FROM orders o
LEFT JOIN customers c
  ON c.customer_id = o.customer_id
WHERE c.customer_id IS NULL;
```

The first query includes all customers and their matching orders. The second adds only orders without a matching customer, so matched rows are not duplicated.

## CROSS JOIN

`CROSS JOIN` returns the Cartesian product: every row from the first table paired with every row from the second table. It has no matching condition.

```sql
SELECT s.size, color.name AS color
FROM sizes s
CROSS JOIN colors color;
```

If there are 4 sizes and 3 colors, the result contains 12 rows.

### Real-world use

Generate all possible product variants, such as every size-and-color combination, or create a date-and-store grid for reporting.

Use it intentionally: the result size is `rows_in_first_table × rows_in_second_table`, which can grow very large.

## Quick comparison

| Join | Preserves unmatched rows from | Typical use |
|---|---|---|
| `LEFT JOIN` | Left table | Keep all primary entities, such as all customers |
| `RIGHT JOIN` | Right table | Keep all rows from the right-side dataset; often rewritten as `LEFT JOIN` |
| `FULL OUTER JOIN` | Both tables | Reconciliation and finding unmatched rows on either side |
| `CROSS JOIN` | Every possible pair | Generate combinations or grids |

## Important outer-join filter caveat

A filter on the nullable side of an outer join can accidentally remove unmatched rows.

For example, this query excludes customers without a paid order because the `WHERE` clause rejects `NULL` values:

```sql
SELECT c.customer_id, c.name, o.order_id
FROM customers c
LEFT JOIN orders o
  ON o.customer_id = c.customer_id
WHERE o.status = 'paid';
```

If all customers should remain and only paid orders should match, put the condition in the `ON` clause:

```sql
SELECT c.customer_id, c.name, o.order_id
FROM customers c
LEFT JOIN orders o
  ON o.customer_id = c.customer_id
 AND o.status = 'paid';
```

## Interview-ready summary

- `LEFT JOIN`: preserve every left-side row, matching right-side rows where available.
- `RIGHT JOIN`: preserve every right-side row; commonly rewritten by swapping tables into a `LEFT JOIN`.
- `FULL OUTER JOIN`: preserve unmatched rows from both sides; useful for reconciliation.
- `CROSS JOIN`: produce every possible pair; useful for deliberate combinations, but can create a large result.
- For outer joins, place right-side match restrictions in `ON` when unmatched left-side rows must remain.
