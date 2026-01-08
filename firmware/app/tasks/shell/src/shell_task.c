#include "shell_task.h"

#include "assert.h"
#include "attributes.h"
#include "mcu_usart.h"
#include "rtos.h"
#include "serial.h"
#include "shell.h"
#include "shell_cmd.h"

#define SHELL_BUFFER_LEN (64)

static rtos_thread_t* thread = {0};
static rtos_mutex_t cmd_list_mutex = {0};

static void shell_thread(void* args);
static void shell_mutex_create(void);
static bool shell_mutex_lock(void);
static bool shell_mutex_unlock(void);

extern serial_config_t serial_config;

static const shell_config_t shell_cfg = {
  .vprintf_func = vprintf,
  .cmd_list_mutex = (void*)&cmd_list_mutex,
  .mutex_create = shell_mutex_create,
  .mutex_lock = shell_mutex_lock,
  .mutex_unlock = shell_mutex_unlock,
};

void shell_task_create(void) {
  // NOTE: This is large because of crypto_cmd -- schnorr signing requires a deep stack.
  thread = rtos_thread_create(shell_thread, NULL, RTOS_THREAD_PRIORITY_NORMAL, 8192);
  ASSERT(thread != NULL);
}

static void shell_thread(void* UNUSED(args)) {
  uint8_t buffer[SHELL_BUFFER_LEN] = {0};

  shell_init(&shell_cfg);

  /* Commands must be registered _after_ shell_init() is called */
  SHELL_CMD_REGISTER_ALL();

  for (;;) {
    const uint32_t n_read =
      mcu_usart_read_timeout(&serial_config.usart, buffer, SHELL_BUFFER_LEN, 10);

    if (n_read > 0) {
      shell_process(buffer, n_read);
    }
  }
}

static void shell_mutex_create(void) {
  rtos_mutex_create(&cmd_list_mutex);
}

static bool shell_mutex_lock(void) {
  return rtos_mutex_lock(&cmd_list_mutex);
}

static bool shell_mutex_unlock(void) {
  return rtos_mutex_unlock(&cmd_list_mutex);
}
