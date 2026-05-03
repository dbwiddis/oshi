/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.software.common.os.linux;

import java.util.List;
import java.util.function.Supplier;

import oshi.annotation.concurrent.ThreadSafe;
import oshi.software.os.CgroupInfo;
import oshi.util.FileUtil;
import oshi.util.Memoizer;
import oshi.util.ParseUtil;
import oshi.util.linux.ProcPath;
import oshi.util.linux.SysPath;

/**
 * Linux implementation of {@link CgroupInfo} supporting both cgroup v2 and v1.
 * <p>
 * This implementation detects the cgroup version and reads resource limits and usage from the appropriate cgroup
 * filesystem paths. Limit values are memoized while usage values are read fresh on each call.
 */
@ThreadSafe
public class LinuxCgroupInfo implements CgroupInfo {

    private static final long MICROSECONDS_PER_NANOSECOND = 1000L;

    private final Supplier<Integer> versionSupplier = Memoizer.memoize(this::detectVersion);
    private final Supplier<String> cgroupPathSupplier = Memoizer.memoize(this::parseCgroupPath);
    private final Supplier<Long> cpuQuotaSupplier = Memoizer.memoize(this::readCpuQuota);
    private final Supplier<Long> cpuPeriodSupplier = Memoizer.memoize(this::readCpuPeriod);
    private final Supplier<Long> memoryLimitSupplier = Memoizer.memoize(this::readMemoryLimit);
    private final Supplier<Long> pidLimitSupplier = Memoizer.memoize(this::readPidLimit);

    /**
     * Constructs a new LinuxCgroupInfo instance.
     */
    public LinuxCgroupInfo() {
    }

    @Override
    public boolean isContainerized() {
        return getVersion() > 0;
    }

    @Override
    public int getVersion() {
        return versionSupplier.get();
    }

    @Override
    public long getCpuQuota() {
        return cpuQuotaSupplier.get();
    }

    @Override
    public long getCpuPeriod() {
        return cpuPeriodSupplier.get();
    }

    @Override
    public long getCpuUsage() {
        int version = getVersion();
        if (version == 2) {
            return readCpuUsageV2();
        } else if (version == 1) {
            return readCpuUsageV1();
        }
        return 0L;
    }

    @Override
    public double getEffectiveCpus() {
        long quota = getCpuQuota();
        long period = getCpuPeriod();
        if (quota <= 0 || period <= 0) {
            return -1.0d;
        }
        return (double) quota / period;
    }

    @Override
    public long getMemoryLimit() {
        return memoryLimitSupplier.get();
    }

    @Override
    public long getMemoryUsage() {
        int version = getVersion();
        if (version == 2) {
            return readMemoryUsageV2();
        } else if (version == 1) {
            return readMemoryUsageV1();
        }
        return 0L;
    }

    @Override
    public long getPidLimit() {
        return pidLimitSupplier.get();
    }

    @Override
    public long getPidCurrent() {
        int version = getVersion();
        if (version == 2) {
            return readPidCurrentV2();
        } else if (version == 1) {
            return readPidCurrentV1();
        }
        return 0L;
    }

    private int detectVersion() {
        List<String> filesystems = FileUtil.readFile(ProcPath.FILESYSTEMS);
        boolean hasCgroup2 = filesystems.stream().anyMatch(line -> line.contains("cgroup2"));

        List<String> selfCgroup = FileUtil.readFile(ProcPath.SELF_CGROUP);
        if (selfCgroup.isEmpty()) {
            return 0;
        }

        // Check for v2: single entry with format "0::/{path}"
        if (hasCgroup2 && selfCgroup.size() == 1 && selfCgroup.get(0).startsWith("0::")) {
            return 2;
        }

        // Multiple entries indicate v1 or hybrid mode, fall back to v1
        if (!selfCgroup.isEmpty()) {
            return 1;
        }

        return 0;
    }

    private String parseCgroupPath() {
        List<String> selfCgroup = FileUtil.readFile(ProcPath.SELF_CGROUP);
        if (selfCgroup.isEmpty()) {
            return "";
        }

        int version = getVersion();
        if (version == 2) {
            // v2 format: "0::/{path}"
            String line = selfCgroup.get(0);
            if (line.startsWith("0::")) {
                String path = line.substring(3);
                return path.isEmpty() ? "/" : path;
            }
        }

        // For v1, return empty - we'll use controller-specific paths
        return "";
    }

    private String getV2CgroupBase() {
        String cgroupPath = cgroupPathSupplier.get();
        if (cgroupPath.isEmpty() || cgroupPath.equals("/")) {
            return SysPath.CGROUP;
        }
        // Remove leading slash for path concatenation
        if (cgroupPath.startsWith("/")) {
            cgroupPath = cgroupPath.substring(1);
        }
        return SysPath.CGROUP + cgroupPath + "/";
    }

    private String getV1ControllerPath(String controller) {
        List<String> selfCgroup = FileUtil.readFile(ProcPath.SELF_CGROUP);
        for (String line : selfCgroup) {
            // v1 format: "hierarchy-id:controllers:path"
            String[] parts = line.split(":");
            if (parts.length >= 3) {
                String controllers = parts[1];
                String path = parts[2];
                if (controllers.contains(controller) || controllers.isEmpty()) {
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    return SysPath.CGROUP + controller + "/" + path + "/";
                }
            }
        }
        return SysPath.CGROUP + controller + "/";
    }

