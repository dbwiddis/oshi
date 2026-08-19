/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 *
 * Prints the true layout of every Windows struct that oshi-core-ffm maps, taken from the SDK headers, in the same
 * shape LayoutDump prints so the two can be diffed. Fields whose offsets OSHI writes by hand are the point of this:
 * a StructLayout at least computes its own arithmetic, while an OFFSET_ constant is a number somebody worked out.
 */
#include <stdio.h>
#include <stddef.h>
#include <winsock2.h>
#include <ws2ipdef.h>
#include <windows.h>
#include <iphlpapi.h>
#include <netioapi.h>
#include <batclass.h>
#include <setupapi.h>
#include <winperf.h>

#define HDR(s)    printf("  %s  [size=%zu align=%zu]\n", #s, sizeof(s), _Alignof(s))
#define F(s, f)   printf("  %6zu | %-30s %zu\n", offsetof(s, f), #f, sizeof(((s *)0)->f))

int main(void) {
    printf("# Windows SDK ground truth, %d-bit\n", (int)(sizeof(void *) * 8));

    /* Every PF_* value winnt.h defines, so OSHI's feature enum can be checked against the SDK rather than memory.
     * A name the SDK does not define prints as undefined; a value with no name printed is a genuine gap. */
#define PF(name) do { \
        printf("  %-46s = %d\n", #name, name); \
    } while (0)
    printf("# PF_* processor features defined by this SDK\n");
#ifdef PF_FLOATING_POINT_PRECISION_ERRATA
    PF(PF_FLOATING_POINT_PRECISION_ERRATA);
#else
    printf("  %-46s = undefined\n", "PF_FLOATING_POINT_PRECISION_ERRATA");
#endif
#ifdef PF_FLOATING_POINT_EMULATED
    PF(PF_FLOATING_POINT_EMULATED);
#else
    printf("  %-46s = undefined\n", "PF_FLOATING_POINT_EMULATED");
#endif
#ifdef PF_COMPARE_EXCHANGE_DOUBLE
    PF(PF_COMPARE_EXCHANGE_DOUBLE);
#else
    printf("  %-46s = undefined\n", "PF_COMPARE_EXCHANGE_DOUBLE");
#endif
#ifdef PF_MMX_INSTRUCTIONS_AVAILABLE
    PF(PF_MMX_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_MMX_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_PPC_MOVEMEM_64BIT_OK
    PF(PF_PPC_MOVEMEM_64BIT_OK);
#else
    printf("  %-46s = undefined\n", "PF_PPC_MOVEMEM_64BIT_OK");
#endif
#ifdef PF_ALPHA_BYTE_INSTRUCTIONS
    PF(PF_ALPHA_BYTE_INSTRUCTIONS);
#else
    printf("  %-46s = undefined\n", "PF_ALPHA_BYTE_INSTRUCTIONS");
#endif
#ifdef PF_XMMI_INSTRUCTIONS_AVAILABLE
    PF(PF_XMMI_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_XMMI_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_3DNOW_INSTRUCTIONS_AVAILABLE
    PF(PF_3DNOW_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_3DNOW_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_RDTSC_INSTRUCTION_AVAILABLE
    PF(PF_RDTSC_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_RDTSC_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_PAE_ENABLED
    PF(PF_PAE_ENABLED);
#else
    printf("  %-46s = undefined\n", "PF_PAE_ENABLED");
#endif
#ifdef PF_XMMI64_INSTRUCTIONS_AVAILABLE
    PF(PF_XMMI64_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_XMMI64_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_SSE_DAZ_MODE_AVAILABLE
    PF(PF_SSE_DAZ_MODE_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_SSE_DAZ_MODE_AVAILABLE");
#endif
#ifdef PF_NX_ENABLED
    PF(PF_NX_ENABLED);
#else
    printf("  %-46s = undefined\n", "PF_NX_ENABLED");
#endif
#ifdef PF_SSE3_INSTRUCTIONS_AVAILABLE
    PF(PF_SSE3_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_SSE3_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_COMPARE_EXCHANGE128
    PF(PF_COMPARE_EXCHANGE128);
#else
    printf("  %-46s = undefined\n", "PF_COMPARE_EXCHANGE128");
#endif
#ifdef PF_COMPARE64_EXCHANGE128
    PF(PF_COMPARE64_EXCHANGE128);
#else
    printf("  %-46s = undefined\n", "PF_COMPARE64_EXCHANGE128");
#endif
#ifdef PF_CHANNELS_ENABLED
    PF(PF_CHANNELS_ENABLED);
#else
    printf("  %-46s = undefined\n", "PF_CHANNELS_ENABLED");
#endif
#ifdef PF_XSAVE_ENABLED
    PF(PF_XSAVE_ENABLED);
#else
    printf("  %-46s = undefined\n", "PF_XSAVE_ENABLED");
#endif
#ifdef PF_ARM_VFP_32_REGISTERS_AVAILABLE
    PF(PF_ARM_VFP_32_REGISTERS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_VFP_32_REGISTERS_AVAILABLE");
#endif
#ifdef PF_ARM_NEON_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_NEON_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_NEON_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_SECOND_LEVEL_ADDRESS_TRANSLATION
    PF(PF_SECOND_LEVEL_ADDRESS_TRANSLATION);
#else
    printf("  %-46s = undefined\n", "PF_SECOND_LEVEL_ADDRESS_TRANSLATION");
#endif
#ifdef PF_VIRT_FIRMWARE_ENABLED
    PF(PF_VIRT_FIRMWARE_ENABLED);
#else
    printf("  %-46s = undefined\n", "PF_VIRT_FIRMWARE_ENABLED");
#endif
#ifdef PF_RDWRFSGSBASE_AVAILABLE
    PF(PF_RDWRFSGSBASE_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_RDWRFSGSBASE_AVAILABLE");
#endif
#ifdef PF_FASTFAIL_AVAILABLE
    PF(PF_FASTFAIL_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_FASTFAIL_AVAILABLE");
#endif
#ifdef PF_ARM_DIVIDE_INSTRUCTION_AVAILABLE
    PF(PF_ARM_DIVIDE_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_DIVIDE_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_ARM_64BIT_LOADSTORE_ATOMIC
    PF(PF_ARM_64BIT_LOADSTORE_ATOMIC);
#else
    printf("  %-46s = undefined\n", "PF_ARM_64BIT_LOADSTORE_ATOMIC");
#endif
#ifdef PF_ARM_EXTERNAL_CACHE_AVAILABLE
    PF(PF_ARM_EXTERNAL_CACHE_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_EXTERNAL_CACHE_AVAILABLE");
#endif
#ifdef PF_ARM_FMAC_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_FMAC_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_FMAC_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_RDRAND_INSTRUCTION_AVAILABLE
    PF(PF_RDRAND_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_RDRAND_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_ARM_V8_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V8_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V8_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_V8_CRYPTO_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V8_CRYPTO_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V8_CRYPTO_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_V8_CRC32_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V8_CRC32_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V8_CRC32_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_RDTSCP_INSTRUCTION_AVAILABLE
    PF(PF_RDTSCP_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_RDTSCP_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_RDPID_INSTRUCTION_AVAILABLE
    PF(PF_RDPID_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_RDPID_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_ARM_V81_ATOMIC_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V81_ATOMIC_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V81_ATOMIC_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_MONITORX_INSTRUCTION_AVAILABLE
    PF(PF_MONITORX_INSTRUCTION_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_MONITORX_INSTRUCTION_AVAILABLE");
#endif
#ifdef PF_SSSE3_INSTRUCTIONS_AVAILABLE
    PF(PF_SSSE3_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_SSSE3_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_SSE4_1_INSTRUCTIONS_AVAILABLE
    PF(PF_SSE4_1_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_SSE4_1_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_SSE4_2_INSTRUCTIONS_AVAILABLE
    PF(PF_SSE4_2_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_SSE4_2_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_AVX_INSTRUCTIONS_AVAILABLE
    PF(PF_AVX_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_AVX_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_AVX2_INSTRUCTIONS_AVAILABLE
    PF(PF_AVX2_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_AVX2_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_AVX512F_INSTRUCTIONS_AVAILABLE
    PF(PF_AVX512F_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_AVX512F_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ERMS_AVAILABLE
    PF(PF_ERMS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ERMS_AVAILABLE");
#endif
#ifdef PF_ARM_V82_DP_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V82_DP_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V82_DP_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_V83_JSCVT_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V83_JSCVT_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V83_JSCVT_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_V83_LRCPC_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_V83_LRCPC_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_V83_LRCPC_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_SVE_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_SVE_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_SVE_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE
    PF(PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_ARM_SVE2_INSTRUCTIONS_AVAILABLE");
#endif
#ifdef PF_BMI2_INSTRUCTIONS_AVAILABLE
    PF(PF_BMI2_INSTRUCTIONS_AVAILABLE);
#else
    printf("  %-46s = undefined\n", "PF_BMI2_INSTRUCTIONS_AVAILABLE");
#endif

    /* winperf.h - the HKEY_PERFORMANCE_DATA blocks walked by HkeyPerformanceDataUtil. These are read by stepping
     * a pointer through a variable-length buffer, so a wrong offset here misreads the whole traversal rather than
     * one field. PERF_DATA_BLOCK is the one to look at first: SYSTEMTIME is followed by a LARGE_INTEGER, so where
     * the alignment padding sits decides every offset after it. */
    HDR(PERF_DATA_BLOCK);
    F(PERF_DATA_BLOCK, Signature);
    F(PERF_DATA_BLOCK, LittleEndian);
    F(PERF_DATA_BLOCK, Version);
    F(PERF_DATA_BLOCK, Revision);
    F(PERF_DATA_BLOCK, TotalByteLength);
    F(PERF_DATA_BLOCK, HeaderLength);
    F(PERF_DATA_BLOCK, NumObjectTypes);
    F(PERF_DATA_BLOCK, DefaultObject);
    F(PERF_DATA_BLOCK, SystemTime);
    F(PERF_DATA_BLOCK, PerfTime);
    F(PERF_DATA_BLOCK, PerfFreq);
    F(PERF_DATA_BLOCK, PerfTime100nSec);
    F(PERF_DATA_BLOCK, SystemNameLength);
    F(PERF_DATA_BLOCK, SystemNameOffset);

    HDR(PERF_OBJECT_TYPE);
    F(PERF_OBJECT_TYPE, TotalByteLength);
    F(PERF_OBJECT_TYPE, DefinitionLength);
    F(PERF_OBJECT_TYPE, HeaderLength);
    F(PERF_OBJECT_TYPE, ObjectNameTitleIndex);
    F(PERF_OBJECT_TYPE, ObjectHelpTitleIndex);
    F(PERF_OBJECT_TYPE, DetailLevel);
    F(PERF_OBJECT_TYPE, NumCounters);
    F(PERF_OBJECT_TYPE, DefaultCounter);
    F(PERF_OBJECT_TYPE, NumInstances);
    F(PERF_OBJECT_TYPE, CodePage);
    F(PERF_OBJECT_TYPE, PerfTime);
    F(PERF_OBJECT_TYPE, PerfFreq);

    HDR(PERF_INSTANCE_DEFINITION);
    F(PERF_INSTANCE_DEFINITION, ByteLength);
    F(PERF_INSTANCE_DEFINITION, ParentObjectTitleIndex);
    F(PERF_INSTANCE_DEFINITION, ParentObjectInstance);
    F(PERF_INSTANCE_DEFINITION, UniqueID);
    F(PERF_INSTANCE_DEFINITION, NameOffset);
    F(PERF_INSTANCE_DEFINITION, NameLength);

    HDR(PERF_COUNTER_DEFINITION);
    F(PERF_COUNTER_DEFINITION, ByteLength);
    F(PERF_COUNTER_DEFINITION, CounterNameTitleIndex);
    F(PERF_COUNTER_DEFINITION, CounterHelpTitleIndex);
    F(PERF_COUNTER_DEFINITION, DefaultScale);
    F(PERF_COUNTER_DEFINITION, DetailLevel);
    F(PERF_COUNTER_DEFINITION, CounterType);
    F(PERF_COUNTER_DEFINITION, CounterSize);
    F(PERF_COUNTER_DEFINITION, CounterOffset);

    HDR(PERF_COUNTER_BLOCK);
    F(PERF_COUNTER_BLOCK, ByteLength);

    /* batclass.h - the battery IOCTL structs behind WindowsPowerSource. Reviewed by hand and found sound, but
     * BATTERY_MANUFACTURE_DATE in particular is easy to get wrong: its fields run Day, Month, Year, not the other
     * way round, and it is the only one of these that is not four-byte uniform. */
    HDR(BATTERY_QUERY_INFORMATION);
    F(BATTERY_QUERY_INFORMATION, BatteryTag);
    F(BATTERY_QUERY_INFORMATION, InformationLevel);
    F(BATTERY_QUERY_INFORMATION, AtRate);

    HDR(BATTERY_INFORMATION);
    F(BATTERY_INFORMATION, Capabilities);
    F(BATTERY_INFORMATION, Technology);
    F(BATTERY_INFORMATION, Chemistry);
    F(BATTERY_INFORMATION, DesignedCapacity);
    F(BATTERY_INFORMATION, FullChargedCapacity);
    F(BATTERY_INFORMATION, DefaultAlert1);
    F(BATTERY_INFORMATION, DefaultAlert2);
    F(BATTERY_INFORMATION, CriticalBias);
    F(BATTERY_INFORMATION, CycleCount);

    HDR(BATTERY_WAIT_STATUS);
    F(BATTERY_WAIT_STATUS, BatteryTag);
    F(BATTERY_WAIT_STATUS, Timeout);
    F(BATTERY_WAIT_STATUS, PowerState);
    F(BATTERY_WAIT_STATUS, LowCapacity);
    F(BATTERY_WAIT_STATUS, HighCapacity);

    HDR(BATTERY_STATUS);
    F(BATTERY_STATUS, PowerState);
    F(BATTERY_STATUS, Capacity);
    F(BATTERY_STATUS, Voltage);
    F(BATTERY_STATUS, Rate);

    HDR(BATTERY_MANUFACTURE_DATE);
    F(BATTERY_MANUFACTURE_DATE, Day);
    F(BATTERY_MANUFACTURE_DATE, Month);
    F(BATTERY_MANUFACTURE_DATE, Year);

    HDR(SP_DEVICE_INTERFACE_DATA);
    F(SP_DEVICE_INTERFACE_DATA, cbSize);
    F(SP_DEVICE_INTERFACE_DATA, InterfaceClassGuid);
    F(SP_DEVICE_INTERFACE_DATA, Flags);

    /* iphlpapi / netioapi - where the hand-written OFFSET_ constants live */
    HDR(MIB_IF_ROW2);
    F(MIB_IF_ROW2, InterfaceLuid);
    F(MIB_IF_ROW2, InterfaceIndex);
    F(MIB_IF_ROW2, Alias);
    F(MIB_IF_ROW2, Type);
    F(MIB_IF_ROW2, PhysicalMediumType);
    F(MIB_IF_ROW2, InterfaceAndOperStatusFlags);
    F(MIB_IF_ROW2, OperStatus);
    F(MIB_IF_ROW2, TransmitLinkSpeed);
    F(MIB_IF_ROW2, ReceiveLinkSpeed);
    F(MIB_IF_ROW2, InOctets);
    F(MIB_IF_ROW2, InUcastPkts);
    F(MIB_IF_ROW2, InDiscards);
    F(MIB_IF_ROW2, InErrors);
    F(MIB_IF_ROW2, OutOctets);
    F(MIB_IF_ROW2, OutUcastPkts);
    F(MIB_IF_ROW2, OutDiscards);
    F(MIB_IF_ROW2, OutErrors);

    HDR(MIB_IPFORWARD_ROW2);
    F(MIB_IPFORWARD_ROW2, InterfaceLuid);
    F(MIB_IPFORWARD_ROW2, InterfaceIndex);
    F(MIB_IPFORWARD_ROW2, DestinationPrefix);
    F(MIB_IPFORWARD_ROW2, NextHop);
    F(MIB_IPFORWARD_ROW2, SitePrefixLength);
    F(MIB_IPFORWARD_ROW2, Metric);
    F(MIB_IPFORWARD_ROW2, Protocol);
    F(MIB_IPFORWARD_ROW2, Loopback);
    F(MIB_IPFORWARD_ROW2, Age);
    F(MIB_IPFORWARD_ROW2, Origin);

    HDR(MIB_IPFORWARD_TABLE2);
    F(MIB_IPFORWARD_TABLE2, NumEntries);
    F(MIB_IPFORWARD_TABLE2, Table);

    HDR(IP_ADDRESS_PREFIX);
    F(IP_ADDRESS_PREFIX, Prefix);
    F(IP_ADDRESS_PREFIX, PrefixLength);

    HDR(MIB_TCPROW_OWNER_PID);
    F(MIB_TCPROW_OWNER_PID, dwState);
    F(MIB_TCPROW_OWNER_PID, dwLocalAddr);
    F(MIB_TCPROW_OWNER_PID, dwLocalPort);
    F(MIB_TCPROW_OWNER_PID, dwRemoteAddr);
    F(MIB_TCPROW_OWNER_PID, dwRemotePort);
    F(MIB_TCPROW_OWNER_PID, dwOwningPid);

    HDR(MIB_TCP6ROW_OWNER_PID);
    F(MIB_TCP6ROW_OWNER_PID, ucLocalAddr);
    F(MIB_TCP6ROW_OWNER_PID, dwLocalScopeId);
    F(MIB_TCP6ROW_OWNER_PID, dwLocalPort);
    F(MIB_TCP6ROW_OWNER_PID, ucRemoteAddr);
    F(MIB_TCP6ROW_OWNER_PID, dwRemoteScopeId);
    F(MIB_TCP6ROW_OWNER_PID, dwRemotePort);
    F(MIB_TCP6ROW_OWNER_PID, dwState);
    F(MIB_TCP6ROW_OWNER_PID, dwOwningPid);

    HDR(MIB_UDPROW_OWNER_PID);
    F(MIB_UDPROW_OWNER_PID, dwLocalAddr);
    F(MIB_UDPROW_OWNER_PID, dwLocalPort);
    F(MIB_UDPROW_OWNER_PID, dwOwningPid);

    HDR(MIB_UDP6ROW_OWNER_PID);
    F(MIB_UDP6ROW_OWNER_PID, ucLocalAddr);
    F(MIB_UDP6ROW_OWNER_PID, dwLocalScopeId);
    F(MIB_UDP6ROW_OWNER_PID, dwLocalPort);
    F(MIB_UDP6ROW_OWNER_PID, dwOwningPid);

    HDR(FIXED_INFO);
    F(FIXED_INFO, HostName);
    F(FIXED_INFO, DomainName);
    F(FIXED_INFO, CurrentDnsServer);
    F(FIXED_INFO, DnsServerList);
    F(FIXED_INFO, NodeType);

    HDR(IP_ADDR_STRING);
    F(IP_ADDR_STRING, Next);
    F(IP_ADDR_STRING, IpAddress);
    F(IP_ADDR_STRING, IpMask);
    F(IP_ADDR_STRING, Context);

    HDR(MIB_TCPSTATS);
    F(MIB_TCPSTATS, dwRtoAlgorithm);
    F(MIB_TCPSTATS, dwMaxConn);
    F(MIB_TCPSTATS, dwActiveOpens);
    F(MIB_TCPSTATS, dwCurrEstab);
    F(MIB_TCPSTATS, dwNumConns);

    HDR(MIB_UDPSTATS);
    F(MIB_UDPSTATS, dwInDatagrams);
    F(MIB_UDPSTATS, dwNoPorts);
    F(MIB_UDPSTATS, dwInErrors);
    F(MIB_UDPSTATS, dwOutDatagrams);
    F(MIB_UDPSTATS, dwNumAddrs);

    return 0;
}
