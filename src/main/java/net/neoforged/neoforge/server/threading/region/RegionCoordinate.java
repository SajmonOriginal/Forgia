/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Stable key for a regionized chunk group in a single server level.
 */
public record RegionCoordinate(ServerLevel level, int regionX, int regionZ) {

    public static final int DEFAULT_REGION_CHUNK_SHIFT = 4;
    public static RegionCoordinate fromBlock(ServerLevel level, BlockPos pos) {
        return fromChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static RegionCoordinate fromChunk(ServerLevel level, int chunkX, int chunkZ) {
        return new RegionCoordinate(level, chunkX >> DEFAULT_REGION_CHUNK_SHIFT, chunkZ >> DEFAULT_REGION_CHUNK_SHIFT);
    }
}
