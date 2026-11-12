# Loyalty Points

Backend for a retail loyalty programme: customers earn points on purchases, redeem them for rewards,
and hold a tier based on recent spend. Points expire 12 months after they're earned.

Java 21 · Spring Boot 3.5 (Spring Framework 6.2) · SQLite via `JdbcClient` · JUnit 5.
Exposed as both a REST API and a CLI.

**Scope:** all Required, Extended and Stretch items are implemented: earn, redeem, balance, expiry,
tiers, refunds, soonest-expiring-first redemption, and a CLI.

## Running it

Requires **Java 21+** (`java -version` to check). Nothing else: no Maven install, no database server.
The Maven wrapper (`./mvnw`) downloads Maven on first use, and SQLite is an embedded file.

```sh
git clone https://github.com/icherpak24/loyalty-points.git
cd loyalty-points
./mvnw package          # compiles, runs the tests, builds target/loyalty-points.jar
```

`./points` is a small shell script around `java -jar target/loyalty-points.jar`. On Windows, use
`mvnw.cmd package` and run the jar directly, e.g. `java -jar target\loyalty-points.jar balance --user=alice`.

**From an IDE:** import as a Maven project and run `com.loyalty.LoyaltyApplication` with program
arguments such as `serve` or `balance --user=alice`.

### Quick demo

```sh
./points earn    --user=alice --purchase-id=order-1 --amount=150   # +150 points
./points redeem  --user=alice --reward=free-coffee                 # -100 points, 50 left
./points refund  --user=alice --purchase-id=order-1                # removes the 50 unspent; the 100 already spent become owed
./points balance --user=alice                                      # 0 available, 100 owed from future earnings
```

