/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.software.os;

import oshi.annotation.PublicApi;
import oshi.annotation.concurrent.ThreadSafe;

/**
 * Represents cgroup (control group) information for containerized environments.
 * <p>
 * This interface provides access to resource limits and usage metrics for processes running in cgroups, supporting both
 * cgroup v1 and v2.
 * <p>
 * Sentinel values are used to indicate special conditions:
 * <ul>
 * <li>{@code -1} for unlimited quota, period, or PIDs</li>
 * <li>{@code Long.MAX_VALUE} for unlimited memory</li>
 * <li>{@code 0} for version when not in a cgroup</li>
 * <li>{@code -1.0d} for unlimited effective CPUs</li>
 * </ul>
 */
@PublicApi
@ThreadSafe
public interface CgroupInfo {

    /**
     * Returns whether the current process is running in a containerized environment (cgroup).
     *
     * @return {@code true} if running in a cgroup, {@code false} otherwise
     */
    boolean isContainerized();

    /**
     * Returns the cgroup version being used.
     *
     * @return {@code 1} for cgroup v1, {@code 2} for cgroup v2, or {@code 0} if not in a cgroup
     */
    int getVersion();

    /**
     * Returns the CPU quota for the cgroup in microseconds.
     *
     * @return the CPU quota in microseconds, or {@code -1} if unlimited
     */
    long getCpuQuota();

    /**
     * Returns the CPU period for the cgroup in microseconds.
     *
     * @return the CPU period in microseconds, or {@code -1} if unlimited (typically {@code 100000} as default)
     */
    long getCpuPeriod();

    /**
     * Returns the total CPU usage for the cgroup in nanoseconds.
     *
     * @return the CPU usage in nanoseconds
     */
    long getCpuUsage();

    /**
     * Returns the effective number of CPUs available to the cgroup.
     * <p>
     * This is calculated as {@code quota / period} when a quota is set.
     *
     * @return the effective number of CPUs as a double, or {@code -1.0d} if unlimited
     */
    double getEffectiveCpus();

    /**
     * Returns the memory limit for the cgroup in bytes.
     *
     * @return the memory limit in bytes, or {@code Long.MAX_VALUE} if unlimited
     */
    long getMemoryLimit();

    /**
     * Returns the current memory usage for the cgroup in bytes.
     *
     * @return the memory usage in bytes
     */
    long getMemoryUsage();

    /**
     * Returns the maximum number of PIDs allowed in the cgroup.
     *
     * @return the PID limit, or {@code -1} if unlimited
     */
    long getPidLimit();

    /**
     * Returns the current number of PIDs in the cgroup.
     *
     * @return the current PID count
     */
    long getPidCurrent();
}
