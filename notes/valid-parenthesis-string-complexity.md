# Valid Parenthesis String: Complexity Notes

Implementation: [`ValidParenthesisString.java`](../src/DSA/Apple/ValidParenthesisString.java)

## Problem and notation

Determine whether a string containing `(`, `)`, and `*` can form valid
parentheses. Each `*` can represent `(`, `)`, or an empty string.

- `n`: length of the input string.
- `w`: number of stars, where `0 <= w <= n`.
- `open`: number of unmatched opening parentheses in a prefix.
- Auxiliary space excludes the input but includes recursion, arrays, and stacks.

The methods return a boolean; they do not construct or print each interpretation.
Therefore, there is no sentence-building or output-length factor in these bounds.

## Summary

| Approach | Method | Time | Auxiliary space |
|---|---|---|---|
| Backtracking | `checkWithBacktracking` | `O(3^n)` upper bound; also `O(n * 3^w)` | `O(n)` |
| Memoization | `checkWithMemoization` | `O(n^2)` | `O(n^2)` |
| Bottom-up DP with rolling rows | `checkWithDynamicProgramming` | `O(n^2)` | `O(n)` |
| Two stacks | `checkWithStacks` | `O(n)` | `O(n)` |
| Greedy balance range | `checkValidString` | `O(n)` | `O(1)` |

These are worst-case upper bounds, not a claim that every input takes that much
time. In particular, pruning and short-circuit evaluation can make backtracking
much faster. The exponential bounds are not claimed to be tight for the pruned
implementation.

## 1. Backtracking: count the recursion-tree nodes

At each position:

- `(` or `)` produces one recursive call.
- `*` produces up to three recursive calls.

Each call does constant work apart from its recursive calls: inspect a character,
update a count, and check a condition. The code passes the same string without
copying it.

### Time

Ignoring pruning, a recursion tree with branching factor at most three and depth
`n` contains at most:

```text
1 + 3 + 3^2 + ... + 3^n
= (3^(n + 1) - 1) / 2
= O(3^n)
```

We do not multiply this bound by `n`: the geometric sum already counts work at
every level, not just at the leaves.

An alternative bound uses the number of stars. There are at most `3^w` complete
interpretations, each involving at most `n` positions:

```text
O(n * 3^w)
```

Both bounds are valid; use whichever is more informative for the input. With no
stars, there is only one path, so the upper bound becomes `O(n)`.

The implementation reduces exploration by:

- Rejecting `open < 0`.
- Rejecting `open > remaining characters`.
- Stopping at the first successful interpretation via Java's short-circuit `||`.

For example, an all-star string succeeds along the first path by treating every
star as empty. It does not explore every branch.

### Space

Branches execute sequentially, not simultaneously. At most `n + 1` recursive
calls are active on a root-to-leaf path, each holding constant-size local state.

```text
Auxiliary space = O(n)
```

**Key distinction:** time counts all visited calls; stack space counts the
maximum number of simultaneously active calls.

## 2. Memoization: count distinct states

The result of a recursive call depends only on:

```text
(index, open)
```

Different choices of earlier stars can reach the same state. Caching its result
prevents solving the same suffix repeatedly.

### Time

There are at most `n` nonterminal indices and `n + 1` possible balance values:

```text
Number of states = O(n^2)
Transitions per state <= 3 = O(1)
Time = states * work per state = O(n^2)
```

Not every pair is reachable, but the upper bound remains quadratic.

The actual implementation also allocates a `Boolean[n][n + 1]` table. Allocating
and initializing this table takes quadratic time, even if recursion immediately
finds an answer. Memoization here is therefore not guaranteed to be faster than
plain backtracking on easy inputs.

### Space

```text
Memo table:      O(n^2)
Recursion stack: O(n)
Total:          O(n^2 + n) = O(n^2)
```

Memoization removes repeated computation; it does not remove recursion depth.

## 3. Bottom-up DP: count loop iterations

`reachable[open]` indicates whether a balance is possible after the processed
prefix. For each new character, compute the next set of reachable balances.

### Time

For prefix index `i`, the inner loop checks balances from `0` through `i`:

```text
1 + 2 + ... + n = n * (n + 1) / 2 = O(n^2)
```

Each checked balance has at most three constant-time transitions.

The implementation also clears the next row with `Arrays.fill` on every
iteration. Each row has `n + 1` entries:

```text
n clears * O(n) per clear = O(n^2)
```

Combining both costs still gives `O(n^2)`, not `O(n^3)`: the clearing and balance
loop run sequentially inside each outer iteration.

### Space

Only the previous and current prefix rows are needed:

```text
Two boolean arrays of size n + 1 = O(n)
```

Keeping all prefix rows would instead require `O(n^2)` space without changing
the time bound.

## 4. Two stacks: count total pushes and pops

Maintain indices of unmatched opening parentheses and available stars.

For each `)`, use an unmatched `(` if possible; otherwise use a star as `(`.
After the scan, match each remaining `(` with a star to its right.

Indices matter: `*(` is invalid because the earlier star cannot close the later
opening parenthesis.

### Time

Although the method contains a scan followed by a matching loop:

```text
Scan:              O(n)
Remaining matches: O(n)
Total:             O(n + n) = O(n)
```

Each index is pushed at most once and popped at most once. `ArrayDeque` operations
take amortized `O(1)` time, so all stack operations together take `O(n)`.

Two loops do not imply quadratic time. Even a nested loop can be linear when the
total number of removals is bounded by the number of inserted elements.

### Space

At most `n` indices are stored across both stacks:

```text
Auxiliary space = O(n)
```

## 5. Greedy: compress reachable states into an interval

Track:

- `minOpen`: smallest possible nonnegative unmatched-opening count.
- `maxOpen`: largest possible unmatched-opening count.

| Character | Change to minimum | Change to maximum |
|---|---|---|
| `(` | `+1` | `+1` |
| `)` | `-1` | `-1` |
| `*` | `-1` | `+1` |

After each character:

1. If `maxOpen < 0`, no interpretation can match the closing parentheses.
2. Clamp `minOpen` to zero, discarding negative balances.

The reachable valid balances form a continuous integer interval. Therefore,
storing its endpoints preserves the information needed for a boolean answer;
there is no need to store every balance as DP does.

At the end, `minOpen == 0` means that at least one interpretation is balanced.

### Time

The loop visits each character once and performs constant work:

```text
n iterations * O(1) work = O(n)
```

Early rejection can take `O(1)` time, but the worst case requires the full scan.
This is asymptotically optimal: in the worst case, validity depends on the final
character, so a correct algorithm must inspect the entire input.

### Space

The method stores only two balance counters, a loop index, and a character:

```text
Auxiliary space = O(1)
```

## Interview-ready comparison

> Backtracking explores alternative star assignments and can take exponential
> time, with only linear recursion space. Memoization reduces repeated work to
> quadratic index-and-balance states. Bottom-up DP uses the same state model but
> compresses storage to two linear rows. Two stacks achieve linear time while
> retaining indices. Greedy compresses all reachable balances into a minimum and
> maximum, achieving linear time and constant space.
