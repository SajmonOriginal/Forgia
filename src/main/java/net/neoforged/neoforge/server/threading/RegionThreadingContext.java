/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * Describes the server execution context for code running under region threading.
 */
public interface RegionThreadingContext {
    /**
     * {@return the kind of work currently being executed}
     */
    Kind kind();

    /**
     * {@return the level owned by this context, if it is level-specific}
     */
    Optional<ServerLevel> level();

    /**
     * {@return true when code is running on a region or global-region tick thread}
     */
    default boolean isTickThread() {
        return this.kind() == Kind.GLOBAL_REGION || this.kind() == Kind.REGION;
    }

    enum Kind {
        NONE,
        GLOBAL_REGION,
        REGION,
        ASYNC
    }
}
