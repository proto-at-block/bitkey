/**
 * @file board_id.h
 *
 * @brief Board Identifier
 *
 * @details The board identifier is a pre-programmed set of bits in hardware
 * that indicate the hardware revision of the device.
 *
 * @{
 */

#pragma once

#include "mcu_gpio.h"

#include <stdbool.h>

/**
 * @brief W3 Board ID values.
 */
typedef enum {
  W3_BOARD_ID_BUTTONS = 0x00, /**< PDVT-M3 main board
                                   EVT-equivalent config with buttons,
                                   normal display orientation.
                                   */

  W3_BOARD_ID_TOUCH_WAKE = 0x01, /**< PDVT-mini, M1, R1
                                      PDVT touch-to-wake main config (POR),
                                      display rotated 180°.
                                      */

  W3_BOARD_ID_TOUCH_NOWAKE = 0x02, /**< PDVT-M2 main board
                                        non-touch-to-wake variant,
                                        display rotated 180°.
                                        Expected to be DEPRECATED after pre-DVT build;
                                        */
} w3_board_id_t;

/**
 * @brief Position of the 0th bit of the board ID.
 */
#define BOARD_ID0_POS (0)

/**
 * @brief Position of the 1st bit of the board ID.
 */
#define BOARD_ID1_POS (1)

/**
 * @brief Board ID GPIO definitions.
 */
typedef struct {
  /**
   * @brief GPIO used to read the 0th bit of the board ID.
   */
  mcu_gpio_config_t board_id0;

  /**
   * @brief GPIO used to read the 1st bit of the board ID.
   */
  mcu_gpio_config_t board_id1;
} board_id_config_t;

/**
 * @brief Initializes the GPIOs used to read the hardware board ID.
 */
void board_id_init(void);

/**
 * @brief Retrieves the board ID of the device.
 *
 * @param[out] board_id_out  Output pointer to store the read board ID.
 *
 * @return `true` if board ID was successfully read, otherwise `false`.
 */
bool board_id_read(uint8_t* board_id_out);

/** @} */
