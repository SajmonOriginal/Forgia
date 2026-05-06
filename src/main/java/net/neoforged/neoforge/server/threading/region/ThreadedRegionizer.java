/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Minimal native regionizer facade while Folia's split/merge algorithm is ported.
 */
final class ThreadedRegionizer {
    private final Map<RegionCoordinate, RegionState> regions = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionSectionState> sections = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionState> sectionRegions = new ConcurrentHashMap<>();
    private final Map<Entity, RegionState> entities = new ConcurrentHashMap<>();
    private final Map<Entity, ServerLevel> entityLevels = new ConcurrentHashMap<>();
    private final AtomicLong structuralChangeCount = new AtomicLong();
    private final StampedLock regionLock = new StampedLock();
    private Thread writeLockOwner;

    RegionState getOrCreate(RegionCoordinate coordinate) {
        return this.regions.computeIfAbsent(coordinate, RegionState::new);
    }

    RegionState get(RegionCoordinate coordinate) {
        return this.regions.get(coordinate);
    }

    RegionState addChunk(ServerLevel level, int chunkX, int chunkZ) {
        final long stamp = this.acquireWriteLock();
        try {
            final RegionSectionCoordinate coordinate = RegionSectionCoordinate.fromChunk(level, chunkX, chunkZ);
            final RegionState region = this.getOrCreate(coordinate.regionCoordinate());
            final RegionSectionState section = this.sections.computeIfAbsent(coordinate, RegionSectionState::new);
            this.sectionRegions.putIfAbsent(coordinate, region);
            final boolean wasEmpty = section.isEmpty();
            if (section.addChunk(chunkX, chunkZ) && wasEmpty) {
                region.addSection(section);
                this.structuralChangeCount.incrementAndGet();
            }
            return region;
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    void removeChunk(ServerLevel level, int chunkX, int chunkZ) {
        final long stamp = this.acquireWriteLock();
        try {
            final RegionSectionCoordinate coordinate = RegionSectionCoordinate.fromChunk(level, chunkX, chunkZ);
            final RegionSectionState section = this.sections.get(coordinate);
            if (section != null && section.removeChunk(chunkX, chunkZ) && section.isEmpty()) {
                final RegionState region = this.sectionRegions.remove(coordinate);
                this.sections.remove(coordinate, section);
                if (region != null) {
                    region.removeSection(coordinate);
                    this.structuralChangeCount.incrementAndGet();
                    this.removeIfEmpty(region);
                }
            }
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    RegionState addEntity(Entity entity, RegionCoordinate coordinate) {
        final long stamp = this.acquireWriteLock();
        try {
            final RegionState region = this.getOrCreate(coordinate);
            final RegionState previous = this.entities.put(entity, region);
            this.entityLevels.put(entity, coordinate.level());
            if (previous != null && previous != region) {
                this.removeIfEmpty(previous);
                this.structuralChangeCount.incrementAndGet();
            } else if (previous == null) {
                this.structuralChangeCount.incrementAndGet();
            }
            return region;
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    RegionState moveEntity(Entity entity, RegionCoordinate coordinate) {
        return this.addEntity(entity, coordinate);
    }

    void removeEntity(Entity entity) {
        final long stamp = this.acquireWriteLock();
        try {
            final RegionState region = this.entities.remove(entity);
            this.entityLevels.remove(entity);
            if (region != null) {
                this.structuralChangeCount.incrementAndGet();
                this.removeIfEmpty(region);
            }
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    void forEach(Consumer<RegionState> action) {
        this.regions.values().forEach(action);
    }

    void removeIfEmpty(RegionState region) {
        if (region.isEmpty()) {
            if (this.regions.remove(region.coordinate(), region)) {
                region.markDead();
            }
        }
    }

    void clearLevel(Object level) {
        final long stamp = this.acquireWriteLock();
        try {
            final int previousSize = this.regions.size() + this.sections.size() + this.entities.size();
            this.regions.values().forEach(region -> {
                if (region.coordinate().level() == level) {
                    region.clearTasks();
                    region.markDead();
                }
            });
            this.sections.keySet().removeIf(section -> section.level() == level);
            this.sectionRegions.keySet().removeIf(section -> section.level() == level);
            this.entityLevels.entrySet().removeIf(entry -> {
                if (entry.getValue() == level) {
                    this.entities.remove(entry.getKey());
                    return true;
                }
                return false;
            });
            this.regions.keySet().removeIf(coordinate -> coordinate.level() == level);
            final int currentSize = this.regions.size() + this.sections.size() + this.entities.size();
            if (currentSize != previousSize) {
                this.structuralChangeCount.incrementAndGet();
            }
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    void clear() {
        final long stamp = this.acquireWriteLock();
        try {
            final boolean changed = !this.regions.isEmpty() || !this.sections.isEmpty() || !this.entities.isEmpty();
            this.regions.values().forEach(region -> {
                region.clearTasks();
                region.markDead();
            });
            this.entityLevels.clear();
            this.entities.clear();
            this.sectionRegions.clear();
            this.sections.clear();
            this.regions.clear();
            if (changed) {
                this.structuralChangeCount.incrementAndGet();
            }
        } finally {
            this.releaseWriteLock(stamp);
        }
    }

    private long acquireWriteLock() {
        final Thread currentThread = Thread.currentThread();
        if (this.writeLockOwner == currentThread) {
            throw new IllegalStateException("Cannot recursively operate in the regionizer");
        }
        final long stamp = this.regionLock.writeLock();
        this.writeLockOwner = currentThread;
        return stamp;
    }

    private void releaseWriteLock(long stamp) {
        this.writeLockOwner = null;
        this.regionLock.unlockWrite(stamp);
    }

    int size() {
        return this.regions.size();
    }

    long structuralChangeCount() {
        return this.structuralChangeCount.get();
    }

    void requestSplitCheck(RegionState region) {
        this.structuralChangeCount.incrementAndGet();
    }

    void requestMergeCheck(RegionState region) {
        this.structuralChangeCount.incrementAndGet();
    }

    int sectionCount() {
        return this.sections.size();
    }

    int loadedChunkCount() {
        int count = 0;
        for (final RegionSectionState section : this.sections.values()) {
            count += section.chunkCount();
        }
        return count;
    }

    int entityCount() {
        return this.entities.size();
    }

    int runningRegionCount() {
        int count = 0;
        for (final RegionState region : this.regions.values()) {
            if (region.isRunning()) {
                ++count;
            }
        }
        return count;
    }

    long totalRegionTickCount() {
        long count = 0L;
        for (final RegionState region : this.regions.values()) {
            count += region.tickMetrics().tickCount();
        }
        return count;
    }

    long maxRegionLastTickDurationNanos() {
        long max = 0L;
        for (final RegionState region : this.regions.values()) {
            max = Math.max(max, region.tickMetrics().lastTickDurationNanos());
        }
        return max;
    }

    Collection<RegionState> regions() {
        return this.regions.values();
    }
}
