/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Schedules work on the region that owns a position.
 */
public interface RegionScheduler {
    /**
     * Runs {@code task} on the next tick of the region that owns {@code pos}.
     */
    void execute(ServerLevel level, BlockPos pos, Runnable task);

    /**
     * Runs {@code task} on the owning region after {@code delayTicks} ticks.
     */
    ScheduledRegionTask runDelayed(ServerLevel level, BlockPos pos, Runnable task, long delayTicks);

    /**
     * Runs {@code task} repeatedly on the owning region.
     */
    ScheduledRegionTask runAtFixedRate(ServerLevel level, BlockPos pos, Runnable task, long initialDelayTicks, long periodTicks);
}
