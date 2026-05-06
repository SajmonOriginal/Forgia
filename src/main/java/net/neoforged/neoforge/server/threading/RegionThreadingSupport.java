/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading;

/**
 * Declares how code is expected to behave under regionized server execution.
 */
public enum RegionThreadingSupport {
    UNKNOWN,
    UNSUPPORTED,
    SUPPORTED,
    REQUIRED
}
