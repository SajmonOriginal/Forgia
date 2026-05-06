/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;

/**
 * Minimal native regionizer facade while Folia's split/merge algorithm is ported.
 */
final class ThreadedRegionizer {
    private final Map<RegionCoordinate, RegionState> regions = new ConcurrentHashMap<>();
    private final Map<RegionSectionCoordinate, RegionState> sections = new ConcurrentHashMap<>();
    private final Map<Entity, RegionState> entities = new ConcurrentHashMap<>();

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
        return region;
    }

    void removeSection(RegionSectionCoordinate section) {
        final RegionState region = this.sections.remove(section);
        if (region != null) {
            region.removeSection(section);
            this.removeIfEmpty(region);
        }
    }

    RegionState addEntity(Entity entity, RegionCoordinate coordinate) {
        final RegionState region = this.getOrCreate(coordinate);
        this.entities.put(entity, region);
        return region;
    }

    RegionState moveEntity(Entity entity, RegionCoordinate coordinate) {
        return this.addEntity(entity, coordinate);
    }

    void removeEntity(Entity entity) {
        final RegionState region = this.entities.remove(entity);
        if (region != null) {
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
        this.sections.keySet().removeIf(section -> section.level() == level);
        this.entities.keySet().removeIf(entity -> entity.level() == level);
        this.regions.keySet().removeIf(coordinate -> coordinate.level() == level);
    }

    void clear() {
        this.entities.clear();
        this.sections.clear();
        this.regions.clear();
    }

    int size() {
        return this.regions.size();
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

    Collection<RegionState> regions() {
        return this.regions.values();
    }
}
