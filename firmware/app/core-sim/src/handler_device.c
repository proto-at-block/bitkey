/**
 * @file handler_device.c
 * @brief Authentication handlers for core-sim
 */

#include "handler_device.h"

#include "handlers.h"

// Centralized auth state (device_state.c)
extern void emu_set_authenticated(bool authenticated);
extern bool emu_get_authenticated(void);

void core_sim_set_authenticated(bool authenticated) {
  emu_set_authenticated(authenticated);
  LOG("Authentication state set to: %s", authenticated ? "AUTHENTICATED" : "UNAUTHENTICATED");
}

bool stdio_query_authentication_handler(uint8_t* rsp, uint32_t* rsp_size) {
  bool is_authenticated = emu_get_authenticated();
  LOG("query_authentication: state=%s", is_authenticated ? "AUTHENTICATED" : "UNAUTHENTICATED");

  fwpb_wallet_rsp response = fwpb_wallet_rsp_init_default;
  response.which_msg = fwpb_wallet_rsp_query_authentication_rsp_tag;

  response.msg.query_authentication_rsp.rsp_status =
    is_authenticated
      ? fwpb_query_authentication_rsp_query_authentication_rsp_status_AUTHENTICATED
      : fwpb_query_authentication_rsp_query_authentication_rsp_status_UNAUTHENTICATED;

  if (!encode_wallet_response(rsp, rsp_size, *rsp_size, &response)) {
    return false;
  }
  LOG("query_authentication: response built, %u bytes", *rsp_size);
  return true;
}
