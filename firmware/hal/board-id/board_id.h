#include "mcu_gpio.h"

typedef struct {
  mcu_gpio_config_t board_id0;
  mcu_gpio_config_t board_id1;
} board_id_config_t;

#define BOARD_ID0_POS (0)
#define BOARD_ID1_POS (1)

void board_id_init(void);
void board_id_read(uint8_t* board_id_out);
