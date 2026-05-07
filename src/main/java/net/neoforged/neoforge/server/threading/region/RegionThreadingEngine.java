/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.threading.EntityRegionScheduler;
import net.neoforged.neoforge.server.threading.GlobalRegionScheduler;
import net.neoforged.neoforge.server.threading.RegionScheduler;
import net.neoforged.neoforge.server.threading.RegionThreading;
import net.neoforged.neoforge.server.threading.RegionThreadingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Native NeoForge region threading engine shell.
 * <p>
 * This class is intentionally small until the Folia regionizer and tick loop are ported.
 */
public final class RegionThreadingEngine implements RegionThreading {
    private static final BasicRegionThreadingContext NONE_CONTEXT = new BasicRegionThreadingContext(RegionThreadingContext.Kind.NONE, null, null, null);
    private static final ThreadLocal<RegionThreadingContext> CURRENT_CONTEXT = ThreadLocal.withInitial(() -> NONE_CONTEXT);

    private final GlobalRegionState globalRegion = new GlobalRegionState();
    private final ThreadedRegionizer regionizer = new ThreadedRegionizer();
    private final Set<EntityRegionScheduledTask> entityTasks = ConcurrentHashMap.newKeySet();
    private final RegionWorkerPool workerPool;
    private final GlobalRegionTickRunner globalTickRunner = new GlobalRegionTickRunner();
    private final RegionTickRunner tickRunner = new RegionTickRunner();
    private final GlobalRegionScheduler globalScheduler = new EngineGlobalRegionScheduler(this);
    private final RegionScheduler regionScheduler = new EngineRegionScheduler(this);
    private final EntityRegionScheduler entityScheduler = new EngineEntityRegionScheduler(this);
    private final boolean workerExecutionEnabled;
    private final boolean globalWorkerExecutionEnabled;
    private volatile boolean shutdown;

    public RegionThreadingEngine(int workerThreads, boolean workerExecutionEnabled, boolean globalWorkerExecutionEnabled) {
        this.workerPool = new RegionWorkerPool(workerThreads > 0 ? workerThreads : Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
        this.workerExecutionEnabled = workerExecutionEnabled;
        this.globalWorkerExecutionEnabled = globalWorkerExecutionEnabled;
        RegionOwnershipViolationHandler.reset();
    }

    @Override
    public GlobalRegionScheduler globalScheduler() {
        return this.globalScheduler;
    }

    @Override
    public RegionScheduler regionScheduler() {
        return this.regionScheduler;
    }

    @Override
    public EntityRegionScheduler entityScheduler() {
        return this.entityScheduler;
    }

    @Override
    public boolean isOwnedByCurrentRegion(ServerLevel level, BlockPos pos) {
        final RegionThreadingContext context = this.currentContext();
        if (!(context instanceof BasicRegionThreadingContext basicContext)
                || basicContext.kind() != RegionThreadingContext.Kind.REGION
                || basicContext.regionCoordinate == null) {
            return false;
        }

        final RegionCoordinate coordinate = RegionCoordinate.fromBlock(level, pos);
        return basicContext.regionCoordinate.equals(coordinate) && basicContext.regionState != null && basicContext.regionState.isOwnedByCurrentThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        return this.isOwnedByCurrentRegion(level, entity.blockPosition());
    }

    @Override
    public void assertOwnedByCurrentRegion(ServerLevel level, BlockPos pos) {
        if (!this.isOwnedByCurrentRegion(level, pos)) {
            RegionOwnershipViolationHandler.handle("Current thread does not own region at " + pos);
        }
    }

    @Override
    public void assertOwnedByCurrentRegion(Entity entity) {
        if (!this.isOwnedByCurrentRegion(entity)) {
            RegionOwnershipViolationHandler.handle("Current thread does not own entity " + entity);
        }
    }

    @Override
    public RegionThreadingContext currentContext() {
        return CURRENT_CONTEXT.get();
    }

    void enqueueGlobal(Runnable task) {
        this.ensureRunning();
        this.globalRegion.taskQueue().enqueue(task);
    }

    RegionScheduledTask scheduleGlobal(Runnable task, long delayTicks) {
        this.ensureRunning();
        return this.globalRegion.taskQueue().schedule(task, delayTicks);
    }

    RegionScheduledTask scheduleGlobalAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
        this.ensureRunning();
        return this.globalRegion.taskQueue().scheduleAtFixedRate(task, initialDelayTicks, periodTicks);
    }

