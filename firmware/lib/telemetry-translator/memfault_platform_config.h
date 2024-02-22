#pragma once

#define MEMFAULT_USE_GNU_BUILD_ID        0
#define MEMFAULT_PLATFORM_HAS_LOG_CONFIG 0

// Not supported on Mac
#define MEMFAULT_COMPACT_LOG_ENABLE 0

// Don't log the build id in events.
#define MEMFAULT_EVENT_INCLUDE_BUILD_ID 0
