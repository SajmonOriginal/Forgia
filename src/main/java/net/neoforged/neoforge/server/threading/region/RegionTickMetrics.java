/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Minimal tick metrics for region runtime diagnostics.
 */
final class RegionTickMetrics {
    private long tickCount;
    private long lastTickDurationNanos;

    void recordTick(long durationNanos) {
        ++this.tickCount;
        this.lastTickDurationNanos = durationNanos;
    }

    long tickCount() {
        return this.tickCount;
    }

    long lastTickDurationNanos() {
        return this.lastTickDurationNanos;
    }
}
