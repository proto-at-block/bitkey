#include "fwup_staged_sig.h"

#include "filesystem.h"
#include "fwup.h"
#include "log.h"
#include "rtos_mpu.h"

bool fwup_staged_sig_write(const fwup_staged_sig_t* staged) {
  bool result = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    result = fs_util_write_global((char*)FWUP_STAGED_SIG_PATH, (uint8_t*)staged, sizeof(*staged));
  });
  if (!result) {
    LOGE("Staged sig write fail");
  }
  return result;
}

bool fwup_staged_sig_read(fwup_staged_sig_t* staged) {
  bool result = false;
  RTOS_THREAD_WITH_PRIVILEGE({
    result = fs_util_read_global((char*)FWUP_STAGED_SIG_PATH, (uint8_t*)staged, sizeof(*staged));
  });
  return result;
}

void fwup_staged_sig_remove(void) {
  RTOS_THREAD_WITH_PRIVILEGE({ (void)fs_remove(FWUP_STAGED_SIG_PATH); });
}

bool fwup_staged_sig_exists(void) {
  bool exists = false;
  RTOS_THREAD_WITH_PRIVILEGE({ exists = fs_file_exists(FWUP_STAGED_SIG_PATH); });
  return exists;
}

#ifdef EMBEDDED_BUILD
bool fwup_staged_sig_commit_to_flash(const fwup_staged_sig_t* staged) {
  // The staged signature must target the inactive slot — never the running one.
  if (staged->target_slot != fwup_target_slot()) {
    LOGE("Bad staged slot");
    return false;
  }
  void* addr = fwup_slot_signature_address(staged->target_slot);
  if (addr == NULL) {
    return false;
  }
  return fwup_commit_signature_to(addr, staged->signature);
}
#endif
