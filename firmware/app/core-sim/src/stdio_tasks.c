/**
 * @file stdio_tasks.c
 * @brief Start real firmware tasks in core-sim
 *
 * Initializes and starts real firmware tasks (sysinfo, fwup, key_manager, auth)
 * using the POSIX RTOS implementation with IPC command handling.
 */

#include "stdio_tasks.h"

#include "auth_task.h"
#include "fwup_task.h"
#include "key_manager_task.h"
#include "platform.h"
#include "sysevent.h"
#include "sysinfo_task.h"
#include "ui_task.h"

#include <stdbool.h>

void stdio_tasks_start(void) {
  sysevent_init();

  sysevent_set(SYSEVENT_FILESYSTEM_READY);
  sysevent_set(SYSEVENT_POWER_READY);
  sysevent_set(SYSEVENT_FEATURE_FLAGS_READY);
  sysevent_set(SYSEVENT_SLEEP_TIMER_READY);

  sysinfo_task_create(HWREV_EVT);
  fwup_task_options_t fwup_options = {.bl_upgrade = false};
  fwup_task_create(fwup_options);
  key_manager_task_create();

  auth_task_create(false);

  ui_task_create();
}

void stdio_tasks_stop(void) {}
