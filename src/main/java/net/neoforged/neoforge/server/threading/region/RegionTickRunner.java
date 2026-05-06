/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Executes due work for a region state.
 */
final class RegionTickRunner {
    private static final int MAX_CATCH_UP_TICKS = 3;

    boolean runIfDue(RegionState region, long nowNanos, Runnable drainTask) {
        if (!region.tickTimer().isDue(nowNanos)) {
            return false;
        }

        final int ticksBehind = Math.min(region.tickTimer().ticksBehind(nowNanos), MAX_CATCH_UP_TICKS);
        region.tickTimer().markTickStarted(nowNanos);
        if (ticksBehind > 1) {
            region.tickTimer().advanceBy(ticksBehind - 1);
        }
        final long start = System.nanoTime();
        drainTask.run();
        region.tickMetrics().recordTick(System.nanoTime() - start);
        return true;
    }
}
