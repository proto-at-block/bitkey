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

#if defined(LOG_TOKENIZED) && !defined(DISABLE_PRINTF)
#include "log_uart.h"
#endif

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

SYSCALL NO_OPTIMIZE uint32_t _app_uc_send_callback(void* context, const uint8_t* data,
                                                   size_t data_len) {
  uint32_t bytes_sent = 0;
  RTOS_THREAD_WITH_PRIVILEGE(
    { bytes_sent = mcu_usart_write((mcu_usart_config_t*)context, data, data_len); });
  return bytes_sent;
}

SYSCALL NO_OPTIMIZE secure_bool_t _app_secure_uart_channel_encrypt(uint8_t const* plaintext,
                                                                   uint8_t* ciphertext,
                                                                   uint32_t len, uint8_t const* aad,
                                                                   uint32_t aad_len, uint8_t* nonce,
                                                                   uint8_t* mac) {
  secure_bool_t status = SECURE_FALSE;
  RTOS_THREAD_WITH_PRIVILEGE({
    status = secure_uart_channel_encrypt(plaintext, ciphertext, len, aad, aad_len, nonce, mac);
  });
  return status;
}

SYSCALL NO_OPTIMIZE secure_bool_t _app_secure_uart_channel_decrypt(uint8_t const* ciphertext,
                                                                   uint8_t* plaintext, uint32_t len,
                                                                   uint8_t const* aad,
                                                                   uint32_t aad_len, uint8_t* nonce,
                                                                   uint8_t* mac) {
  secure_bool_t status = SECURE_FALSE;
  RTOS_THREAD_WITH_PRIVILEGE({
    status = secure_uart_channel_decrypt(ciphertext, plaintext, len, aad, aad_len, nonce, mac);
  });
  return status;
}

SYSCALL NO_OPTIMIZE bool _app_secure_uart_channel_check_recv_seq_number(uint32_t new_seq) {
  bool valid = false;
  RTOS_THREAD_WITH_PRIVILEGE({ valid = secure_uart_channel_check_recv_seq_number(new_seq); });
  return valid;
}

SYSCALL NO_OPTIMIZE uint32_t _app_secure_uart_channel_get_send_seq_number(void) {
  uint32_t next_seq = 0;
  RTOS_THREAD_WITH_PRIVILEGE({ next_seq = secure_uart_channel_get_send_seq_number(); });
  return next_seq;
}

SYSCALL NO_OPTIMIZE bool _app_secure_uart_channel_confirmed(void) {
  bool confirmed = false;
  RTOS_THREAD_WITH_PRIVILEGE({ confirmed = secure_uart_channel_confirmed(); });
  return confirmed;
}

NO_OPTIMIZE int main(void) {
  assert_init(&memfault_fault_handling_assert);
  mcu_init();
  mpu_regions_init();
  exti_init();

  mcu_gpio_configure(&boot_status_config, true /* booted */);

  serial_init();
#if defined(LOG_TOKENIZED) && !defined(DISABLE_PRINTF)
  // Banner the GNU/Memfault build ID so the host log decoder can verify the
  // connected firmware matches the user-supplied ELF.
  log_uart_emit_build_id();
#endif

  // Initialize sysevent system
  sysevent_init();

  // Initialize UXC comms.
  // No message encryption on MFG test devices
#ifdef MFGTEST
  uc_init(_app_uc_send_callback, NULL, (void*)&comms_usart_config);
  sysevent_set(SYSEVENT_UXC_SECURE_COMMS_ESTABLISHED);
#else
  uc_crypto_api_t crypto_api = {
    .gcm_encrypt = &_app_secure_uart_channel_encrypt,
    .gcm_decrypt = &_app_secure_uart_channel_decrypt,
    .check_recv_seq = &_app_secure_uart_channel_check_recv_seq_number,
    .get_send_seq = &_app_secure_uart_channel_get_send_seq_number,
    .has_session = &_app_secure_uart_channel_confirmed,
  };
  uc_init(_app_uc_send_callback, &crypto_api, (void*)&comms_usart_config);
#endif

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
  fwup_task_create((fwup_task_options_t){
    .bl_upgrade = true,
    .confirmation = SECURE_FALSE,
  });
#else
  // Create the FWUP task.
  fwup_task_create((fwup_task_options_t){
    .bl_upgrade = false,
    .confirmation = SECURE_TRUE,
  });
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
