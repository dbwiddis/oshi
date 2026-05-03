/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.software.common;

import oshi.annotation.concurrent.ThreadSafe;
import oshi.software.os.CgroupInfo;

/**
 * A no-op {@link CgroupInfo} implementation returned by platforms that do not support cgroups.
 * <p>
 * This implementation returns sentinel values indicating that the process is not running in a containerized
 * environment:
 * <ul>
 * <li>{@link #isContainerized()} returns {@code false}</li>
 * <li>{@link #getVersion()} returns {@code 0}</li>
 * <li>{@link #getCpuQuota()} returns {@code -1L} (unlimited)</li>
 * <li>{@link #getCpuPeriod()} returns {@code 100000L} (standard default)</li>
 * <li>{@link #getCpuUsage()} returns {@code 0L}</li>
 * <li>{@link #getEffectiveCpus()} returns {@code -1.0d} (unlimited)</li>
 * <li>{@link #getMemoryLimit()} returns {@code Long.MAX_VALUE} (unlimited)</li>
 * <li>{@link #getMemoryUsage()} returns {@code 0L}</li>
 * <li>{@link #getPidLimit()} returns {@code -1L} (unlimited)</li>
 * <li>{@link #getPidCurrent()} returns {@code 0L}</li>
 * </ul>
 */
@ThreadSafe
public final class NoOpCgroupInfo implements CgroupInfo {

    /**
     * Singleton instance for use by non-Linux platforms.
     */
    public static final NoOpCgroupInfo INSTANCE = new NoOpCgroupInfo();

    /**
     * Constructs a new NoOpCgroupInfo instance.
     */
    NoOpCgroupInfo() {
    }

    @Override
    public boolean isContainerized() {
        return false;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public long getCpuQuota() {
        return -1L;
    }

    @Override
    public long getCpuPeriod() {
        return 100000L;
    }

    @Override
    public long getCpuUsage() {
        return 0L;
    }

    @Override
    public double getEffectiveCpus() {
        return -1.0d;
    }

    @Override
    public long getMemoryLimit() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getMemoryUsage() {
        return 0L;
    }

    @Override
    public long getPidLimit() {
        return -1L;
    }

    @Override
    public long getPidCurrent() {
        return 0L;
    }
}
