/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Minimal native regionizer facade while Folia's split/merge algorithm is ported.
 */
final class ThreadedRegionizer {
    private final Map<RegionCoordinate, RegionState> regions = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionState> sections = new ConcurrentHashMap<>();
    private final Map<Entity, RegionState> entities = new ConcurrentHashMap<>();
    private final Map<Entity, ServerLevel> entityLevels = new ConcurrentHashMap<>();
    private final AtomicLong structuralChangeCount = new AtomicLong();

    RegionState getOrCreate(RegionCoordinate coordinate) {
        return this.regions.computeIfAbsent(coordinate, RegionState::new);
    }

    RegionState get(RegionCoordinate coordinate) {
        return this.regions.get(coordinate);
    }

    RegionState addSection(RegionSectionCoordinate section) {
        final RegionState region = this.getOrCreate(section.regionCoordinate());
        this.sections.put(section, region);
        region.addSection(section);
        this.structuralChangeCount.incrementAndGet();
        return region;
    }

    void removeSection(RegionSectionCoordinate section) {
        final RegionState region = this.sections.remove(section);
        if (region != null) {
            region.removeSection(section);
            this.structuralChangeCount.incrementAndGet();
            this.removeIfEmpty(region);
        }
    }

    RegionState addEntity(Entity entity, RegionCoordinate coordinate) {
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
    }

    RegionState moveEntity(Entity entity, RegionCoordinate coordinate) {
        return this.addEntity(entity, coordinate);
    }

    void removeEntity(Entity entity) {
        final RegionState region = this.entities.remove(entity);
        this.entityLevels.remove(entity);
        if (region != null) {
            this.structuralChangeCount.incrementAndGet();
            this.removeIfEmpty(region);
        }
    }

    void forEach(Consumer<RegionState> action) {
        this.regions.values().forEach(action);
    }

    void removeIfEmpty(RegionState region) {
        if (region.isEmpty()) {
            this.regions.remove(region.coordinate(), region);
        }
    }

    void clearLevel(Object level) {
        final int previousSize = this.regions.size() + this.sections.size() + this.entities.size();
        this.regions.values().forEach(region -> {
            if (region.coordinate().level() == level) {
                region.clearTasks();
            }
        });
        this.sections.keySet().removeIf(section -> section.level() == level);
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
    }

    void clear() {
        final boolean changed = !this.regions.isEmpty() || !this.sections.isEmpty() || !this.entities.isEmpty();
        this.regions.values().forEach(RegionState::clearTasks);
        this.entityLevels.clear();
        this.entities.clear();
        this.sections.clear();
        this.regions.clear();
        if (changed) {
            this.structuralChangeCount.incrementAndGet();
        }
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
