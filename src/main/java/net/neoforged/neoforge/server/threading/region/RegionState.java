/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    void removeSection(RegionSectionCoordinate section) {
        this.sections.remove(section);
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
        return this.lifecycleState.compareAndSet(RegionLifecycleState.READY, RegionLifecycleState.TICKING);
    }

    void clearRunning() {
        this.lifecycleState.compareAndSet(RegionLifecycleState.TICKING, RegionLifecycleState.READY);
    }

    boolean isRunning() {
        return this.lifecycleState.get() == RegionLifecycleState.TICKING;
    }

    boolean isDead() {
        return this.lifecycleState.get() == RegionLifecycleState.DEAD;
    }

    void markDead() {
        this.lifecycleState.set(RegionLifecycleState.DEAD);
    }

    boolean isEmpty() {
        return !this.isDead() && !this.isRunning() && !this.hasSections() && this.taskQueue.isEmpty();
    }
}
