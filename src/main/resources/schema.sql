-- All timestamps are stored as UTC epoch milliseconds (INTEGER) so they sort and compare correctly.
-- All money is stored as integer cents to avoid floating point rounding.

CREATE TABLE IF NOT EXISTS customers (
    id           TEXT    PRIMARY KEY,
    -- Points clawed back by a refund that the customer had already spent and that could not be
    -- recovered from other active lots. Repaid from future earnings before they become spendable.
    points_owed  INTEGER NOT NULL DEFAULT 0 CHECK (points_owed >= 0),
    created_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS purchases (
    id            TEXT    PRIMARY KEY,           -- caller-supplied order id; uniqueness = idempotency guard
    customer_id   TEXT    NOT NULL REFERENCES customers (id),
    amount_cents  INTEGER NOT NULL CHECK (amount_cents > 0),
    purchased_at  INTEGER NOT NULL,
    refunded_at   INTEGER                         -- NULL while the purchase stands
);

CREATE INDEX IF NOT EXISTS idx_purchases_customer_time ON purchases (customer_id, purchased_at);

-- One lot per earning purchase. Each lot expires independently, which is what makes
-- per-point expiry and "soonest-expiring first" redemption straightforward.
CREATE TABLE IF NOT EXISTS point_lots (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id       TEXT    NOT NULL REFERENCES customers (id),
    purchase_id       TEXT    NOT NULL UNIQUE REFERENCES purchases (id),
    points_earned     INTEGER NOT NULL CHECK (points_earned > 0),
    points_remaining  INTEGER NOT NULL CHECK (points_remaining >= 0 AND points_remaining <= points_earned),
    earned_at         INTEGER NOT NULL,
    expires_at        INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_point_lots_customer_expiry ON point_lots (customer_id, expires_at);

CREATE TABLE IF NOT EXISTS rewards (
    id           TEXT    PRIMARY KEY,
    name         TEXT    NOT NULL,
    cost_points  INTEGER NOT NULL CHECK (cost_points > 0)
);

CREATE TABLE IF NOT EXISTS redemptions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id   TEXT    NOT NULL REFERENCES customers (id),
    reward_id     TEXT    NOT NULL REFERENCES rewards (id),
    points_spent  INTEGER NOT NULL CHECK (points_spent > 0),
    redeemed_at   INTEGER NOT NULL
);

-- Audit trail: every deduction from a lot, and why. points_remaining on the lot is the
-- fast-path value; this table explains how it got there.
CREATE TABLE IF NOT EXISTS lot_consumptions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    lot_id        INTEGER NOT NULL REFERENCES point_lots (id),
    reason        TEXT    NOT NULL CHECK (reason IN ('REDEMPTION', 'REFUND_CLAWBACK', 'DEBT_REPAYMENT')),
    reference_id  TEXT    NOT NULL,              -- redemption id or purchase id, depending on reason
    points        INTEGER NOT NULL CHECK (points > 0),
    consumed_at   INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lot_consumptions_lot ON lot_consumptions (lot_id);

-- Seed reward catalog.
INSERT OR IGNORE INTO rewards (id, name, cost_points) VALUES ('free-coffee', 'Free coffee', 100);
INSERT OR IGNORE INTO rewards (id, name, cost_points) VALUES ('free-pastry', 'Free pastry', 150);
INSERT OR IGNORE INTO rewards (id, name, cost_points) VALUES ('ten-dollars-off', '$10 off your next purchase', 500);
