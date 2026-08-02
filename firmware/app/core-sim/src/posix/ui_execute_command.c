/**
 * @file ui_execute_command.c
 * @brief UI command forwarding for core-sim
 *
 * This file implements ui_execute_command(), which is called by the real
 * display_controller library when running in non-embedded mode. The function
 * forwards display commands to ui-simulate via the UXC socket transport.
 */

#include "display.pb.h"
#include "stdio_defs.h"
#include "uxc.pb.h"
#include "uxc_socket_server.h"
#include "uxc_transport.h"

#include <stdbool.h>
#include <string.h>

fwpb_display_result ui_execute_command(const fwpb_display_command* cmd) {
  if (!cmd) {
    return fwpb_display_result_DISPLAY_RESULT_INVALID_PARAM;
  }

  if (uxc_socket_is_connected()) {
    fwpb_uxc_msg_host host_msg = fwpb_uxc_msg_host_init_default;
    host_msg.which_msg = fwpb_uxc_msg_host_display_cmd_tag;
    memcpy(&host_msg.msg.display_cmd, cmd, sizeof(fwpb_display_command));

    if (uxc_transport_send_host_msg(&host_msg)) {
      LOG("Forwarded display command to ui-simulate (which_command=%u)", cmd->which_command);
    } else {
      LOG("Failed to forward display command to ui-simulate");
    }
  }

  return fwpb_display_result_DISPLAY_RESULT_SUCCESS;
}
