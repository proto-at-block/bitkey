#include "auth_sim.h"

#include "device_state.h"
#include "ipc.h"

#include <stdio.h>
#include <string.h>

void core_sim_start_fingerprint_enrollment(uint8_t index, const char* label) {
  static auth_start_fingerprint_enrollment_internal_t cmd;
  memset(&cmd, 0, sizeof(cmd));
  cmd.index = index;
  (void)snprintf(cmd.label, sizeof(cmd.label), "%s", label ? label : "Fingerprint");

  ipc_send(auth_port, &cmd, sizeof(cmd), IPC_AUTH_START_FINGERPRINT_ENROLLMENT_INTERNAL);
  emu_enrollment_start(cmd.index, cmd.label);
}
