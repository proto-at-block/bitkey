#include "hal_nfc.h"
#include "hal_nfc_reader_impl.h"
#include "platform.h"
#include "rfal_platform.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#if defined(PLATFORM_CFG_NFC_READER_MODE) && (PLATFORM_CFG_NFC_READER_MODE)

void hal_nfc_reader_init(void) {
  // TODO(W-14164): Add NFC Reader Support
}

void hal_nfc_reader_deinit(void) {
  // TODO(W-14164): Add NFC Reader Support
}

void hal_nfc_reader_run(hal_nfc_callback_t callback) {
  // TODO(W-14164): Add NFC Reader Support
  (void)callback;
}

#endif
