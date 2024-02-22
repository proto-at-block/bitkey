#include "app.h"

// These are here instead of in sysinfo.c because propagating cflags to dependencies
// in Meson is hard (impossible?).
USED char _sysinfo_software_type[SYSINFO_SOFTWARE_TYPE_MAX_LENGTH] = SYSINFO_SOFTWARE_TYPE;
USED char _sysinfo_hardware_revision[SYSINFO_HARDWARE_REVISION_MAX_LENGTH] =
  SYSINFO_HARDWARE_REVISION;
USED char _sysinfo_version_string[SYSINFO_VERSION_MAX_LENGTH] = SYSINFO_VERSION_STRING;

static void detect_glitch(void);

NO_OPTIMIZE int main(void) {
  CHIP_Init();
  assert_init(&memfault_fault_handling_assert);
  mcu_init();
  board_id_init();
  mcu_smu_init();
  mpu_regions_init();

  mcu_i2c_init();  // Must come after MPU init but before power init

  power_init();
  led_init();
  serial_init();
  sysevent_init();

#ifndef CONFIG_PROD
  shell_task_create();
#endif

  sl_se_init();
  secutils_init((secutils_api_t){
    .detect_glitch = &detect_glitch,
    .secure_random = &crypto_rand_short,
    .cpu_freq = &clock_get_freq,
  });

  SECURE_DO_ONCE({ mcu_smu_init(); });
  SECURE_DO_ONCE({ se_configure_active_mode(SECURE_TRUE); });
  SECURE_DO_ONCE({ tamper_init(); });
  SECURE_DO_ONCE({ canary_init(); });

  bitlog_init((bitlog_api_t){
    .timestamp_cb = &rtos_thread_systime,
  });

  telemetry_init((telemetry_api_t){
    .get_chunk = &memfault_packetizer_get_chunk,
    .set_drain_all = &memfault_port_drain_all,
    .set_drain_only_events = &memfault_port_drain_only_events,
  });

  secure_channel_init();

  fs_mount();

  memfault_platform_boot();

  nfc_task_create();
  sysinfo_task_create(PLATFORM_HW_REV);
  led_task_create();

#ifdef MFGTEST
  power_retain_charged_indicator();
  led_mfgtest_task_create();
  mfgtest_task_create();
  fwup_task_create((fwup_task_options_t){
    .bl_upgrade = true,
  });
  auth_task_create(true);
#else
  key_manager_task_create();
  auth_task_create(false);
  fwup_task_create((fwup_task_options_t){
    .bl_upgrade = false,
  });
#endif

  mcu_wdog_init();

  rtos_thread_start_scheduler();
}

static void detect_glitch(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}