The last two lines show the refund trade-off described under [Design](#trade-offs).

### CLI

```sh
./points rewards
./points earn    --user=alice --purchase-id=order-123 --amount=42.50
./points balance --user=alice
./points redeem  --user=alice --reward=free-coffee
./points refund  --user=alice --purchase-id=order-123
```

Exit codes: `0` success, `1` request rejected by a business rule (e.g. insufficient points), `2` usage error.

### REST API

```sh
./points serve                # http://localhost:7070  (or: ./points serve --port=8080)
```

| Method | Path                                                            | Body                                        | Success |
|--------|-----------------------------------------------------------------|---------------------------------------------|---------|
| GET    | `/points/rewards`                                               |                                             | 200     |
| GET    | `/points/customers/{customerId}/balance`                        |                                             | 200     |
| POST   | `/points/customers/{customerId}/purchases`                      | `{"purchaseId":"order-1","amount":42.50}`   | 201     |
| POST   | `/points/customers/{customerId}/purchases/{purchaseId}/refund`  |                                             | 200     |
| POST   | `/points/customers/{customerId}/redemptions`                    | `{"rewardId":"free-coffee"}`                | 201     |

```sh
curl -X POST localhost:7070/points/customers/alice/purchases \
     -H 'Content-Type: application/json' -d '{"purchaseId":"order-1","amount":120.50}'

curl localhost:7070/points/customers/alice/balance
# {"customerId":"alice","availablePoints":120,"pointsOwed":0,"tier":"SILVER","rollingSpend":120.50}
```

Errors return a stable code plus a readable message, e.g. `{"error":"INSUFFICIENT_POINTS","message":"..."}`:
`400` invalid input · `404` unknown customer/reward/purchase · `409` duplicate purchase or repeat refund ·
`422` insufficient points · `500` anything unexpected (logged, details not leaked).

Both the CLI and the server use the SQLite file `./loyalty.db` (override with `LOYALTY_DB=/path/to.db`).
The schema is created automatically on start-up, and a small reward catalog is seeded.

### Tests

```sh
./mvnw test
```

All tests run against the real Spring context and a temporary SQLite file (see `IntegrationTest`).
Tables are wiped before each test.

- `LoyaltyServiceTest`: the business rules (earning, redemption, expiry boundaries, tiers, refunds),
  driven by a controllable clock so expiry is tested without waiting a year.
- `ConcurrencyTest`: 10 threads try to spend the same 100 points at once; exactly one succeeds.
- `HttpApiTest`: end to end over HTTP, covering routing, JSON shape, and error-to-status mapping.

## Design

### Structure

```
LoyaltyApplication  entry point: `serve` starts the web server; other commands run one CLI command and exit
api/          LoyaltyController (REST routes) + ApiExceptionHandler      ┐ thin adapters,
cli/          Cli: argument parsing and human-readable output            ┘ no business logic
service/      LoyaltyService: every business rule, @Transactional per operation
persistence/  one small JdbcClient repository per table
config/       LoyaltyConfig: SQLite DataSource, Clock and LoyaltyPolicy beans
domain/       immutable records (Purchase, PointLot, BalanceSummary...), Tier, LoyaltyPolicy
error/        business exceptions, each with a stable error code
resources/    schema.sql (run on start-up by Spring), application.properties
```

Both entry points call the same `LoyaltyService`, so the CLI and the API can't drift apart in behaviour.
The rules that the business is most likely to tune (points per dollar, validity period, tier window,
tier thresholds) live in `LoyaltyPolicy` and `Tier`.

### Data model

| Table              | Purpose |
|--------------------|---------|
| `customers`        | One row per customer, created on their first purchase. Holds `points_owed` (see refunds). |
| `purchases`        | Every purchase, with amount in **cents** and `refunded_at`. Its id is the caller's order id. |
| `point_lots`       | **One lot per earning purchase**: `points_earned`, `points_remaining`, `expires_at`. |
| `rewards`          | The catalog (`id`, `name`, `cost_points`). |
| `redemptions`      | One row per reward redeemed. |
| `lot_consumptions` | Audit trail: every deduction from a lot, with reason (`REDEMPTION`, `REFUND_CLAWBACK`, `DEBT_REPAYMENT`) and a reference id. |

The core idea is the **lot**. Because each purchase's points expire on their own date, a single
`balance` column on the customer can't represent them. A balance of 300 might be 100 points
expiring next week and 200 expiring next year. Keeping points in per-purchase lots means:

- **Balance** = `SUM(points_remaining) WHERE expires_at > now`.
- **Expiry** needs no background job. Expired lots simply stop matching the query, so expired
  points can't be spent no matter when someone checks.
- **Soonest-expiring-first redemption** = walk the active lots `ORDER BY expires_at` and
  deduct until the cost is covered.
- **Refunds** know exactly which points came from the refunded purchase (`point_lots.purchase_id`).

`points_remaining` is denormalised for fast reads. `lot_consumptions` records how each lot got there,
so any balance can be explained and audited. Database `CHECK` constraints stop a lot from
going negative or exceeding what it earned, as a backstop to the application logic.

Other modelling choices:
- **Money is integer cents** and timestamps are **UTC epoch millis**. That avoids floating-point
  rounding errors and makes timestamp comparisons in SQL correct (ISO strings with variable
  fractional seconds don't sort correctly as text).
- **Purchase id is the primary key**, so the same order can't earn points twice, even under retries.
- **Tier is derived, not stored**: `SUM(amount_cents)` over non-refunded purchases in the last 12 months.
  It's always correct, with no job needed to demote customers as old purchases roll out of the window.

### Trade-offs

**1. Refunding points that were already spent.** This is the most interesting decision. If Alice earns
100 points from order A, redeems them, then refunds order A, what happens? Options:

| Option | Downside |
|--------|----------|
| Only claw back what's still in A's lot | Earn → redeem → refund becomes a free reward. Easy to abuse. |
| Block the refund | Points shouldn't block a legitimate refund of money. |
| Let the balance go negative | Simple, but "-60 points" is confusing, and it's unclear how it interacts with expiry. |
| **Recover from other lots, then record the rest as owed**  | Slightly more logic. |

I chose the last option. On refund: (1) take whatever is left in the purchase's own lot; (2) ignore any of
its points that already *expired* unused, since the customer never benefited from them; (3) take the
already-spent remainder from the customer's other active lots, soonest-expiring first; (4) record
anything still outstanding as `points_owed`, which is paid off from the next earnings before they
become spendable. Available balance therefore never goes negative, and the debt is visible in the balance response.

**2. Expiry computed at read time instead of by a scheduled job.** This is always correct and has no moving
parts, but expired points stay physically in `points_remaining`. Reporting "how many points expired
last month" means querying expired lots rather than reading a ledger entry. At scale I'd add a
periodic sweep that writes explicit `EXPIRY` consumptions for reporting, without making correctness depend on it.

**3. SQLite with `BEGIN IMMEDIATE` transactions.** `LoyaltyConfig` configures every SQLite connection so
that the transaction `@Transactional` opens is `BEGIN IMMEDIATE`. Each operation takes the write lock up front, so
concurrent redemptions are serialised and can't double-spend (see `ConcurrencyTest`). That's simple
and correct, but it means one writer at a time for the whole database, and reads take the lock
too. That's fine for this exercise. In production on Postgres I'd lock per customer instead
(`SELECT ... FOR UPDATE` on the customer row) so different customers don't contend.

**4. `JdbcClient` (plain SQL) instead of JPA/Hibernate or Spring Data.** There are about a dozen queries, and the important ones
(balance, ordered lot consumption, the guarded `points_remaining >= :points` update) are clearer as visible SQL.
The guarded update checks and subtracts in one atomic statement; the typical JPA equivalent is a
read-modify-write in Java. JPA would also add dirty-checking and lazy-loading behaviour to reason
about, and SQLite is not well supported by either Hibernate or Spring Data JDBC. With a larger,
CRUD-heavy domain on Postgres, I'd move the simple parts to Spring Data JDBC.

**5. Spring Boot for the CLI too.** The CLI starts the Spring context without a web server
(`WebApplicationType.NONE`) and reuses the same `LoyaltyService` bean. This costs about a second of
start-up per command. In exchange there is one wiring path, so the CLI and API can't behave differently.

### Assumptions

- Points are 1 per **whole** dollar, rounded down per purchase ($42.99 → 42 points). Purchases under $1
  are recorded (they count toward tier) but earn nothing.
- "12 months" is calendar-aware in UTC: points earned 15 Jan 2026 10:00Z expire at 15 Jan 2027 10:00Z
  and are unusable from that instant.
- Points are earned at the time the purchase is recorded (no back-dated purchases).
- Refunds are **full** refunds only.
- Customers are created implicitly by their first purchase. Balance for an unknown customer is a `404`.
- Tier thresholds over rolling 12-month non-refunded spend: **Silver** below $500, **Gold** $500+,
  **Platinum** $2,000+. Tier is informational; it doesn't change the earn rate.
- The reward catalog is seed data. Managing it is out of scope.
- The CLI's `earn` takes `--amount` because a purchase needs a value. The spec example omits it, which
  would need a purchase-lookup service that doesn't exist here.

### AI tools

I used **Claude Code** (Anthropic's command-line coding agent) throughout:
- **Implementation:** To make first working version: the schema, service, CLI, tests. 
- **Iteration under my direction:** I then had it migrate to Spring Boot 3.5 (Spring Framework 6.2),
  move the repositories from `JdbcTemplate` to `JdbcClient`, and put the API under `/points`. 
- **Verification:** Created tests using AI. 
