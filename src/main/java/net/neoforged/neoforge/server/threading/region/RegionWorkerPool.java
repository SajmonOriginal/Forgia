/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.threading.region;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker pool reserved for native region execution.
 */
final class RegionWorkerPool implements AutoCloseable {
    private final ExecutorService executor;
    private final int threadCount;
    private volatile boolean closed;

    RegionWorkerPool(int threadCount) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be > 0");
        }
        this.threadCount = threadCount;
        this.executor = Executors.newFixedThreadPool(threadCount, new RegionThreadFactory());
    }

    int threadCount() {
        return this.threadCount;
    }

    void execute(Runnable task) {
        if (this.closed) {
            throw new IllegalStateException("Region worker pool is closed");
        }
        this.executor.execute(task);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.executor.shutdownNow();
    }

    private static final class RegionThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            final Thread thread = new Thread(task, "NeoForge Region Worker #" + this.counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
