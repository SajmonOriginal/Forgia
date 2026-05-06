/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

import net.minecraft.world.entity.Entity;

/**
 * Schedules work on the region that owns an entity.
 */
public interface EntityRegionScheduler {
    /**
     * Runs {@code task} on the next tick where {@code entity} is alive and owned by a region.
     */
    void execute(Entity entity, Runnable task);

    /**
     * Runs {@code task} for {@code entity} after {@code delayTicks} entity-owned ticks.
     */
    ScheduledRegionTask runDelayed(Entity entity, Runnable task, long delayTicks);

    /**
     * Runs {@code task} repeatedly while {@code entity} remains alive and schedulable.
     */
    ScheduledRegionTask runAtFixedRate(Entity entity, Runnable task, long initialDelayTicks, long periodTicks);
}
