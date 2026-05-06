/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

/**
 * Handle for work scheduled through the region threading API.
 */
public interface ScheduledRegionTask {
    /**
     * Attempts to cancel the task.
     *
     * @return true if this call cancelled the task
     */
    boolean cancel();

    /**
     * {@return true if this task has been cancelled}
     */
    boolean isCancelled();

    /**
     * {@return true if this task has completed or has been cancelled}
     */
    boolean isDone();
}
