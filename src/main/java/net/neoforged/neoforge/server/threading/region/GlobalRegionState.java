/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable runtime state for server-global region work.
 */
final class GlobalRegionState {
    private final RegionTaskQueue taskQueue = new RegionTaskQueue();
    private final RegionTickTimer tickTimer = new RegionTickTimer(System.nanoTime());
    private final RegionTickMetrics tickMetrics = new RegionTickMetrics();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean workerSubmitted = new AtomicBoolean();
    private volatile Thread ownerThread;

    RegionTaskQueue taskQueue() {
        return this.taskQueue;
    }

    void clearTasks() {
        this.taskQueue.clearAndCancel();
    }

    RegionTickTimer tickTimer() {
        return this.tickTimer;
    }

    RegionTickMetrics tickMetrics() {
        return this.tickMetrics;
    }

    void beginOwnership() {
        this.ownerThread = Thread.currentThread();
    }

    void endOwnership() {
        this.ownerThread = null;
    }

    boolean isOwnedByCurrentThread() {
        return this.ownerThread == Thread.currentThread();
    }

    boolean tryMarkRunning() {
        return this.running.compareAndSet(false, true);
    }

    void clearRunning() {
        this.running.set(false);
    }

    boolean isRunning() {
        return this.running.get();
    }

    boolean tryMarkWorkerSubmitted() {
        return this.workerSubmitted.compareAndSet(false, true);
    }

    void clearWorkerSubmitted() {
        this.workerSubmitted.set(false);
    }

    boolean isWorkerSubmitted() {
        return this.workerSubmitted.get();
    }
}
