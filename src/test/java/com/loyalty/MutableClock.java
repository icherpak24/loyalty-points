package com.loyalty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A test clock that only moves when told to. */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void advance(Period period) {
        now = now.atZone(ZoneOffset.UTC).plus(period).toInstant();
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
