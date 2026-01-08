#include "usart_task.h"

#include <mcu_usart.h>
#include <rtos.h>
#include <stddef.h>
#include <stdint.h>

#pragma once

#define USART_TASK_RD_TIMEOUT_MS (10u)
#define USART_TASK_PRIORITY      (RTOS_THREAD_PRIORITY_NORMAL)
#define USART_TASK_STACK_SIZE    (2048u)

/**
 * @brief Configuration for the USART task. Specifies which USART the task
 * should communicate with and how to handle data.
 */
typedef struct {
  /**
   * @brief Configuration for instantiating the USART used by the instance.
   */
  mcu_usart_config_t usart_cfg;

  /**
   * @brief Callback invoked when the USART task receives data. The callback
   * is invoked within the context of the USART task.
   */
  usart_task_recv_data_cb_t recv_data_cb;

  /**
   * @brief Callback invoked when the USART task is idle (no data available).
   */
  usart_task_idle_cb_t idle_cb;

  /**
   * @brief Receive buffer for data read over USART.
   */
  uint8_t recv_buf[MCU_USART_RX_BUFFER_LEN];

  /**
   * @brief User-supplied context pointer passed to bound callbacks.
   */
  void* context;

  /**
   * @brief `true` if instance has been initialized, otherwise `false`.
   */
  bool initialized;
} usart_task_config_t;
