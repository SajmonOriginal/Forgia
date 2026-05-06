/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.neoforged.neoforge.server.threading.GlobalRegionScheduler;
import net.neoforged.neoforge.server.threading.ScheduledRegionTask;

final class EngineGlobalRegionScheduler implements GlobalRegionScheduler {
    private final RegionThreadingEngine engine;

    EngineGlobalRegionScheduler(RegionThreadingEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(Runnable task) {
        this.engine.enqueueGlobal(task);
    }

    @Override
    public ScheduledRegionTask runDelayed(Runnable task, long delayTicks) {
        return this.engine.scheduleGlobal(task, delayTicks);
    }

    @Override
    public ScheduledRegionTask runAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        return this.engine.scheduleGlobalAtFixedRate(task, initialDelayTicks, periodTicks);
    }
}
