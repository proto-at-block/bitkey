#include "app.h"
#include "assert.h"
#include "bitlog.h"
#include "canary.h"
#include "clock.h"
#include "display_task.h"
#include "exti.h"
#include "filesystem.h"
#include "fwup_task.h"
#include "key_manager_task.h"
#include "langpack.h"
#include "mcu.h"
#include "mcu_gpio.h"
#include "mcu_usart.h"
#include "mcu_wdog.h"
#include "memfault.h"
#include "mfgtest_task.h"
#include "mpu_regions.h"
#include "rtos.h"
#include "secure_channel.h"
#include "secure_rng.h"
#include "secutils.h"
#include "serial.h"
#ifndef CONFIG_PROD
#include "shell_task.h"
#endif
#include "sysevent.h"
#include "sysinfo_task.h"
#include "telemetry_storage.h"
#include "touch_task.h"
#include "uc.h"
#include "uc_route.h"
#include "usart_task.h"

#include <stdbool.h>
#include <string.h>

// These are here instead of in sysinfo.c because propagating cflags to dependencies
// in Meson is hard (impossible?).
USED char _sysinfo_software_type[SYSINFO_SOFTWARE_TYPE_MAX_LENGTH] = SYSINFO_SOFTWARE_TYPE;
USED char _sysinfo_hardware_revision[SYSINFO_HARDWARE_REVISION_MAX_LENGTH] =
  SYSINFO_HARDWARE_REVISION;
USED char _sysinfo_version_string[SYSINFO_VERSION_MAX_LENGTH] = SYSINFO_VERSION_STRING;

extern const mcu_gpio_config_t boot_status_config;
extern mcu_usart_config_t comms_usart_config;

static void app_detect_glitch(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}

NO_OPTIMIZE int main(void) {
  assert_init(&memfault_fault_handling_assert);
  mcu_init();
  mpu_regions_init();
  exti_init();

  mcu_gpio_configure(&boot_status_config, true /* booted */);

  serial_init();
  uc_init((uc_send_callback_t)mcu_usart_write, (void*)&comms_usart_config);

  // Initialize sysevent system
  sysevent_init();

#ifndef CONFIG_PROD
  shell_task_create();
#endif

  crypto_random_init();
  secutils_init((secutils_api_t){
    .detect_glitch = &app_detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });
  SECURE_DO_ONCE({ canary_init(); });

  // Initialize default language pack (English).
  langpack_load_default();

  // Create display task
  display_task_create();

  // Create touch task
  touch_task_create();

  // Create serial task
  usart_task_create(&comms_usart_config, uc_handle_data, uc_idle, &comms_usart_config);

  bitlog_init((bitlog_api_t){
    .timestamp_cb = &rtos_thread_systime,
  });

  telemetry_init((telemetry_api_t){
    .get_chunk = &memfault_packetizer_get_chunk,
    .set_drain_all = &memfault_port_drain_all,
    .set_drain_only_events = &memfault_port_drain_only_events,
  });

  secure_uart_channel_init(SECURE_UART_CHANNEL_UXC);

  // Create the info task
  sysinfo_task_create(PLATFORM_HW_REV);

  key_manager_task_create();

#ifdef MFGTEST
  // Create manufacturing test task
  mfgtest_task_create();

  // Create the FWUP task.
  fwup_task_create((fwup_task_options_t){.bl_upgrade = true});
#else
  // Create the FWUP task.
  fwup_task_create((fwup_task_options_t){.bl_upgrade = false});
#endif

  // Mount the filesystem.
  fs_mount();

  memfault_platform_boot();

  // Signal that power is ready (for tasks waiting on this event)
  sysevent_set(SYSEVENT_POWER_READY);

  // Start the watchdog (must be done last before RTOS scheduling)
  mcu_wdog_init();

  // Start RTOS scheduler
  rtos_thread_start_scheduler();
}
