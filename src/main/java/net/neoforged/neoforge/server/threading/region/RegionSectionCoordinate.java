/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import net.minecraft.server.level.ServerLevel;

/**
 * Stable key for one chunk section tracked by the regionizer.
 */
record RegionSectionCoordinate(ServerLevel level, int sectionX, int sectionZ) {
    static RegionSectionCoordinate fromChunk(ServerLevel level, int chunkX, int chunkZ) {
        return new RegionSectionCoordinate(level, chunkX, chunkZ);
    }

    RegionCoordinate regionCoordinate() {
        return RegionCoordinate.fromChunk(this.level, this.sectionX, this.sectionZ);
    }
}
