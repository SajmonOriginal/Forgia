/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.neoforged.neoforge.server.threading.ScheduledRegionTask;

final class RegionScheduledTask implements ScheduledRegionTask {
    private final Runnable task;
    private final long periodTicks;
    private volatile boolean cancelled;
    private volatile boolean completed;
    private long deadlineTick;

    RegionScheduledTask(Runnable task, long deadlineTick, long periodTicks) {
        this.task = task;
        this.deadlineTick = deadlineTick;
        this.periodTicks = periodTicks;
    }

    static RegionScheduledTask cancelled(Runnable task) {
        final RegionScheduledTask scheduledTask = new RegionScheduledTask(task, 0L, 0L);
        scheduledTask.cancel();
        return scheduledTask;
    }

    @Override
    public boolean cancel() {
        if (this.completed) {
            return false;
        }
        final boolean wasCancelled = this.cancelled;
        this.cancelled = true;
        return !wasCancelled;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public boolean isDone() {
        return this.cancelled || this.completed;
    }

    boolean isDue(long currentTick) {
        return !this.cancelled && !this.completed && this.deadlineTick <= currentTick;
    }

    boolean runAndReschedule(long currentTick) {
        if (this.cancelled || this.completed) {
            return false;
        }

        try {
            this.task.run();
        } catch (RuntimeException | Error exception) {
            this.completed = true;
            throw exception;
        }

        if (this.cancelled || this.periodTicks <= 0L) {
            this.completed = true;
            return false;
        }

        this.deadlineTick = currentTick + this.periodTicks;
        return true;
    }
}
