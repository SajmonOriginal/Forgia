/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable runtime state for one region coordinate.
 */
final class RegionState {
    private final RegionCoordinate coordinate;
    private final Set<RegionSectionCoordinate> sections = ConcurrentHashMap.newKeySet();
    private final RegionTaskQueue taskQueue = new RegionTaskQueue();
    private final RegionTickTimer tickTimer = new RegionTickTimer(System.nanoTime());
    private final RegionTickMetrics tickMetrics = new RegionTickMetrics();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread ownerThread;

    RegionState(RegionCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    RegionCoordinate coordinate() {
        return this.coordinate;
    }

    void addSection(RegionSectionCoordinate section) {
        this.sections.add(section);
    }

    void removeSection(RegionSectionCoordinate section) {
        this.sections.remove(section);
    }

    boolean hasSections() {
        return !this.sections.isEmpty();
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
        return this.running.compareAndSet(false, true);
    }

    void clearRunning() {
        this.running.set(false);
    }

    boolean isRunning() {
        return this.running.get();
    }

    boolean isEmpty() {
        return !this.isRunning() && !this.hasSections() && this.taskQueue.isEmpty();
    }
}
