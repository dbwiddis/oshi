/*
 * Copyright 2016-2025 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 */
package oshi.software.os.windows;

import static oshi.software.os.OSService.State.OTHER;
import static oshi.software.os.OSService.State.RUNNING;
import static oshi.software.os.OSService.State.STOPPED;
import static oshi.software.os.OperatingSystem.ProcessFiltering.VALID_PROCESS;
import static oshi.util.Memoizer.defaultExpiration;
import static oshi.util.Memoizer.installedAppsExpiration;
import static oshi.util.Memoizer.memoize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Advapi32Util.EventLogIterator;
import com.sun.jna.platform.win32.Advapi32Util.EventLogRecord;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.VersionHelpers;
import com.sun.jna.platform.win32.W32ServiceManager;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.LUID;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.COM.WbemcliUtil.WmiResult;

import oshi.annotation.concurrent.ThreadSafe;
import oshi.driver.windows.EnumWindows;
import oshi.driver.windows.registry.HkeyUserData;
import oshi.driver.windows.registry.NetSessionData;
import oshi.driver.windows.registry.ProcessPerformanceData;
import oshi.driver.windows.registry.ProcessWtsData;
import oshi.driver.windows.registry.ProcessWtsData.WtsInfo;
import oshi.driver.windows.registry.SessionWtsData;
import oshi.driver.windows.registry.ThreadPerformanceData;
import oshi.driver.windows.wmi.Win32OperatingSystem;
import oshi.driver.windows.wmi.Win32OperatingSystem.OSVersionProperty;
import oshi.driver.windows.wmi.Win32Processor;
import oshi.driver.windows.wmi.Win32Processor.BitnessProperty;
import oshi.jna.ByRef.CloseableHANDLEByReference;
import oshi.jna.ByRef.CloseableIntByReference;
import oshi.jna.ByRef.CloseablePROCESSENTRY32ByReference;
import oshi.jna.Struct.CloseablePerformanceInformation;
import oshi.jna.Struct.CloseableSystemInfo;
import oshi.software.common.AbstractOperatingSystem;
import oshi.software.os.ApplicationInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.InternetProtocolStats;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSDesktopWindow;
import oshi.software.os.OSProcess;
import oshi.software.os.OSService;
import oshi.software.os.OSService.State;
import oshi.software.os.OSSession;
import oshi.software.os.OSThread;
import oshi.util.Constants;
import oshi.util.GlobalConfig;
import oshi.util.Memoizer;
import oshi.util.platform.windows.WmiUtil;
import oshi.util.tuples.Pair;

/**
 * Microsoft Windows, commonly referred to as Windows, is a group of several proprietary graphical operating system
 * families, all of which are developed and marketed by Microsoft.
 */
@ThreadSafe
public class WindowsOperatingSystem extends AbstractOperatingSystem {

    private static final Logger LOG = LoggerFactory.getLogger(WindowsOperatingSystem.class);

    private static final boolean USE_PROCSTATE_SUSPENDED = GlobalConfig
            .get(GlobalConfig.OSHI_OS_WINDOWS_PROCSTATE_SUSPENDED, false);

    private static final boolean IS_VISTA_OR_GREATER = VersionHelpers.IsWindowsVistaOrGreater();

    /*
     * Windows event log name
     */
    private static Supplier<String> systemLog = memoize(WindowsOperatingSystem::querySystemLog,
            TimeUnit.HOURS.toNanos(1));

    private static final long BOOTTIME = querySystemBootTime();

    static {
        enableDebugPrivilege();
    }

    /*
     * OSProcess code will need to know bitness of current process
     */
    private static final boolean X86 = isCurrentX86();
    private static final boolean WOW = isCurrentWow();

    private final Supplier<List<ApplicationInfo>> installedAppsSupplier = Memoizer
            .memoize(WindowsInstalledApps::queryInstalledApps, installedAppsExpiration());

