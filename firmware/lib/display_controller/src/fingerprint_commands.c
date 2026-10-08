#include "display_controller_internal.h"

#ifdef EMBEDDED_BUILD
#include "auth.h"
#include "ipc.h"

#include <attributes.h>
#endif

void display_controller_query_fingerprint_status(void) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_BSS auth_get_enrolled_fingerprints_internal_t cmd;
  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_GET_ENROLLED_FINGERPRINTS_INTERNAL);
#endif
}

bool display_controller_delete_fingerprint(uint8_t slot_index) {
#ifdef EMBEDDED_BUILD
  static SHARED_TASK_BSS auth_delete_fingerprint_internal_t cmd;
  cmd.index = slot_index;
  return ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_DELETE_FINGERPRINT_INTERNAL);
#else
  (void)slot_index;
  return false;
#endif
}
