/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable runtime state for one region coordinate.
 */
final class RegionState {
    private final RegionCoordinate coordinate;
    private final Map<RegionSectionCoordinate, RegionSectionState> sections = new ConcurrentHashMap<>();
    private final RegionTaskQueue taskQueue = new RegionTaskQueue();
    private final RegionTickTimer tickTimer = new RegionTickTimer(System.nanoTime());
    private final RegionTickMetrics tickMetrics = new RegionTickMetrics();
    private final AtomicReference<RegionLifecycleState> lifecycleState = new AtomicReference<>(RegionLifecycleState.READY);
    private final AtomicBoolean pendingRecalculation = new AtomicBoolean();
    private final AtomicBoolean workerSubmitted = new AtomicBoolean();
    private volatile Thread ownerThread;

    RegionState(RegionCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    RegionCoordinate coordinate() {
        return this.coordinate;
    }

    void addSection(RegionSectionState section) {
        this.sections.put(section.coordinate(), section);
    }

    Collection<RegionSectionState> sections() {
        return this.sections.values();
    }

    void removeSection(RegionSectionCoordinate section) {
        this.sections.remove(section);
    }

    void clearSections() {
        this.sections.clear();
    }

    boolean hasSections() {
        return !this.sections.isEmpty();
    }

    int chunkCount() {
        int count = 0;
        for (final RegionSectionState section : this.sections.values()) {
            count += section.chunkCount();
        }
        return count;
    }

    RegionTaskQueue taskQueue() {
        return this.taskQueue;
    }

    boolean hasQueuedTasks() {
        return !this.taskQueue.isEmpty();
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

    long cappedDrainCount() {
        return this.taskQueue.cappedDrainCount();
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
        return this.lifecycleState.compareAndSet(RegionLifecycleState.READY, RegionLifecycleState.TICKING);
    }

    void clearRunning() {
        this.lifecycleState.compareAndSet(RegionLifecycleState.TICKING, RegionLifecycleState.READY);
    }

    boolean isRunning() {
        return this.lifecycleState.get() == RegionLifecycleState.TICKING;
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

    void markPendingRecalculation() {
        this.pendingRecalculation.set(true);
    }

    boolean consumePendingRecalculation() {
        return this.pendingRecalculation.compareAndSet(true, false);
    }

    boolean isDead() {
        return this.lifecycleState.get() == RegionLifecycleState.DEAD;
    }

    void markDead() {
        this.lifecycleState.set(RegionLifecycleState.DEAD);
    }

    boolean isEmpty() {
        return !this.isDead() && !this.isRunning() && !this.isWorkerSubmitted() && !this.hasSections() && this.taskQueue.isEmpty();
    }
}