    /*
     * Cache full process stats queries. Second query will only populate if first one returns null.
     */
    private Supplier<Map<Integer, ProcessPerformanceData.PerfCounterBlock>> processMapFromRegistry = memoize(
            WindowsOperatingSystem::queryProcessMapFromRegistry, defaultExpiration());
    private Supplier<Map<Integer, ProcessPerformanceData.PerfCounterBlock>> processMapFromPerfCounters = memoize(
            WindowsOperatingSystem::queryProcessMapFromPerfCounters, defaultExpiration());
    /*
     * Cache full thread stats queries. Second query will only populate if first one returns null. Only used if
     * USE_PROCSTATE_SUSPENDED is set true.
     */
    private Supplier<Map<Integer, ThreadPerformanceData.PerfCounterBlock>> threadMapFromRegistry = memoize(
            WindowsOperatingSystem::queryThreadMapFromRegistry, defaultExpiration());
    private Supplier<Map<Integer, ThreadPerformanceData.PerfCounterBlock>> threadMapFromPerfCounters = memoize(
            WindowsOperatingSystem::queryThreadMapFromPerfCounters, defaultExpiration());

    @Override
    public String queryManufacturer() {
        return "Microsoft";
    }

    @Override
    public Pair<String, OSVersionInfo> queryFamilyVersionInfo() {
        String version = System.getProperty("os.name");
        if (version.startsWith("Windows ")) {
            version = version.substring(8);
        }

        String sp = null;
        int suiteMask = 0;
        String buildNumber = "";
        WmiResult<OSVersionProperty> versionInfo = Win32OperatingSystem.queryOsVersion();
        if (versionInfo.getResultCount() > 0) {
            sp = WmiUtil.getString(versionInfo, OSVersionProperty.CSDVERSION, 0);
            if (!sp.isEmpty() && !Constants.UNKNOWN.equals(sp)) {
                version = version + " " + sp.replace("Service Pack ", "SP");
            }
            suiteMask = WmiUtil.getUint32(versionInfo, OSVersionProperty.SUITEMASK, 0);
            buildNumber = WmiUtil.getString(versionInfo, OSVersionProperty.BUILDNUMBER, 0);
        }
        String codeName = parseCodeName(suiteMask);
        // Older JDKs don't recognize Win11 and Server2022
        if ("10".equals(version) && buildNumber.compareTo("22000") >= 0) {
            version = "11";
        }
        if ("Server 2016".equals(version) && buildNumber.compareTo("17762") > 0) {
            version = "Server 2019";
        }
        if ("Server 2019".equals(version) && buildNumber.compareTo("20347") > 0) {
            version = "Server 2022";
        }
        if ("Server 2022".equals(version) && buildNumber.compareTo("26039") > 0) {
            version = "Server 2025";
        }
        return new Pair<>("Windows", new OSVersionInfo(version, codeName, buildNumber));
    }

    /**
     * Gets suites available on the system and return as a codename
     *
     * @param suiteMask The suite mask bitmask
     *
     * @return Suites
     */
    private static String parseCodeName(int suiteMask) {
        List<String> suites = new ArrayList<>();
        if ((suiteMask & 0x00000002) != 0) {
            suites.add("Enterprise");
        }
        if ((suiteMask & 0x00000004) != 0) {
            suites.add("BackOffice");
        }
        if ((suiteMask & 0x00000008) != 0) {
            suites.add("Communications Server");
        }
        if ((suiteMask & 0x00000080) != 0) {
            suites.add("Datacenter");
        }
        if ((suiteMask & 0x00000200) != 0) {
            suites.add("Home");
        }
        if ((suiteMask & 0x00000400) != 0) {
            suites.add("Web Server");
        }
        if ((suiteMask & 0x00002000) != 0) {
            suites.add("Storage Server");
        }
        if ((suiteMask & 0x00004000) != 0) {
            suites.add("Compute Cluster");
        }
        if ((suiteMask & 0x00008000) != 0) {
            suites.add("Home Server");
        }
        return String.join(",", suites);
    }

    @Override
    protected int queryBitness(int jvmBitness) {
        if (jvmBitness < 64 && System.getenv("ProgramFiles(x86)") != null && IS_VISTA_OR_GREATER) {
            WmiResult<BitnessProperty> bitnessMap = Win32Processor.queryBitness();
            if (bitnessMap.getResultCount() > 0) {
                return WmiUtil.getUint16(bitnessMap, BitnessProperty.ADDRESSWIDTH, 0);
            }
        }
        return jvmBitness;
    }

    @Override
    public boolean isElevated() {
        return Advapi32Util.isCurrentProcessElevated();
    }

    @Override
    public FileSystem getFileSystem() {
        return new WindowsFileSystem();
    }

    @Override
    public InternetProtocolStats getInternetProtocolStats() {
        return new WindowsInternetProtocolStats();
    }

