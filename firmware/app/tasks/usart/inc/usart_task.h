#include <mcu_usart.h>
#include <stddef.h>
#include <stdint.h>

#pragma once

/**
 * @brief Callback invoked by the USART task on reception of data.
 *
 * @param data      Pointer to the received data over USART.
 * @param data_len  Length of the received data, @p data, in bytes.
 * @param context   User-supplied context pointer.
 */
typedef void (*usart_task_recv_data_cb_t)(const uint8_t* data, uint32_t data_len, void* context);

/**
 * @brief Callback invoked when the USART task is idle.
 *
 * @param context   User-supplied context pointer.
 */
typedef void (*usart_task_idle_cb_t)(void* context);

/**
 * @brief Creates a USART task for the specified USART instance.
 *
 * @param mcu_usart_cfg  Pointer to the USART configuration to use.
 * @param recv_data_cb   Callback to invoke when data is received.
 * @param idle_cb        Callback to invoke when USART task is idle.
 * @param context        User-supplied context pointer to pass to the receive callback.
 */
void usart_task_create(mcu_usart_config_t* mcu_usart_cfg, usart_task_recv_data_cb_t recv_data_cb,
                       usart_task_idle_cb_t idle_cb, void* context);
