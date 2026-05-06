/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Snapshot of the internal region threading runtime state.
 */
public record RegionThreadingDiagnostics(
        int regionCount,
        int runningRegionCount,
        int sectionCount,
        int trackedEntityCount,
        int trackedEntityTaskCount,
        int workerThreadCount,
        long globalTickCount,
        long globalLastTickDurationNanos) {}
