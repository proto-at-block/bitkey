#include "ipc.h"
#include "key_manager_task_impl.h"
#include "log.h"

void key_manager_task_handle_uxc_session_response(ipc_ref_t* message) {
  (void)message;
  LOGE("Unexpected call to UXC session response handler.");
}

void key_manager_task_handle_uxc_boot(void) {
  LOGE("Unexpected call to UXC boot handler.");
}

void key_manager_task_register_listeners(void) {}