    private long readCpuQuota() {
        int version = getVersion();
        if (version == 2) {
            return readCpuQuotaV2();
        } else if (version == 1) {
            return readCpuQuotaV1();
        }
        return -1L;
    }

    private long readCpuQuotaV2() {
        String cpuMax = FileUtil.getStringFromFile(getV2CgroupBase() + "cpu.max");
        if (cpuMax.isEmpty()) {
            return -1L;
        }
        String[] parts = cpuMax.split("\\s+");
        if (parts.length >= 1) {
            if ("max".equalsIgnoreCase(parts[0])) {
                return -1L;
            }
            return ParseUtil.parseLongOrDefault(parts[0], -1L);
        }
        return -1L;
    }

    private long readCpuQuotaV1() {
        String quotaPath = getV1ControllerPath("cpu") + "cpu.cfs_quota_us";
        long quota = FileUtil.getLongFromFile(quotaPath);
        return quota == 0 ? -1L : quota;
    }

    private long readCpuPeriod() {
        int version = getVersion();
        if (version == 2) {
            return readCpuPeriodV2();
        } else if (version == 1) {
            return readCpuPeriodV1();
        }
        return 100000L;
    }

    private long readCpuPeriodV2() {
        String cpuMax = FileUtil.getStringFromFile(getV2CgroupBase() + "cpu.max");
        if (cpuMax.isEmpty()) {
            return 100000L;
        }
        String[] parts = cpuMax.split("\\s+");
        if (parts.length >= 2) {
            return ParseUtil.parseLongOrDefault(parts[1], 100000L);
        }
        return 100000L;
    }

    private long readCpuPeriodV1() {
        String periodPath = getV1ControllerPath("cpu") + "cpu.cfs_period_us";
        long period = FileUtil.getLongFromFile(periodPath);
        return period == 0 ? 100000L : period;
    }

    private long readCpuUsageV2() {
        String cpuStat = FileUtil.getStringFromFile(getV2CgroupBase() + "cpu.stat");
        if (cpuStat.isEmpty()) {
            // Try reading the full file
            List<String> lines = FileUtil.readFile(getV2CgroupBase() + "cpu.stat");
            for (String line : lines) {
                if (line.startsWith("usage_usec")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        long usec = ParseUtil.parseLongOrDefault(parts[1], 0L);
                        return usec * MICROSECONDS_PER_NANOSECOND;
                    }
                }
            }
        }
        return 0L;
    }

    private long readCpuUsageV1() {
        String usagePath = getV1ControllerPath("cpuacct") + "cpuacct.usage";
        return FileUtil.getLongFromFile(usagePath);
    }

    private long readMemoryLimit() {
        int version = getVersion();
        if (version == 2) {
            return readMemoryLimitV2();
        } else if (version == 1) {
            return readMemoryLimitV1();
        }
        return Long.MAX_VALUE;
    }

    private long readMemoryLimitV2() {
        String memMax = FileUtil.getStringFromFile(getV2CgroupBase() + "memory.max");
        if (memMax.isEmpty() || "max".equalsIgnoreCase(memMax.trim())) {
            return Long.MAX_VALUE;
        }
        return ParseUtil.parseLongOrDefault(memMax.trim(), Long.MAX_VALUE);
    }

    private long readMemoryLimitV1() {
        String limitPath = getV1ControllerPath("memory") + "memory.limit_in_bytes";
        long limit = FileUtil.getLongFromFile(limitPath);
        // In v1, a very large value (close to Long.MAX_VALUE) indicates unlimited
        if (limit == 0 || limit > Long.MAX_VALUE - 4096) {
            return Long.MAX_VALUE;
        }
        return limit;
    }

    private long readMemoryUsageV2() {
        String memCurrent = FileUtil.getStringFromFile(getV2CgroupBase() + "memory.current");
        return ParseUtil.parseLongOrDefault(memCurrent.trim(), 0L);
    }

    private long readMemoryUsageV1() {
        String usagePath = getV1ControllerPath("memory") + "memory.usage_in_bytes";
        return FileUtil.getLongFromFile(usagePath);
    }

    private long readPidLimit() {
        int version = getVersion();
        if (version == 2) {
            return readPidLimitV2();
        } else if (version == 1) {
            return readPidLimitV1();
        }
        return -1L;
    }

    private long readPidLimitV2() {
        String pidsMax = FileUtil.getStringFromFile(getV2CgroupBase() + "pids.max");
        if (pidsMax.isEmpty() || "max".equalsIgnoreCase(pidsMax.trim())) {
            return -1L;
        }
        return ParseUtil.parseLongOrDefault(pidsMax.trim(), -1L);
    }

    private long readPidLimitV1() {
        String maxPath = getV1ControllerPath("pids") + "pids.max";
        String pidsMax = FileUtil.getStringFromFile(maxPath);
        if (pidsMax.isEmpty() || "max".equalsIgnoreCase(pidsMax.trim())) {
            return -1L;
        }
        long limit = ParseUtil.parseLongOrDefault(pidsMax.trim(), -1L);
        return limit == 0 ? -1L : limit;
    }

    private long readPidCurrentV2() {
        String pidsCurrent = FileUtil.getStringFromFile(getV2CgroupBase() + "pids.current");
        return ParseUtil.parseLongOrDefault(pidsCurrent.trim(), 0L);
    }

    private long readPidCurrentV1() {
        String currentPath = getV1ControllerPath("pids") + "pids.current";
        return FileUtil.getLongFromFile(currentPath);
    }
}
