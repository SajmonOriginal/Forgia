/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.threading.EntityRegionScheduler;
import net.neoforged.neoforge.server.threading.ScheduledRegionTask;

final class EngineEntityRegionScheduler implements EntityRegionScheduler {
    private final RegionThreadingEngine engine;

    EngineEntityRegionScheduler(RegionThreadingEngine engine) {
        this.engine = engine;
    }

    @Override
    public void execute(Entity entity, Runnable task) {
        if (entity.level() instanceof ServerLevel level) {
            this.engine.enqueueRegion(level, entity.blockPosition(), task);
        }
    }

    @Override
    public ScheduledRegionTask runDelayed(Entity entity, Runnable task, long delayTicks) {
        if (entity.level() instanceof ServerLevel) {
            return this.engine.scheduleEntity(entity, task, delayTicks);
        }
        return RegionScheduledTask.cancelled(task);
    }

    @Override
    public ScheduledRegionTask runAtFixedRate(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        if (entity.level() instanceof ServerLevel) {
            return this.engine.scheduleEntityAtFixedRate(entity, task, initialDelayTicks, periodTicks);
        }
        return RegionScheduledTask.cancelled(task);
    }
}
