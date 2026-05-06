/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

import java.util.Optional;
import java.util.function.Consumer;
import net.neoforged.neoforge.server.threading.region.RegionThreadingEngine;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Access point for the active regionized threading implementation.
 */
public final class RegionThreadingManager {
    @Nullable
    private static volatile RegionThreading current;

    private RegionThreadingManager() {}

    /**
     * {@return the active region threading implementation, if installed}
     */
    public static Optional<RegionThreading> get() {
        return Optional.ofNullable(current);
    }

    /**
     * {@return true if a region threading implementation is installed}
     */
    public static boolean isInstalled() {
        return current != null;
    }

    /**
     * Runs {@code action} when region threading is installed.
     */
    public static void ifInstalled(Consumer<RegionThreading> action) {
        final RegionThreading threading = current;
        if (threading != null) {
            action.accept(threading);
        }
    }

    /**
     * {@return the active region threading implementation}
     *
     * @throws IllegalStateException if region threading is not installed
     */
    public static RegionThreading getOrThrow() {
        final RegionThreading threading = current;
        if (threading == null) {
            throw new IllegalStateException("Region threading is not installed");
        }
        return threading;
    }

    /**
     * Installs the active region threading implementation.
     */
    @ApiStatus.Internal
    public static synchronized void install(RegionThreading threading) {
        if (current != null) {
            throw new IllegalStateException("Region threading is already installed; clear it before installing another implementation");
        }
        current = threading;
    }

    /**
     * Clears the active region threading implementation during shutdown.
     */
    @ApiStatus.Internal
    public static synchronized void clear() {
        if (current instanceof RegionThreadingEngine engine) {
            engine.shutdown();
        }
        current = null;
    }
}
