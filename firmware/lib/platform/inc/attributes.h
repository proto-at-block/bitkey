/**
 * @file attributes.h
 * @brief POSIX passthrough for attributes.h.
 *
 * The canonical lib/helpers/attributes.h already guards every
 * embedded-only attribute behind EMBEDDED_BUILD. The one POSIX hazard is
 * CLEANUP() (clang hard-errors on goto jumping past __cleanup__ variables);
 * core-sim neutralizes that by force-including
 * lib/rtos/inc/posix/posix_cleanup_workaround.h, which redefines
 * __cleanup__ to __unused__ before any code uses it.
 */

#pragma once

#include "../../helpers/attributes.h"
