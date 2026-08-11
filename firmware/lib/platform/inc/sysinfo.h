/**
 * @file sysinfo.h
 * @brief POSIX passthrough for sysinfo.h.
 *
 * The canonical hal/sysinfo header is hardware-independent declarations.
 * POSIX implementations (fixed serial/version placeholders) live in
 * app/core-sim/src/posix/stubs.c, with sysinfo_chip_id_read in
 * app/core-sim/src/posix/key_manager_task_port.c.
 */

#pragma once

#include "../../../hal/sysinfo/inc/sysinfo.h"
