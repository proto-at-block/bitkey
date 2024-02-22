#include "secure_engine.h"
#include "sl_se_manager_util.h"
#include "sli_se_manager_internal.h"

#include <stdbool.h>
#include <stdint.h>

#define SLI_SE_COMMAND_ENTER_ACTIVE_MODE 0x45000000UL
#define SLI_SE_COMMAND_EXIT_ACTIVE_MODE  0x45010000UL

NO_OPTIMIZE sl_status_t se_configure_active_mode(secure_bool_t enter_foo) {
  sl_se_command_context_t cmd_ctx = {0};
  if (sl_se_init_command_context(&cmd_ctx) != SL_STATUS_OK) {
    return SL_STATUS_FAIL;
  }

  uint32_t command_id = SLI_SE_COMMAND_ENTER_ACTIVE_MODE;
  SECURE_DO_FAILOUT((enter_foo == SECURE_FALSE), { command_id = SLI_SE_COMMAND_EXIT_ACTIVE_MODE; });

  // Write command ID to context
  sli_se_command_init((&cmd_ctx), command_id);

  return sli_se_execute_and_wait(&cmd_ctx);
}
