/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Tracks fixed-period deadlines for region tick scheduling.
 */
final class RegionSchedule {
    private long lastPeriod;

    RegionSchedule(long firstPeriod) {
        this.lastPeriod = firstPeriod;
    }

    long lastPeriod() {
        return this.lastPeriod;
    }

    void setLastPeriod(long value) {
        this.lastPeriod = value;
    }

    long deadline(long periodLength) {
        return this.lastPeriod + periodLength;
    }

    void setNextPeriod(long nextPeriod, long periodLength) {
        this.lastPeriod = nextPeriod - periodLength;
    }

    int periodsAhead(long periodLength, long time) {
        final long difference = time - this.lastPeriod;
        final int periods = (int) (Math.abs(difference) / periodLength);
        return difference >= 0 ? periods : -periods;
    }

    void advanceBy(int periods, long periodLength) {
        this.lastPeriod += (long) periods * periodLength;
    }

    void setPeriodsAhead(int periodsToBeAhead, long periodLength, long time) {
        final int periodsAhead = this.periodsAhead(periodLength, time);
        this.lastPeriod -= (long) (periodsToBeAhead - periodsAhead) * periodLength;
    }
}