    void enqueueRegion(ServerLevel level, BlockPos pos, Runnable task) {
        this.ensureRunning();
        this.regionQueue(level, pos).enqueue(task);
    }

    RegionScheduledTask scheduleRegion(ServerLevel level, BlockPos pos, Runnable task, long delayTicks) {
        this.ensureRunning();
        return this.regionQueue(level, pos).schedule(task, delayTicks);
    }

    RegionScheduledTask scheduleRegionAtFixedRate(ServerLevel level, BlockPos pos, Runnable task, long initialDelayTicks, long periodTicks) {
        this.ensureRunning();
        return this.regionQueue(level, pos).scheduleAtFixedRate(task, initialDelayTicks, periodTicks);
    }

    EntityRegionScheduledTask scheduleEntity(Entity entity, Runnable task, long delayTicks) {
        this.ensureRunning();
        final EntityRegionScheduledTask scheduledTask = new EntityRegionScheduledTask(this, entity, task, delayTicks, 0L);
        this.entityTasks.add(scheduledTask);
        return scheduledTask;
    }

    EntityRegionScheduledTask scheduleEntityAtFixedRate(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        this.ensureRunning();
        final EntityRegionScheduledTask scheduledTask = new EntityRegionScheduledTask(this, entity, task, initialDelayTicks, periodTicks);
        this.entityTasks.add(scheduledTask);
        return scheduledTask;
    }

    /**
     * Drains tasks queued to the global region.
     */
    @ApiStatus.Internal
    public void drainGlobalTasks() {
        if (this.globalRegion.isRunning()) {
            return;
        }
        final long now = System.nanoTime();
        if (this.globalWorkerExecutionEnabled) {
            this.workerPool.execute(() -> this.globalTickRunner.runIfDue(this.globalRegion, now, this::drainGlobalRegionState));
            return;
        }
        this.globalTickRunner.runIfDue(this.globalRegion, now, this::drainGlobalRegionState);
    }

    private void drainGlobalRegionState() {
        if (!this.globalRegion.tryMarkRunning()) {
            return;
        }
        try {
            this.runInGlobalRegionOwned(this.globalRegion.taskQueue()::drain);
        } finally {
            this.globalRegion.clearRunning();
        }
    }

    /**
     * Runs {@code task} while the current thread owns the global region context.
     */
    @ApiStatus.Internal
    public void runInGlobalRegion(Runnable task) {
        if (!this.globalRegion.tryMarkRunning()) {
            RegionOwnershipViolationHandler.handle("Global region is already running on another thread");
            return;
        }
        try {
            this.runInGlobalRegionOwned(task);
        } finally {
            this.globalRegion.clearRunning();
        }
    }

    private void runInGlobalRegionOwned(Runnable task) {
        this.globalRegion.beginOwnership();
        try {
            this.runWithContext(new BasicRegionThreadingContext(RegionThreadingContext.Kind.GLOBAL_REGION, null, null, null), task);
        } finally {
            this.globalRegion.endOwnership();
        }
    }

    /**
     * Runs {@code task} while the current thread is executing a server level tick bridge.
     */
    @ApiStatus.Internal
    public void runInLevelTick(ServerLevel level, Runnable task) {
        this.runWithContext(new BasicRegionThreadingContext(RegionThreadingContext.Kind.LEVEL, level, null, null), task);
    }

