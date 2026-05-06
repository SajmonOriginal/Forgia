/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

final class RegionTaskQueue {
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final List<RegionScheduledTask> scheduledTasks = new ArrayList<>();
    private long currentTick;

    void enqueue(Runnable task) {
        this.tasks.add(task);
    }

    RegionScheduledTask schedule(Runnable task, long delayTicks) {
        if (delayTicks < 0L) {
            throw new IllegalArgumentException("delayTicks must be >= 0");
        }
        return this.schedule(task, delayTicks, 0L);
    }

    RegionScheduledTask scheduleAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks < 0L) {
            throw new IllegalArgumentException("initialDelayTicks must be >= 0");
        }
        if (periodTicks <= 0L) {
            throw new IllegalArgumentException("periodTicks must be > 0");
        }
        return this.schedule(task, initialDelayTicks, periodTicks);
    }

    void drain() {
        Runnable task;
        while ((task = this.tasks.poll()) != null) {
            task.run();
        }

        for (final RegionScheduledTask scheduledTask : this.pollDueScheduledTasks()) {
            if (scheduledTask.runAndReschedule(this.currentTick)) {
                synchronized (this.scheduledTasks) {
                    this.scheduledTasks.add(scheduledTask);
                }
            }
        }

        ++this.currentTick;
    }

    boolean isEmpty() {
        if (!this.tasks.isEmpty()) {
            return false;
        }

        synchronized (this.scheduledTasks) {
            this.scheduledTasks.removeIf(RegionScheduledTask::isCancelled);
            return this.scheduledTasks.isEmpty();
        }
    }

    void clearAndCancel() {
        this.tasks.clear();
        synchronized (this.scheduledTasks) {
            this.scheduledTasks.forEach(RegionScheduledTask::cancel);
            this.scheduledTasks.clear();
        }
    }

    private RegionScheduledTask schedule(Runnable task, long delayTicks, long periodTicks) {
        final RegionScheduledTask scheduledTask = new RegionScheduledTask(task, this.currentTick + delayTicks, periodTicks);
        synchronized (this.scheduledTasks) {
            this.scheduledTasks.add(scheduledTask);
        }
        return scheduledTask;
    }

    private List<RegionScheduledTask> pollDueScheduledTasks() {
        final List<RegionScheduledTask> dueTasks = new ArrayList<>();
        synchronized (this.scheduledTasks) {
            final Iterator<RegionScheduledTask> iterator = this.scheduledTasks.iterator();
            while (iterator.hasNext()) {
                final RegionScheduledTask scheduledTask = iterator.next();
                if (scheduledTask.isCancelled()) {
                    iterator.remove();
                } else if (scheduledTask.isDue(this.currentTick)) {
                    iterator.remove();
                    dueTasks.add(scheduledTask);
                }
            }
        }
        return dueTasks;
    }
}
