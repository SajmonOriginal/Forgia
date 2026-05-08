/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
    private static final int REGION_SECTION_MERGE_RADIUS = 1;

    private final Map<RegionCoordinate, RegionState> regions = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionSectionState> sections = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionState> sectionRegions = new ConcurrentHashMap<>();
    private final Map<Entity, RegionState> entities = new ConcurrentHashMap<>();
    private final Map<Entity, ServerLevel> entityLevels = new ConcurrentHashMap<>();
    private final AtomicLong structuralChangeCount = new AtomicLong();
    private final AtomicLong splitCheckRequestCount = new AtomicLong();
    private final AtomicLong mergeCheckRequestCount = new AtomicLong();
    private final StampedLock regionLock = new StampedLock();
    private Thread writeLockOwner;

    RegionState getOrCreate(RegionCoordinate coordinate) {
        final RegionState existing = this.regions.get(coordinate);
        if (existing != null) {
            return existing;
        }
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
                this.requestMergeCheckIfNeeded(coordinate, region);
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
                    this.requestSplitCheck(region);
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
        this.uniqueRegions().forEach(action);
    }

    void removeIfEmpty(RegionState region) {
        this.recalculateIfPending(region);
        if (region.isEmpty()) {
            if (this.regions.values().removeIf(value -> value == region)) {
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
        return this.uniqueRegions().size();
    }

    long structuralChangeCount() {
        return this.structuralChangeCount.get();
    }

    long splitCheckRequestCount() {
        return this.splitCheckRequestCount.get();
    }

    long mergeCheckRequestCount() {
        return this.mergeCheckRequestCount.get();
    }

    void requestSplitCheck(RegionState region) {
        this.splitCheckRequestCount.incrementAndGet();
        this.splitRegionIfNeeded(region);
    }

    void requestMergeCheck(RegionState region) {
        this.mergeCheckRequestCount.incrementAndGet();
    }

    private void requestMergeCheckIfNeeded(RegionSectionCoordinate coordinate, RegionState region) {
        for (int dz = -REGION_SECTION_MERGE_RADIUS; dz <= REGION_SECTION_MERGE_RADIUS; ++dz) {
            for (int dx = -REGION_SECTION_MERGE_RADIUS; dx <= REGION_SECTION_MERGE_RADIUS; ++dx) {
                if ((dx | dz) == 0) {
                    continue;
                }

                final RegionSectionCoordinate neighbour = new RegionSectionCoordinate(coordinate.level(), coordinate.sectionX() + dx, coordinate.sectionZ() + dz);
                final RegionState neighbourRegion = this.sectionRegions.get(neighbour);
                if (neighbourRegion != null && neighbourRegion != region) {
                    this.requestMergeCheck(region);
                    this.mergeRegions(region, neighbourRegion);
                    return;
                }
            }
        }
    }

    private void mergeRegions(RegionState target, RegionState source) {
        if (target.isRunning() || source.isRunning() || target.hasQueuedTasks() || source.hasQueuedTasks()) {
            target.markPendingRecalculation();
            source.markPendingRecalculation();
            return;
        }

        for (final RegionSectionState section : source.sections()) {
            target.addSection(section);
            this.sectionRegions.put(section.coordinate(), target);
        }
        source.clearSections();
        source.markDead();
        this.regions.replaceAll((coordinate, region) -> region == source ? target : region);
        this.entities.replaceAll((entity, region) -> region == source ? target : region);
        this.structuralChangeCount.incrementAndGet();
    }

    private void splitRegionIfNeeded(RegionState region) {
        if (region.isRunning() || region.hasQueuedTasks()) {
            region.markPendingRecalculation();
            return;
        }

        final List<List<RegionSectionState>> components = this.connectedComponents(region.sections());
        if (components.size() <= 1) {
            return;
        }

        this.regions.values().removeIf(value -> value == region);
        region.clearSections();
        for (int i = 0; i < components.size(); ++i) {
            final List<RegionSectionState> component = components.get(i);
            final RegionState splitRegion = i == 0 ? region : new RegionState(component.get(0).coordinate().regionCoordinate());
            for (final RegionSectionState section : component) {
                splitRegion.addSection(section);
                this.sectionRegions.put(section.coordinate(), splitRegion);
                this.regions.put(section.coordinate().regionCoordinate(), splitRegion);
            }
        }

        this.entities.replaceAll((entity, currentRegion) -> {
            if (currentRegion != region || !(entity.level() instanceof ServerLevel level)) {
                return currentRegion;
            }
            final RegionState splitRegion = this.get(RegionCoordinate.fromBlock(level, entity.blockPosition()));
            return splitRegion == null ? currentRegion : splitRegion;
        });
        this.structuralChangeCount.incrementAndGet();
    }

    private void recalculateIfPending(RegionState region) {
        if (!region.consumePendingRecalculation() || region.isRunning() || region.hasQueuedTasks() || region.isDead()) {
            return;
        }

        for (final RegionSectionState section : region.sections()) {
            this.requestMergeCheckIfNeeded(section.coordinate(), region);
        }
        this.splitRegionIfNeeded(region);
    }

    private List<List<RegionSectionState>> connectedComponents(Collection<RegionSectionState> regionSections) {
        final Map<RegionSectionCoordinate, RegionSectionState> remaining = new ConcurrentHashMap<>();
        for (final RegionSectionState section : regionSections) {
            remaining.put(section.coordinate(), section);
        }

        final List<List<RegionSectionState>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            final RegionSectionState first = remaining.values().iterator().next();
            remaining.remove(first.coordinate());
            final List<RegionSectionState> component = new ArrayList<>();
            final Queue<RegionSectionState> queue = new ArrayDeque<>();
            queue.add(first);

            while (!queue.isEmpty()) {
                final RegionSectionState section = queue.remove();
                component.add(section);
                for (int dz = -REGION_SECTION_MERGE_RADIUS; dz <= REGION_SECTION_MERGE_RADIUS; ++dz) {
                    for (int dx = -REGION_SECTION_MERGE_RADIUS; dx <= REGION_SECTION_MERGE_RADIUS; ++dx) {
                        if ((dx | dz) == 0) {
                            continue;
                        }
                        final RegionSectionCoordinate neighbour = new RegionSectionCoordinate(section.coordinate().level(), section.coordinate().sectionX() + dx, section.coordinate().sectionZ() + dz);
                        final RegionSectionState neighbourSection = remaining.remove(neighbour);
                        if (neighbourSection != null) {
                            queue.add(neighbourSection);
                        }
                    }
                }
            }

            components.add(component);
        }

        return components;
    }

    private Set<RegionState> uniqueRegions() {
        return new HashSet<>(this.regions.values());
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
        for (final RegionState region : this.uniqueRegions()) {
            if (region.isRunning()) {
                ++count;
            }
        }
        return count;
    }

    int submittedRegionCount() {
        int count = 0;
        for (final RegionState region : this.uniqueRegions()) {
            if (region.isWorkerSubmitted()) {
                ++count;
            }
        }
        return count;
    }

    long totalRegionTickCount() {
        long count = 0L;
        for (final RegionState region : this.uniqueRegions()) {
            count += region.tickMetrics().tickCount();
        }
        return count;
    }

    long maxRegionLastTickDurationNanos() {
        long max = 0L;
        for (final RegionState region : this.uniqueRegions()) {
            max = Math.max(max, region.tickMetrics().lastTickDurationNanos());
        }
        return max;
    }

    Collection<RegionState> regions() {
        return this.uniqueRegions();
    }
}
