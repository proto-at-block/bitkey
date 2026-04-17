/**
 * @file sysinfo_task.h
 *
 * @brief System Information Task
 *
 * @{
 */

#pragma once

#include "platform.h"
#include "wallet.pb.h"

#include <stdbool.h>

/**
 * @brief Creates the system information task.
 */
void sysinfo_task_create(const platform_hwrev_t hwrev);

/**
 * @brief Updates the cached UXC version reported in device_info_rsp.
 *
 * Called after UXC firmware verification succeeds during the atomic FWUP
 * protocol.  The new version is not yet running on UXC — the signature has
 * been verified but not committed to flash.  This allows the app to see the
 * target version in getDeviceInfo() and proceed with Core FWUP without
 * requiring UXC to reset first.
 *
 * The cached version self-corrects on any reset: UXC sends a fresh
 * uxc_boot_status_msg with its actual running version, which overwrites
 * this value.
 *
 * @param version  The verified target version that UXC will run after commit.
 */
void sysinfo_task_port_set_uxc_pending_version(const fwpb_semver* version);

/** @} */
