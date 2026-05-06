/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.threading.RegionScheduler;
import net.neoforged.neoforge.server.threading.ScheduledRegionTask;

final class EngineRegionScheduler implements RegionScheduler {
    private final RegionThreadingEngine engine;

    EngineRegionScheduler(RegionThreadingEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(ServerLevel level, BlockPos pos, Runnable task) {
        this.engine.enqueueRegion(level, pos, task);
    }

    @Override
    public ScheduledRegionTask runDelayed(ServerLevel level, BlockPos pos, Runnable task, long delayTicks) {
        return this.engine.scheduleRegion(level, pos, task, delayTicks);
    }

    @Override
    public ScheduledRegionTask runAtFixedRate(ServerLevel level, BlockPos pos, Runnable task, long initialDelayTicks, long periodTicks) {
        return this.engine.scheduleRegionAtFixedRate(level, pos, task, initialDelayTicks, periodTicks);
    }
}
