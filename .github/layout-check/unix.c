/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 *
 * Pulls in the headers oshi-core-ffm maps on this platform. Nothing needs to be declared here: clang's
 * -fdump-record-layouts prints every record in the translation unit, so including the headers is the whole job.
 */
#if defined(__APPLE__)
#include <sys/proc_info.h>
#include <sys/sysctl.h>
#include <net/if.h>
#include <net/if_dl.h>
#include <netinet/in.h>
#include <sys/mount.h>
#include <mach/mach_host.h>
#elif defined(__linux__)
#include <sys/sysinfo.h>
#include <sys/statvfs.h>
#include <sys/utsname.h>
#include <net/if.h>
#include <netinet/in.h>
#include <utmpx.h>
#endif
int main(void) { return 0; }