    @Override
    public List<OSSession> getSessions() {
        List<OSSession> whoList = HkeyUserData.queryUserSessions();
        whoList.addAll(SessionWtsData.queryUserSessions());
        whoList.addAll(NetSessionData.queryUserSessions());
        return whoList;
    }

    @Override
    public List<OSProcess> getProcesses(Collection<Integer> pids) {
        return processMapToList(pids);
    }

    @Override
    public List<OSProcess> queryAllProcesses() {
        return processMapToList(null);
    }

    @Override
    public List<OSProcess> queryChildProcesses(int parentPid) {
        Set<Integer> descendantPids = getChildrenOrDescendants(getParentPidsFromSnapshot(), parentPid, false);
        return processMapToList(descendantPids);
    }

    @Override
    public List<OSProcess> queryDescendantProcesses(int parentPid) {
        Set<Integer> descendantPids = getChildrenOrDescendants(getParentPidsFromSnapshot(), parentPid, true);
        return processMapToList(descendantPids);
    }

    private static Map<Integer, Integer> getParentPidsFromSnapshot() {
        Map<Integer, Integer> parentPidMap = new HashMap<>();
        // Get processes from ToolHelp API for parent PID
        try (CloseablePROCESSENTRY32ByReference processEntry = new CloseablePROCESSENTRY32ByReference()) {
            WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS,
                    new DWORD(0));
            try {
                while (Kernel32.INSTANCE.Process32Next(snapshot, processEntry)) {
                    parentPidMap.put(processEntry.th32ProcessID.intValue(),
                            processEntry.th32ParentProcessID.intValue());
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(snapshot);
            }
        }
        return parentPidMap;
    }

    @Override
    public OSProcess getProcess(int pid) {
        List<OSProcess> procList = processMapToList(Arrays.asList(pid));
        return procList.isEmpty() ? null : procList.get(0);
    }

    private List<OSProcess> processMapToList(Collection<Integer> pids) {
        // Get data from the registry if possible
        Map<Integer, ProcessPerformanceData.PerfCounterBlock> processMap = processMapFromRegistry.get();
        // otherwise performance counters with WMI backup
        if (processMap == null || processMap.isEmpty()) {
            processMap = (pids == null) ? processMapFromPerfCounters.get()
                    : ProcessPerformanceData.buildProcessMapFromPerfCounters(pids);
        }
        Map<Integer, ThreadPerformanceData.PerfCounterBlock> threadMap = null;
        if (USE_PROCSTATE_SUSPENDED) {
            // Get data from the registry if possible
            threadMap = threadMapFromRegistry.get();
            // otherwise performance counters with WMI backup
            if (threadMap == null || threadMap.isEmpty()) {
                threadMap = (pids == null) ? threadMapFromPerfCounters.get()
                        : ThreadPerformanceData.buildThreadMapFromPerfCounters(pids);
            }
        }

        Map<Integer, WtsInfo> processWtsMap = ProcessWtsData.queryProcessWtsMap(pids);

        Set<Integer> mapKeys = new HashSet<>(processWtsMap.keySet());
        mapKeys.retainAll(processMap.keySet());

        final Map<Integer, ProcessPerformanceData.PerfCounterBlock> finalProcessMap = processMap;
        final Map<Integer, ThreadPerformanceData.PerfCounterBlock> finalThreadMap = threadMap;
        return mapKeys.stream().parallel()
                .map(pid -> new WindowsOSProcess(pid, this, finalProcessMap, processWtsMap, finalThreadMap))
                .filter(VALID_PROCESS).collect(Collectors.toList());
    }

    private static Map<Integer, ProcessPerformanceData.PerfCounterBlock> queryProcessMapFromRegistry() {
        return ProcessPerformanceData.buildProcessMapFromRegistry(null);
    }

    private static Map<Integer, ProcessPerformanceData.PerfCounterBlock> queryProcessMapFromPerfCounters() {
        return ProcessPerformanceData.buildProcessMapFromPerfCounters(null);
    }

    private static Map<Integer, ThreadPerformanceData.PerfCounterBlock> queryThreadMapFromRegistry() {
        return ThreadPerformanceData.buildThreadMapFromRegistry(null);
    }

    private static Map<Integer, ThreadPerformanceData.PerfCounterBlock> queryThreadMapFromPerfCounters() {
        return ThreadPerformanceData.buildThreadMapFromPerfCounters(null);
    }

