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
        int submittedRegionCount,
        int sectionCount,
        int loadedChunkCount,
        int pendingRecalculationCount,
        long structuralChangeCount,
        long splitCheckRequestCount,
        long mergeCheckRequestCount,
        int trackedEntityCount,
        int trackedEntityTaskCount,
        int workerThreadCount,
        boolean workerExecutionEnabled,
        boolean globalWorkerExecutionEnabled,
        boolean globalRegionRunning,
        boolean globalRegionSubmitted,
        long rejectedWorkerTaskCount,
        long failedQueuedTaskCount,
        long ownershipViolationCount,
        long globalTickCount,
        long globalLastTickDurationNanos,
        long totalRegionTickCount,
        long maxRegionLastTickDurationNanos,
        long cappedDrainCount) {}
