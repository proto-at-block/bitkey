/**
 * @file telemetry_storage.h
 * @brief POSIX passthrough for telemetry_storage.h.
 *
 * The canonical header is hardware-independent. The simulator has no
 * telemetry flash region; no-op implementations live in
 * app/core-sim/src/posix/task_stubs.c.
 */

#pragma once

#include "../../telemetry-storage/inc/telemetry_storage.h"
