/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;

/**
 * Tracks loaded chunks inside one region section.
 */
final class RegionSectionState {
    private final RegionSectionCoordinate coordinate;
    private final Set<Long> chunks = ConcurrentHashMap.newKeySet();

    RegionSectionState(RegionSectionCoordinate coordinate) {
        this.coordinate = coordinate;
    }

    RegionSectionCoordinate coordinate() {
        return this.coordinate;
    }

    boolean addChunk(int chunkX, int chunkZ) {
        return this.chunks.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    boolean removeChunk(int chunkX, int chunkZ) {
        return this.chunks.remove(ChunkPos.asLong(chunkX, chunkZ));
    }

    boolean isEmpty() {
        return this.chunks.isEmpty();
    }

    int chunkCount() {
        return this.chunks.size();
    }
}
