#include "app.h"
#include "bio.h"
#include "exti.h"

// These are here instead of in sysinfo.c because propagating cflags to dependencies
// in Meson is hard (impossible?).
USED char _sysinfo_software_type[SYSINFO_SOFTWARE_TYPE_MAX_LENGTH] = SYSINFO_SOFTWARE_TYPE;
USED char _sysinfo_hardware_revision[SYSINFO_HARDWARE_REVISION_MAX_LENGTH] =
  SYSINFO_HARDWARE_REVISION;
USED char _sysinfo_version_string[SYSINFO_VERSION_MAX_LENGTH] = SYSINFO_VERSION_STRING;

extern mcu_usart_config_t comms_usart_config;

extern coproc_cfg_t uxc_coproc_cfg;

static void detect_glitch(void);

NO_OPTIMIZE int main(void) {
  CHIP_Init();
  assert_init(&memfault_fault_handling_assert);
  mcu_init();
  board_id_init();
  mcu_smu_init();
  mpu_regions_init();

  mcu_i2c_init();  // Must come after MPU init but before power init
  exti_init();

  power_init();

  serial_init();
  sysevent_init();

#ifdef CONFIG_PROD
  grant_protocol_init(true);
#else
  grant_protocol_init(false);
#endif

#ifndef CONFIG_PROD
  shell_task_create();
#endif

  thermal_task_create();

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

  secure_nfc_channel_init();
  secure_uart_channel_init(SECURE_UART_CHANNEL_CORE);

  fs_mount();

  coproc_init(&uxc_coproc_cfg);

  memfault_platform_boot();

  nfc_task_create();
  sysinfo_task_create(PLATFORM_HW_REV);

  // Initialize biometrics HAL early to reserve high-priority DMA channels (0 and 1).
  // This must happen before comms USART to ensure FPC SPI gets the highest
  // priority channels for reliable full-duplex operation.
  bio_hal_init();

  // Initialize UXC comms.
  uc_init((uc_send_callback_t)mcu_usart_write, (void*)&comms_usart_config);
  usart_task_create(&comms_usart_config, uc_handle_data, uc_idle, &comms_usart_config);

  ui_task_create();  // Start after UC (dependancy)

  captouch_task_create();

#ifdef MFGTEST
  power_retain_charged_indicator();
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

  // This must be done in privileged mode because EXTI configuration requires NVIC access.
  // While we could add an MPU region for the NVIC peripheral, this would expose the entire
  // System Control Space (SCS) to unprivileged tasks, creating security vulnerabilities.
  button_init();

  mcu_wdog_init();

  rtos_thread_start_scheduler();
}

static void detect_glitch(void) {
  mcu_reset_with_reason(MCU_RESET_FAULT);
}
