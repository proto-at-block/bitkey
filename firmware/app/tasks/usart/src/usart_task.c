#include "usart_task.h"

#include "usart_task_impl.h"

#include <assert.h>
#include <attributes.h>
#include <mcu.h>
#include <mcu_usart.h>
#include <rtos.h>
#include <stdint.h>
#include <string.h>

static usart_task_config_t _usart_task_cfgs[1] USART_TASK_DATA = {0};

static usart_task_config_t* _usart_task_allocate_config(void) {
  for (unsigned int i = 0; i < sizeof(_usart_task_cfgs) / sizeof(_usart_task_cfgs[0]); i++) {
    if (!_usart_task_cfgs[i].initialized) {
      return &_usart_task_cfgs[i];
    }
  }
  return NULL;
}

SYSCALL NO_OPTIMIZE static void _usart_task_init(mcu_usart_config_t* config) {
  RTOS_THREAD_WITH_PRIVILEGE({ mcu_usart_init(config); });
}

SYSCALL NO_OPTIMIZE static void _usart_task_write(mcu_usart_config_t* config, const uint8_t* data,
                                                  uint32_t data_len) {
  RTOS_THREAD_WITH_PRIVILEGE({ (void)mcu_usart_write(config, data, data_len); });
}

SYSCALL NO_OPTIMIZE static uint32_t _usart_task_read(mcu_usart_config_t* config, uint8_t* data,
                                                     uint32_t data_len, uint32_t timeout_ms) {
  uint32_t bytes_read = 0;
  RTOS_THREAD_WITH_PRIVILEGE(
    { bytes_read = mcu_usart_read_timeout(config, data, data_len, timeout_ms); });
  return bytes_read;
}

static void _usart_task_echo(const uint8_t* data, uint32_t data_len, void* context) {
  ASSERT(context != NULL);
  _usart_task_write((mcu_usart_config_t*)context, data, data_len);
}

static void usart_task_thread(void* args) {
  usart_task_config_t* cfg = (usart_task_config_t*)args;
  ASSERT(cfg != NULL);

  mcu_usart_config_t* usart_cfg = &cfg->usart_cfg;
  const usart_task_recv_data_cb_t recv_data_cb =
    (cfg->recv_data_cb ? cfg->recv_data_cb : _usart_task_echo);
  const usart_task_idle_cb_t idle_cb = cfg->idle_cb;
  void* context = (cfg->recv_data_cb ? cfg->context : usart_cfg);

  uint8_t* recv_buf = cfg->recv_buf;
  const uint32_t recv_buf_len = sizeof(cfg->recv_buf);

  _usart_task_init(usart_cfg);

  while (1) {
    const uint32_t bytes_read =
      _usart_task_read(usart_cfg, recv_buf, recv_buf_len, USART_TASK_RD_TIMEOUT_MS);
    if (bytes_read > 0) {
      recv_data_cb(recv_buf, bytes_read, context);
    }
    if (idle_cb != NULL) {
      idle_cb(context);
    }
  }
}

void usart_task_create(mcu_usart_config_t* mcu_usart_cfg, usart_task_recv_data_cb_t recv_data_cb,
                       usart_task_idle_cb_t idle_cb, void* context) {
  ASSERT(mcu_usart_cfg != NULL);

  usart_task_config_t* usart_task_cfg = _usart_task_allocate_config();
  ASSERT(usart_task_cfg != NULL);
  ASSERT(!usart_task_cfg->initialized);

  memcpy(&usart_task_cfg->usart_cfg, mcu_usart_cfg, sizeof(*mcu_usart_cfg));
  usart_task_cfg->recv_data_cb = recv_data_cb;
  usart_task_cfg->idle_cb = idle_cb;
  usart_task_cfg->context = context;
  usart_task_cfg->initialized = true;

  rtos_thread_t* thread = rtos_thread_create(usart_task_thread, (void*)usart_task_cfg,
                                             USART_TASK_PRIORITY, USART_TASK_STACK_SIZE);
  ASSERT(thread != NULL);
}
