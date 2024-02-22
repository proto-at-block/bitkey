#include "assert.h"
#include "attributes.h"
#include "t4t.h"
#include "t4t_impl.h"

#include <string.h>

bool t4t_handle_command(uint8_t* UNUSED(cmd), uint32_t UNUSED(cmd_len), uint8_t* rsp,
                        uint32_t* rsp_len) {
  ASSERT(*rsp_len >= SW_SIZE);

  *rsp_len = SW_SIZE;
  RSP_OK(rsp, 0);
  return true;
}

bool t4t_is_valid(uint8_t* cmd, uint32_t cmd_len) {
  return (cmd_len >= (P2 + 1)) && cmd[CLA] == T4T_CLA;
}
