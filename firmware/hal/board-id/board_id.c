#include "board_id.h"

#include "bitops.h"

extern board_id_config_t board_id_config;

void board_id_init(void) {
  mcu_gpio_configure(&board_id_config.board_id0, false);
  mcu_gpio_configure(&board_id_config.board_id1, false);
}

void board_id_read(uint8_t* board_id_out) {
  uint8_t id0 = (uint8_t)mcu_gpio_read(&board_id_config.board_id0);
  uint8_t id1 = (uint8_t)mcu_gpio_read(&board_id_config.board_id1);
  *board_id_out = 0;
  BIT_CHANGE(*board_id_out, BOARD_ID0_POS, id0);
  BIT_CHANGE(*board_id_out, BOARD_ID1_POS, id1);
}
