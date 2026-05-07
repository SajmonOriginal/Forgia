/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.threading.region.RegionThreadingDiagnostics;
import net.neoforged.neoforge.server.threading.region.RegionThreadingEngine;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal bridge points for the region threading runtime.
 */
@ApiStatus.Internal
public final class RegionThreadingHooks {
    private RegionThreadingHooks() {}

    /**
     * Drains global-region work on the current server tick loop.
     */
    public static void drainGlobalTasks() {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.drainGlobalTasks();
            }
        });
    }

    /**
     * Runs {@code task} in global-region context when the native engine is installed.
     */
    public static void runGlobalTask(Runnable task) {
        final Optional<RegionThreading> threading = RegionThreadingManager.get();
        if (threading.isPresent() && threading.get() instanceof RegionThreadingEngine engine) {
            engine.runInGlobalRegion(task);
        } else {
            task.run();
        }
    }

    /**
     * Drains region work for {@code level} on the current level tick loop.
     */
    public static void drainLevelRegionTasks(ServerLevel level) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.drainLevelRegionTasks(level);
            }
        });
    }

    /**
     * Runs {@code task} in the current level tick bridge context.
     */
    public static void runLevelTickTask(ServerLevel level, Runnable task) {
        final Optional<RegionThreading> threading = RegionThreadingManager.get();
        if (threading.isPresent() && threading.get() instanceof RegionThreadingEngine engine) {
            engine.runInLevelTick(level, task);
        } else {
            task.run();
        }
    }

    /**
     * Drops queued region work for {@code level} during level unload.
     */
    public static void clearLevelRegionTasks(ServerLevel level) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.clearLevelRegionTasks(level);
            }
        });
    }

    /**
     * Tracks a loaded chunk section in the native regionizer facade.
     */
    public static void addRegionSection(ServerLevel level, int chunkX, int chunkZ) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.addRegionSection(level, chunkX, chunkZ);
            }
        });
    }

    /**
     * Removes a loaded chunk section from the native regionizer facade.
     */
    public static void removeRegionSection(ServerLevel level, int chunkX, int chunkZ) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.removeRegionSection(level, chunkX, chunkZ);
            }
        });
    }

    /**
     * Tracks an entity in the native regionizer facade.
     */
    public static void addRegionEntity(Entity entity) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.addRegionEntity(entity);
            }
        });
    }

    /**
     * Removes an entity from the native regionizer facade.
     */
    public static void removeRegionEntity(Entity entity) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.removeRegionEntity(entity);
            }
        });
    }

    /**
     * Updates an entity's current region in the native regionizer facade.
     */
    public static void moveRegionEntity(Entity entity) {
        RegionThreadingManager.ifInstalled(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                engine.moveRegionEntity(entity);
            }
        });
    }

    /**
     * Runs {@code task} in the entity's current region context when the native engine is installed.
     */
    public static void runEntityTickTask(Entity entity, Runnable task) {
        final Optional<RegionThreading> threading = RegionThreadingManager.get();
        if (threading.isPresent() && threading.get() instanceof RegionThreadingEngine engine) {
            engine.runInEntityRegion(entity, task);
        } else {
            task.run();
        }
    }

    /**
     * Runs {@code task} in the owning block region context when the native engine is installed.
     */
    public static void runBlockTickTask(ServerLevel level, BlockPos pos, Runnable task) {
        final Optional<RegionThreading> threading = RegionThreadingManager.get();
        if (threading.isPresent() && threading.get() instanceof RegionThreadingEngine engine) {
            engine.runInBlockRegion(level, pos, task);
        } else {
            task.run();
        }
    }

    /**
     * {@return a snapshot of internal diagnostics, if the native engine is installed}
     */
    public static Optional<RegionThreadingDiagnostics> diagnostics() {
        return RegionThreadingManager.get().flatMap(threading -> {
            if (threading instanceof RegionThreadingEngine engine) {
                return Optional.of(engine.diagnostics());
            }
            return Optional.empty();
        });
    }
}
