#pragma once

// Include this file from other libraries.

#include "memfault/components.h"
#include "memfault/core/data_packetizer.h"
#include "memfault/core/log.h"
#include "memfault/core/platform/core.h"
#include "memfault/core/platform/debug_log.h"
#include "memfault/panics/assert.h"

void memfault_port_drain_only_events(void);
void memfault_port_drain_all(void);

#ifdef EMBEDDED_BUILD
// Prevent a coredump from being persisted while secret material may be live. Calls may be nested or
// overlap across tasks.
void memfault_port_coredump_sensitive_operation_begin(void);
void memfault_port_coredump_sensitive_operation_end(void);
#else
static inline void memfault_port_coredump_sensitive_operation_begin(void) {}
static inline void memfault_port_coredump_sensitive_operation_end(void) {}
#endif
