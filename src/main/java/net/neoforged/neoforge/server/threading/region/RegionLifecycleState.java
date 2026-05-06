/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

/**
 * Minimal region lifecycle states aligned with Folia's ready/ticking/dead model.
 */
enum RegionLifecycleState {
    READY,
    TICKING,
    DEAD
}