    @Override
    public int getProcessId() {
        return Kernel32.INSTANCE.GetCurrentProcessId();
    }

    @Override
    public int getProcessCount() {
        try (CloseablePerformanceInformation perfInfo = new CloseablePerformanceInformation()) {
            if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
                LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
                return 0;
            }
            return perfInfo.ProcessCount.intValue();
        }
    }

    @Override
    public int getThreadId() {
        return Kernel32.INSTANCE.GetCurrentThreadId();
    }

    @Override
    public OSThread getCurrentThread() {
        OSProcess proc = getCurrentProcess();
        final int tid = getThreadId();
        return proc.getThreadDetails().stream().filter(t -> t.getThreadId() == tid).findFirst()
                .orElse(new WindowsOSThread(proc.getProcessID(), tid, null, null));
    }

    @Override
    public int getThreadCount() {
        try (CloseablePerformanceInformation perfInfo = new CloseablePerformanceInformation()) {
            if (!Psapi.INSTANCE.GetPerformanceInfo(perfInfo, perfInfo.size())) {
                LOG.error("Failed to get Performance Info. Error code: {}", Kernel32.INSTANCE.GetLastError());
                return 0;
            }
            return perfInfo.ThreadCount.intValue();
        }
    }

    @Override
    public long getSystemUptime() {
        return querySystemUptime();
    }

    private static long querySystemUptime() {
        // Uptime is in seconds so divide milliseconds
        // GetTickCount64 requires Vista (6.0) or later
        if (IS_VISTA_OR_GREATER) {
            return Kernel32.INSTANCE.GetTickCount64() / 1000L;
        } else {
            // 32 bit rolls over at ~ 49 days
            return Kernel32.INSTANCE.GetTickCount() / 1000L;
        }
    }

    @Override
    public long getSystemBootTime() {
        return BOOTTIME;
    }

    private static long querySystemBootTime() {
        String eventLog = systemLog.get();
        if (eventLog != null) {
            try {
                EventLogIterator iter = new EventLogIterator(null, eventLog, WinNT.EVENTLOG_BACKWARDS_READ);
                // Get the most recent boot event (ID 12) from the Event log. If Windows "Fast
                // Startup" is enabled we may not see event 12, so also check for most recent ID
                // 6005 (Event log startup) as a reasonably close backup.
                long event6005Time = 0L;
                while (iter.hasNext()) {
                    EventLogRecord logRecord = iter.next();
                    if (logRecord.getStatusCode() == 12) {
                        // Event 12 is system boot. We want this value unless we find two 6005 events
                        // first (may occur with Fast Boot)
                        return logRecord.getRecord().TimeGenerated.longValue();
                    } else if (logRecord.getStatusCode() == 6005) {
                        // If we already found one, this means we've found a second one without finding
                        // an event 12. Return the latest one.
                        if (event6005Time > 0) {
                            return event6005Time;
                        }
                        // First 6005; tentatively assign
                        event6005Time = logRecord.getRecord().TimeGenerated.longValue();
                    }
                }
                // Only one 6005 found, return
                if (event6005Time > 0) {
                    return event6005Time;
                }
            } catch (Win32Exception e) {
                LOG.warn("Can't open event log \"{}\".", eventLog);
            }
        }
        // If we get this far, event log reading has failed, either from no log or no
        // startup times. Subtract up time from current time as a reasonable proxy.
        return System.currentTimeMillis() / 1000L - querySystemUptime();
    }

    @Override
    public NetworkParams getNetworkParams() {
        return new WindowsNetworkParams();
    }

    /**
     * Attempts to enable debug privileges for this process, required for OpenProcess() to get processes other than the
     * current user. Requires elevated permissions.
     *
     * @return {@code true} if debug privileges were successfully enabled.
     */
    private static boolean enableDebugPrivilege() {
        try (CloseableHANDLEByReference hToken = new CloseableHANDLEByReference()) {
            boolean success = Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(),
                    WinNT.TOKEN_QUERY | WinNT.TOKEN_ADJUST_PRIVILEGES, hToken);
            if (!success) {
                LOG.error("OpenProcessToken failed. Error: {}", Native.getLastError());
                return false;
            }
            try {
                LUID luid = new LUID();
                success = Advapi32.INSTANCE.LookupPrivilegeValue(null, WinNT.SE_DEBUG_NAME, luid);
                if (!success) {
                    LOG.error("LookupPrivilegeValue failed. Error: {}", Native.getLastError());
                    return false;
                }
                WinNT.TOKEN_PRIVILEGES tkp = new WinNT.TOKEN_PRIVILEGES(1);
                tkp.Privileges[0] = new WinNT.LUID_AND_ATTRIBUTES(luid, new DWORD(WinNT.SE_PRIVILEGE_ENABLED));
                success = Advapi32.INSTANCE.AdjustTokenPrivileges(hToken.getValue(), false, tkp, 0, null, null);
                int err = Native.getLastError();
                if (!success) {
                    LOG.error("AdjustTokenPrivileges failed. Error: {}", err);
                    return false;
                } else if (err == WinError.ERROR_NOT_ALL_ASSIGNED) {
                    LOG.debug("Debug privileges not enabled.");
                    return false;
                }
            } finally {
                Kernel32.INSTANCE.CloseHandle(hToken.getValue());
            }
        }
        return true;
    }

    @Override
    public List<OSService> getServices() {
        try (W32ServiceManager sm = new W32ServiceManager()) {
            sm.open(Winsvc.SC_MANAGER_ENUMERATE_SERVICE);
            Winsvc.ENUM_SERVICE_STATUS_PROCESS[] services = sm.enumServicesStatusExProcess(WinNT.SERVICE_WIN32,
                    Winsvc.SERVICE_STATE_ALL, null);
            List<OSService> svcArray = new ArrayList<>();
            for (Winsvc.ENUM_SERVICE_STATUS_PROCESS service : services) {
                State state;
                switch (service.ServiceStatusProcess.dwCurrentState) {
                case 1:
                    state = STOPPED;
                    break;
                case 4:
                    state = RUNNING;
                    break;
                default:
                    state = OTHER;
                    break;
                }
                svcArray.add(new OSService(service.lpDisplayName, service.ServiceStatusProcess.dwProcessId, state));
            }
            return svcArray;
        } catch (com.sun.jna.platform.win32.Win32Exception ex) {
            LOG.error("Win32Exception: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private static String querySystemLog() {
        String systemLog = GlobalConfig.get(GlobalConfig.OSHI_OS_WINDOWS_EVENTLOG, "System");
        if (systemLog.isEmpty()) {
            // Use faster boot time approximation
            return null;
        }
        // Check whether it works
        HANDLE h = Advapi32.INSTANCE.OpenEventLog(null, systemLog);
        if (h == null) {
            LOG.warn("Unable to open configured system Event log \"{}\". Calculating boot time from uptime.",
                    systemLog);
            return null;
        }
        return systemLog;
    }

    @Override
    public List<OSDesktopWindow> getDesktopWindows(boolean visibleOnly) {
        return EnumWindows.queryDesktopWindows(visibleOnly);
    }

    @Override
    public List<ApplicationInfo> getInstalledApplications() {
        return installedAppsSupplier.get();
    }

    /*
     * Package-private methods for use by WindowsOSProcess to limit process memory queries to processes with same
     * bitness as the current one
     */
    /**
     * Is the processor architecture x86?
     *
     * @return true if the processor architecture is Intel x86
     */
    static boolean isX86() {
        return X86;
    }

    private static boolean isCurrentX86() {
        try (CloseableSystemInfo sysinfo = new CloseableSystemInfo()) {
            Kernel32.INSTANCE.GetNativeSystemInfo(sysinfo);
            return (0 == sysinfo.processorArchitecture.pi.wProcessorArchitecture.intValue());
        }
    }

    /**
     * Is the current operating process x86 or x86-compatibility mode?
     *
     * @return true if the current process is 32-bit
     */
    static boolean isWow() {
        return WOW;
    }

    /**
     * Is the specified process x86 or x86-compatibility mode?
     *
     * @param h The handle to the processs to check
     * @return true if the process is 32-bit
     */
    static boolean isWow(HANDLE h) {
        if (X86) {
            return true;
        }
        try (CloseableIntByReference isWow = new CloseableIntByReference()) {
            Kernel32.INSTANCE.IsWow64Process(h, isWow);
            return isWow.getValue() != 0;
        }
    }

    private static boolean isCurrentWow() {
        if (X86) {
            return true;
        }
        HANDLE h = Kernel32.INSTANCE.GetCurrentProcess();
        return (h == null) ? false : isWow(h);
    }
}
