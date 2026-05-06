/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.neoforge.common.NeoForgeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class RegionOwnershipViolationHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final AtomicLong VIOLATION_COUNT = new AtomicLong();

    private RegionOwnershipViolationHandler() {}

    static void handle(String message) {
        VIOLATION_COUNT.incrementAndGet();
        switch (NeoForgeConfig.SERVER.regionThreadingOwnershipViolationMode.get()) {
            case DISABLED -> {}
            case WARN -> LOGGER.warn(message);
            case THROW -> throw new IllegalStateException(message);
        }
    }

    static long violationCount() {
        return VIOLATION_COUNT.get();
    }

    static void reset() {
        VIOLATION_COUNT.set(0L);
    }
}
