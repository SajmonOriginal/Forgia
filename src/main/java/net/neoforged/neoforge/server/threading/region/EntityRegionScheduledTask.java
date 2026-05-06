/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.threading.ScheduledRegionTask;

final class EntityRegionScheduledTask implements ScheduledRegionTask {
    private final RegionThreadingEngine engine;
    private final Entity entity;
    private final Runnable task;
    private final ScheduledRegionTask timerTask;
    private final boolean repeating;
    private volatile ScheduledRegionTask dispatchedTask;
    private volatile boolean cancelled;
    private volatile boolean completed;

    EntityRegionScheduledTask(RegionThreadingEngine engine, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        this.engine = engine;
        this.entity = entity;
        this.task = task;
        this.repeating = periodTicks > 0L;
        this.timerTask = this.repeating ? engine.scheduleGlobalAtFixedRate(this::dispatch, delayTicks, periodTicks) : engine.scheduleGlobal(this::dispatch, delayTicks);
    }

    @Override
    public boolean cancel() {
        if (this.completed) {
            return false;
        }
        final boolean wasCancelled = this.cancelled;
        this.cancelled = true;
        final ScheduledRegionTask dispatched = this.dispatchedTask;
        if (dispatched != null) {
            dispatched.cancel();
        }
        this.engine.unregisterEntityTask(this);
        return this.timerTask.cancel() || !wasCancelled;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled || this.timerTask.isCancelled();
    }

    @Override
    public boolean isDone() {
        return this.completed || this.isCancelled();
    }

    boolean isOwnedBy(ServerLevel level) {
        return this.entity.level() == level;
    }

    private void dispatch() {
        if (this.cancelled || !(this.entity.level() instanceof ServerLevel level)) {
            this.completeIfOneShot();
            return;
        }

        this.dispatchedTask = this.engine.scheduleRegion(level, this.entity.blockPosition(), () -> {
            try {
                if (!this.cancelled) {
                    this.task.run();
                }
            } finally {
                this.completeIfOneShot();
            }
        }, 0L);
    }

    private void completeIfOneShot() {
        if (!this.repeating) {
            this.completed = true;
            this.engine.unregisterEntityTask(this);
        }
    }
}
