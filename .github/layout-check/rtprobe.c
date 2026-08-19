/*
 * Copyright 2026 The OSHI Project Contributors
 * SPDX-License-Identifier: MIT
 *
 * Reports what a NET_RT_DUMP consumer has to know on this platform: the rt_msghdr layout, where the sockaddr array
 * starts, and the padding unit it steps by. The padding unit is measured rather than read from a macro -- walking a
 * message with the wrong unit does not land on its end, so the unit that fits every message exactly is the right one.
 */
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/sysctl.h>
#include <net/route.h>
#include <netinet/in.h>

#define O(f) printf("  %-14s off=%3zu size=%2zu\n", #f, offsetof(struct rt_msghdr, f), \
                    sizeof(((struct rt_msghdr *)0)->f))

static size_t roundup_to(size_t len, size_t unit) {
    return len ? (1 + ((len - 1) | (unit - 1))) : unit;
}

/* Header bytes before the sockaddr array. OpenBSD states it per message; elsewhere it is the struct size. */
static size_t hdrlen_of(struct rt_msghdr *rtm) {
#if defined(__OpenBSD__)
    return rtm->rtm_hdrlen;
#else
    (void) rtm;
    return sizeof(struct rt_msghdr);
#endif
}

/* Does the sockaddr array end exactly where the message does, stepping by this unit? */
static int walk_fits(struct rt_msghdr *rtm, size_t unit) {
    char *sa = (char *) rtm + hdrlen_of(rtm);
    char *end = (char *) rtm + rtm->rtm_msglen;
    for (int i = 0; i < RTAX_MAX; i++) {
        if (!(rtm->rtm_addrs & (1 << i))) {
            continue;
        }
        if (sa >= end) {
            return 0;
        }
        unsigned char slen = ((struct sockaddr *) sa)->sa_len;
        sa += roundup_to(slen, unit);
        if (sa > end) {
            return 0;
        }
    }
    return sa == end;
}

int main(void) {
    printf("== %s\n",
#if defined(__APPLE__)
           "macOS"
#elif defined(__DragonFly__)
           "DragonFly BSD"
#elif defined(__FreeBSD__)
           "FreeBSD"
#elif defined(__NetBSD__)
           "NetBSD"
#elif defined(__OpenBSD__)
           "OpenBSD"
#else
           "unknown"
#endif
    );
    printf("RTM_VERSION=%d  sizeof(rt_msghdr)=%zu  sizeof(long)=%zu\n", RTM_VERSION, sizeof(struct rt_msghdr),
           sizeof(long));
    O(rtm_msglen);
    O(rtm_version);
    O(rtm_type);
    O(rtm_index);
    O(rtm_flags);
    O(rtm_addrs);
    O(rtm_pid);
    O(rtm_seq);
    O(rtm_errno);
#if defined(__OpenBSD__)
    O(rtm_hdrlen);
    O(rtm_priority);
    O(rtm_tableid);
#endif
    printf("RTAX_DST=%d GATEWAY=%d NETMASK=%d IFP=%d IFA=%d RTAX_MAX=%d\n", RTAX_DST, RTAX_GATEWAY, RTAX_NETMASK,
           RTAX_IFP, RTAX_IFA, RTAX_MAX);
    printf("RTF_UP=0x%x RTF_GATEWAY=0x%x RTF_HOST=0x%x\n", RTF_UP, RTF_GATEWAY, RTF_HOST);

    int mib[6] = { CTL_NET, PF_ROUTE, 0, 0, NET_RT_DUMP, 0 };
    size_t len = 0;
    if (sysctl(mib, 6, NULL, &len, NULL, 0) < 0) {
        perror("sysctl size");
        return 1;
    }
    char *buf = malloc(len);
    if (!buf || sysctl(mib, 6, buf, &len, NULL, 0) < 0) {
        perror("sysctl data");
        return 1;
    }

    int msgs = 0, fit4 = 0, fit8 = 0, hdrlen_differs = 0;
    for (char *p = buf; p < buf + len;) {
        struct rt_msghdr *rtm = (struct rt_msghdr *) p;
        if (rtm->rtm_msglen == 0) {
            break;
        }
        msgs++;
        fit4 += walk_fits(rtm, 4);
        fit8 += walk_fits(rtm, 8);
        if (hdrlen_of(rtm) != sizeof(struct rt_msghdr)) {
            hdrlen_differs++;
        }
        p += rtm->rtm_msglen;
    }
    printf("NET_RT_DUMP: %zu bytes, %d messages\n", len, msgs);
    printf("sockaddr padding unit: 4 fits %d/%d, 8 fits %d/%d\n", fit4, msgs, fit8, msgs);
    printf("messages whose header length differs from sizeof(rt_msghdr): %d\n", hdrlen_differs);
    return 0;
}