    /**
     * Runs {@code task} while the current thread owns the entity's current region context.
     */
    @ApiStatus.Internal
    public void runInEntityRegion(Entity entity, Runnable task) {
        if (!(entity.level() instanceof ServerLevel level)) {
            task.run();
            return;
        }

        final RegionCoordinate coordinate = RegionCoordinate.fromBlock(level, entity.blockPosition());
        final RegionState region = this.regionizer.moveEntity(entity, coordinate);
        if (!region.tryMarkRunning()) {
            RegionOwnershipViolationHandler.handle("Entity region is already running for " + entity);
            return;
        }

        try {
            region.beginOwnership();
            this.runWithContext(new BasicRegionThreadingContext(RegionThreadingContext.Kind.REGION, level, coordinate, region), task);
        } finally {
            region.endOwnership();
            region.clearRunning();
            this.regionizer.removeIfEmpty(region);
        }
    }

    /**
     * Runs {@code task} while the current thread owns the region containing {@code pos}.
     */
    @ApiStatus.Internal
    public void runInBlockRegion(ServerLevel level, BlockPos pos, Runnable task) {
        if (this.isOwnedByCurrentRegion(level, pos)) {
            task.run();
            return;
        }

        final RegionCoordinate coordinate = RegionCoordinate.fromBlock(level, pos);
        final RegionState region = this.regionizer.getOrCreate(coordinate);
        if (!region.tryMarkRunning()) {
            RegionOwnershipViolationHandler.handle("Block region is already running at " + pos);
            return;
        }

        try {
            region.beginOwnership();
            this.runWithContext(new BasicRegionThreadingContext(RegionThreadingContext.Kind.REGION, level, coordinate, region), task);
        } finally {
            region.endOwnership();
            region.clearRunning();
            this.regionizer.removeIfEmpty(region);
        }
    }

    /**
     * Drains tasks queued to the region that owns {@code pos}.
     */
    @ApiStatus.Internal
    public void drainRegionTasks(ServerLevel level, BlockPos pos) {
        final RegionCoordinate coordinate = RegionCoordinate.fromBlock(level, pos);
        final RegionState region = this.regionizer.get(coordinate);
        if (region != null) {
            this.drainRegionState(region);
            this.regionizer.removeIfEmpty(region);
        }
    }

    /**
     * Drains all tasks queued to regions in {@code level}.
     */
    @ApiStatus.Internal
    public void drainLevelRegionTasks(ServerLevel level) {
        final long now = System.nanoTime();
        this.regionizer.forEach(region -> {
            if (region.coordinate().level() == level) {
                if (this.workerExecutionEnabled && region.isRunning()) {
                    return;
                }
                if (this.workerExecutionEnabled) {
                    this.submitRegionTick(region, now);
                } else {
                    this.tickRunner.runIfDue(region, now, () -> this.drainRegionState(region));
                }
                this.regionizer.removeIfEmpty(region);
            }
        });
    }

    /**
     * Drops queued region work for {@code level} during level unload.
     */
    @ApiStatus.Internal
    public void clearLevelRegionTasks(ServerLevel level) {
        this.regionizer.clearLevel(level);
        this.entityTasks.removeIf(task -> {
            if (task.isOwnedBy(level)) {
                task.cancel();
                return true;
            }
            return false;
        });
    }

    /**
     * Tracks a loaded chunk section in the native regionizer facade.
     */
    @ApiStatus.Internal
    public void addRegionSection(ServerLevel level, int chunkX, int chunkZ) {
        this.ensureRunning();
        this.regionizer.addChunk(level, chunkX, chunkZ);
    }

    /**
     * Removes a loaded chunk section from the native regionizer facade.
     */
    @ApiStatus.Internal
    public void removeRegionSection(ServerLevel level, int chunkX, int chunkZ) {
        this.ensureRunning();
        this.regionizer.removeChunk(level, chunkX, chunkZ);
    }

    /**
     * Tracks an entity in the native regionizer facade.
     */
    @ApiStatus.Internal
    public void addRegionEntity(Entity entity) {
        this.ensureRunning();
        if (entity.level() instanceof ServerLevel level) {
            this.regionizer.addEntity(entity, RegionCoordinate.fromBlock(level, entity.blockPosition()));
        }
    }

