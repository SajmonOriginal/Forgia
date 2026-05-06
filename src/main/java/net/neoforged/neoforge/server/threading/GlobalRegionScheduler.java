/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

/**
 * Schedules work that must run on the global region.
 */
public interface GlobalRegionScheduler {
    /**
     * Runs {@code task} on the next global-region tick.
     */
    void execute(Runnable task);

    /**
     * Runs {@code task} on the global region after {@code delayTicks} ticks.
     */
    ScheduledRegionTask runDelayed(Runnable task, long delayTicks);

    /**
     * Runs {@code task} repeatedly on the global region.
     */
    ScheduledRegionTask runAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks);
}
