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