    /**
     * Removes an entity from the native regionizer facade.
     */
    @ApiStatus.Internal
    public void removeRegionEntity(Entity entity) {
        this.ensureRunning();
        this.regionizer.removeEntity(entity);
    }

    /**
     * Updates an entity's current region in the native regionizer facade.
     */
    @ApiStatus.Internal
    public void moveRegionEntity(Entity entity) {
        this.ensureRunning();
        if (entity.level() instanceof ServerLevel level) {
            this.regionizer.moveEntity(entity, RegionCoordinate.fromBlock(level, entity.blockPosition()));
        }
    }

    void unregisterEntityTask(EntityRegionScheduledTask task) {
        this.entityTasks.remove(task);
    }

    /**
     * {@return a snapshot of internal runtime diagnostics}
     */
    @ApiStatus.Internal
    public RegionThreadingDiagnostics diagnostics() {
        return new RegionThreadingDiagnostics(
                this.regionizer.size(),
                this.regionizer.runningRegionCount(),
                this.regionizer.sectionCount(),
                this.regionizer.loadedChunkCount(),
                this.regionizer.structuralChangeCount(),
                this.regionizer.entityCount(),
                this.entityTasks.size(),
                this.workerPool.threadCount(),
                this.workerExecutionEnabled,
                this.globalWorkerExecutionEnabled,
                this.globalRegion.isRunning(),
                RegionOwnershipViolationHandler.violationCount(),
                this.globalRegion.tickMetrics().tickCount(),
                this.globalRegion.tickMetrics().lastTickDurationNanos(),
                this.regionizer.totalRegionTickCount(),
                this.regionizer.maxRegionLastTickDurationNanos());
    }

    /**
     * Shuts down region threading runtime resources.
     */
    @ApiStatus.Internal
    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        this.globalRegion.clearTasks();
        this.entityTasks.forEach(EntityRegionScheduledTask::cancel);
        this.entityTasks.clear();
        this.regionizer.clear();
        this.workerPool.close();
    }

    private RegionTaskQueue regionQueue(ServerLevel level, BlockPos pos) {
        return this.region(level, pos).taskQueue();
    }

    private void ensureRunning() {
        if (this.shutdown) {
            throw new IllegalStateException("Region threading engine is shut down");
        }
    }

    private RegionState region(ServerLevel level, BlockPos pos) {
        final RegionCoordinate coordinate = RegionCoordinate.fromBlock(level, pos);
        return this.regionizer.getOrCreate(coordinate);
    }

    private void drainRegionState(RegionState region) {
        if (!region.tryMarkRunning()) {
            return;
        }
        final RegionCoordinate coordinate = region.coordinate();
        try {
            region.beginOwnership();
            this.runWithContext(new BasicRegionThreadingContext(RegionThreadingContext.Kind.REGION, coordinate.level(), coordinate, region), region.taskQueue()::drain);
        } finally {
            region.endOwnership();
            region.clearRunning();
        }
    }

    private void submitRegionTick(RegionState region, long nowNanos) {
        this.workerPool.execute(() -> {
            this.tickRunner.runIfDue(region, nowNanos, () -> this.drainRegionState(region));
            this.regionizer.removeIfEmpty(region);
        });
    }

    private void runWithContext(BasicRegionThreadingContext context, Runnable task) {
        final RegionThreadingContext previousContext = CURRENT_CONTEXT.get();
        CURRENT_CONTEXT.set(context);
        try {
            task.run();
        } finally {
            CURRENT_CONTEXT.set(previousContext);
        }
    }

    private record BasicRegionThreadingContext(
            RegionThreadingContext.Kind kind,
            @Nullable ServerLevel nullableLevel,
            @Nullable RegionCoordinate regionCoordinate,
            @Nullable RegionState regionState) implements RegionThreadingContext {
        @Override
        public Optional<ServerLevel> level() {
            return Optional.ofNullable(this.nullableLevel);
        }
    }
}
