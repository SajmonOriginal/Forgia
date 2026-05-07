/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.server.threading.RegionThreadingCompatibility;
import net.neoforged.neoforge.server.threading.RegionThreadingHooks;
import net.neoforged.neoforge.server.threading.region.RegionThreadingDiagnostics;

final class RegionThreadingCommand {
    private RegionThreadingCommand() {}

    static ArgumentBuilder<CommandSourceStack, ?> register() {
        return Commands.literal("regionthreading")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> {
                    final CommandSourceStack source = context.getSource();
                    RegionThreadingHooks.diagnostics().ifPresentOrElse(
                            diagnostics -> sendDiagnostics(source, diagnostics),
                            () -> sendNotInstalled(source));
                    return 1;
                }));
    }

    private static void sendDiagnostics(CommandSourceStack source, RegionThreadingDiagnostics diagnostics) {
        source.sendSuccess(() -> Component.literal("Region threading diagnostics: enabled=" + NeoForgeConfig.SERVER.enableRegionThreadingBridge.get()
                + ", regions=" + diagnostics.regionCount()
                + ", runningRegions=" + diagnostics.runningRegionCount()
                + ", sections=" + diagnostics.sectionCount()
                + ", loadedChunks=" + diagnostics.loadedChunkCount()
                + ", structuralChanges=" + diagnostics.structuralChangeCount()
                + ", splitChecks=" + diagnostics.splitCheckRequestCount()
                + ", mergeChecks=" + diagnostics.mergeCheckRequestCount()
                + ", trackedEntities=" + diagnostics.trackedEntityCount()
                + ", entityTasks=" + diagnostics.trackedEntityTaskCount()
                + ", workerThreads=" + diagnostics.workerThreadCount()
                + ", workerExecution=" + diagnostics.workerExecutionEnabled()
                + ", globalWorkerExecution=" + diagnostics.globalWorkerExecutionEnabled()
                + ", globalRunning=" + diagnostics.globalRegionRunning()
                + ", rejectedWorkerTasks=" + diagnostics.rejectedWorkerTaskCount()
                + ", ownershipViolations=" + NeoForgeConfig.SERVER.regionThreadingOwnershipViolationMode.get()
                + ", ownershipViolationCount=" + diagnostics.ownershipViolationCount()
                + ", defaultModSupport=" + RegionThreadingCompatibility.defaultSupport()
                + ", unsupportedMods=" + RegionThreadingCompatibility.unsupportedMods().size()
                + ", requiredMods=" + RegionThreadingCompatibility.requiredMods().size()
                + ", globalTicks=" + diagnostics.globalTickCount()
                + ", globalLastTickNs=" + diagnostics.globalLastTickDurationNanos()
                + ", regionTicks=" + diagnostics.totalRegionTickCount()
                + ", maxRegionLastTickNs=" + diagnostics.maxRegionLastTickDurationNanos()), false);
    }

    private static void sendNotInstalled(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Region threading bridge is not installed: enabled="
                + NeoForgeConfig.SERVER.enableRegionThreadingBridge.get()
                + ", workerExecution=" + NeoForgeConfig.SERVER.enableRegionThreadingWorkers.get()
                + ", globalWorkerExecution=" + NeoForgeConfig.SERVER.enableGlobalRegionThreadingWorker.get()
                + ", ownershipViolations=" + NeoForgeConfig.SERVER.regionThreadingOwnershipViolationMode.get()
                + ", defaultModSupport=" + RegionThreadingCompatibility.defaultSupport()
                + ", unsupportedMods=" + RegionThreadingCompatibility.unsupportedMods().size()
                + ", requiredMods=" + RegionThreadingCompatibility.requiredMods().size()
                + ", configuredWorkerThreads=" + NeoForgeConfig.SERVER.regionThreadingWorkerThreads.get()), false);
    }
}
