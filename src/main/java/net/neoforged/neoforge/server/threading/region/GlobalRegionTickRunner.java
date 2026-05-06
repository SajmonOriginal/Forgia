/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Executes due work for the server-global region state.
 */
final class GlobalRegionTickRunner {
    boolean runIfDue(GlobalRegionState region, long nowNanos, Runnable drainTask) {
        synchronized (region) {
            if (!region.tickTimer().isDue(nowNanos)) {
                return false;
            }

            region.tickTimer().markTickStarted(nowNanos);
        }
        final long start = System.nanoTime();
        drainTask.run();
        final long duration = System.nanoTime() - start;
        region.tickMetrics().recordTick(duration);
        RegionTickWatchdog.warnIfLongTick("global", duration);
        return true;
    }
}
