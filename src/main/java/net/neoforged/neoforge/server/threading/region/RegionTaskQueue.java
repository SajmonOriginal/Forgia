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
import net.neoforged.neoforge.common.NeoForgeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class RegionTaskQueue {
    private static final Logger LOGGER = LogManager.getLogger();

    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final List<RegionScheduledTask> scheduledTasks = new ArrayList<>();
    private long cappedDrainCount;
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

    int drain() {
        int failedTasks = 0;
        int drainedImmediateTasks = 0;
        final int maxImmediateTasks = NeoForgeConfig.SERVER.regionThreadingMaxImmediateTasksPerDrain.get();
        Runnable task;
        while ((task = this.tasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException | Error exception) {
                ++failedTasks;
                LOGGER.error("Region task failed", exception);
            }
            if (maxImmediateTasks > 0 && ++drainedImmediateTasks >= maxImmediateTasks && !this.tasks.isEmpty()) {
                ++this.cappedDrainCount;
                LOGGER.warn("Region task drain hit immediate task limit of {}", maxImmediateTasks);
                break;
            }
        }

        for (final RegionScheduledTask scheduledTask : this.pollDueScheduledTasks(NeoForgeConfig.SERVER.regionThreadingMaxScheduledTasksPerDrain.get())) {
            try {
                if (scheduledTask.runAndReschedule(this.currentTick)) {
                    synchronized (this.scheduledTasks) {
                        this.scheduledTasks.add(scheduledTask);
                    }
                }
            } catch (RuntimeException | Error exception) {
                ++failedTasks;
                LOGGER.error("Scheduled region task failed", exception);
            }
        }

        ++this.currentTick;
        return failedTasks;
    }

    long cappedDrainCount() {
        return this.cappedDrainCount;
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

    private List<RegionScheduledTask> pollDueScheduledTasks(int maxScheduledTasks) {
        final List<RegionScheduledTask> dueTasks = new ArrayList<>();
        synchronized (this.scheduledTasks) {
            final Iterator<RegionScheduledTask> iterator = this.scheduledTasks.iterator();
            while (iterator.hasNext()) {
                final RegionScheduledTask scheduledTask = iterator.next();
                if (scheduledTask.isCancelled()) {
                    iterator.remove();
                } else if (scheduledTask.isDue(this.currentTick)) {
                    if (maxScheduledTasks > 0 && dueTasks.size() >= maxScheduledTasks) {
                        ++this.cappedDrainCount;
                        LOGGER.warn("Region task drain hit scheduled task limit of {}", maxScheduledTasks);
                        break;
                    }
                    iterator.remove();
                    dueTasks.add(scheduledTask);
                }
            }
        }
        return dueTasks;
    }
}
