#include "assert.h"
#include "hal_nfc.h"
#include "hal_nfc_impl.h"
#include "hal_nfc_listener_impl.h"
#include "hex.h"
#include "log.h"
#include "platform.h"
#include "rfal_isoDep.h"
#include "rfal_nfc.h"
#include "rfal_platform.h"
#include "rfal_rf.h"
#include "rtos_thread.h"
#include "t4t.h"
#include "ui_messaging.h"
#include "wca.h"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

// ST25 configuration fields.
static const uint8_t NFCID3[] = {0x01, 0xFE, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A};
static const uint8_t GB[] = {0x46, 0x66, 0x6d, 0x01, 0x01, 0x11, 0x02, 0x02, 0x07, 0x80,
                             0x03, 0x02, 0x00, 0x03, 0x04, 0x01, 0x32, 0x07, 0x01, 0x03};
static const uint8_t ceNFCA_NFCID[] = {'W', '1', 'A', '0'};  // NFCID1 / UID (4 bytes)
static const uint8_t ceNFCA_SENS_RES[] = {0x02, 0x00};       // SENS_RES / ATQA for 4-byte UID
static const uint8_t ceNFCA_SEL_RES = 0x20;                  // SEL_RES / SAK

// iOS NFC session timeout state machine.
//
// iOS performs background tag reading (BTR) whenever the screen is on and an NFC tag is nearby.
// The Bitkey device's T4T stub responds 0x9000 (success) to all T4T commands, so BTR believes
// it has found a valid NDEF tag and holds the NFC radio indefinitely. This prevents the Bitkey
// app's foreground NFCTagReaderSession from activating — the NFC scanning sheet never appears,
// or the UI freezes until the user physically removes the device from the field.
//
// At the firmware level, BTR and foreground sessions are indistinguishable — both use T4T
// commands with no distinguishing markers (unlike Android, which uses vendor-specific bytes
// caught by the filter in RFAL_NFC_STATE_ACTIVATED below). The only behavioral difference is
// that the app's foreground session sends a WCA (Wallet Custom APDU) command after the T4T
// handshake. Background reading never sends WCA.
//
// The state machine uses this as the discriminator:
//   IDLE → (activation) → ACTIVATED → (WCA received) → ESTABLISHED (normal operation)
//                              ↓ (timeout, no WCA)
//                          COOLDOWN → (cooldown expires) → IDLE
//
// When a session activates, a timer starts. If WCA arrives in time, the session is permanent.
// If not, we assume background reading, tear down the session, and briefly reject
// re-activations (cooldown) to create a gap for the foreground session to claim the radio.

static bool hal_nfc_filter_command(uint8_t* buffer, uint32_t len);
static st_ret_t hal_nfc_deactivate_session(void);

extern hal_nfc_priv_t hal_nfc_priv;

void hal_nfc_listener_init(void) {
  hal_nfc_priv.discovery_cfg.compMode = RFAL_COMPLIANCE_MODE_NFC;
  hal_nfc_priv.discovery_cfg.devLimit = 1U;
  hal_nfc_priv.discovery_cfg.nfcfBR = RFAL_BR_212;
  hal_nfc_priv.discovery_cfg.ap2pBR = RFAL_BR_424;
  hal_nfc_priv.discovery_cfg.maxBR = RFAL_BR_KEEP;

  hal_nfc_priv.discovery_cfg.isoDepFS = RFAL_ISODEP_FSXI_256;
  hal_nfc_priv.discovery_cfg.nfcDepLR = RFAL_NFCDEP_LR_254;
  memcpy(&hal_nfc_priv.discovery_cfg.nfcid3, NFCID3, sizeof(NFCID3));
  memcpy(&hal_nfc_priv.discovery_cfg.GB, GB, sizeof(GB));
  hal_nfc_priv.discovery_cfg.GBLen = sizeof(GB);
  hal_nfc_priv.discovery_cfg.p2pNfcaPrio = false;

  hal_nfc_priv.discovery_cfg.notifyCb = NULL;
  hal_nfc_priv.discovery_cfg.wakeupEnabled = false;
  hal_nfc_priv.discovery_cfg.wakeupConfigDefault = true;
  hal_nfc_priv.discovery_cfg.wakeupNPolls = 1U;
  hal_nfc_priv.discovery_cfg.totalDuration = HAL_NFC_DEFAULT_LISTENER_TIMEOUT_MS;
  hal_nfc_priv.discovery_cfg.techs2Find = RFAL_NFC_LISTEN_TECH_A;

  memcpy(hal_nfc_priv.discovery_cfg.lmConfigPA.SENS_RES, ceNFCA_SENS_RES, RFAL_LM_SENS_RES_LEN);
  memcpy(hal_nfc_priv.discovery_cfg.lmConfigPA.nfcid, ceNFCA_NFCID, RFAL_LM_NFCID_LEN_04);
  hal_nfc_priv.discovery_cfg.lmConfigPA.nfcidLen = RFAL_LM_NFCID_LEN_04;
  hal_nfc_priv.discovery_cfg.lmConfigPA.SEL_RES = ceNFCA_SEL_RES;
}

void hal_nfc_listener_deinit(void) {
  hal_nfc_priv.session_state = NFC_SESSION_IDLE;
  hal_nfc_priv.session_timer_start = 0;
  const st_ret_t err = rfalListenStop();
  ASSERT_LOG(((err == RFAL_ERR_NONE) || (err == RFAL_ERR_WRONG_STATE)), "%d", err);
}

void hal_nfc_listener_run(hal_nfc_callback_t callback) {
  const rfalNfcState rfal_state = rfalNfcGetState();

  // Handle session timeout and cooldown before processing RFAL state.
  // This keeps all cooldown logic in one place.
  switch (hal_nfc_priv.session_state) {
    case NFC_SESSION_ACTIVATED:
      if (RTOS_DEADLINE(hal_nfc_priv.session_timer_start, NFC_SESSION_TIMEOUT_MS)) {
#if HAL_NFC_LOG_COMMS
        LOGD("nfc session timeout — no WCA received, deactivating");
#endif
        hal_nfc_deactivate_session();
        hal_nfc_priv.session_state = NFC_SESSION_COOLDOWN;
        hal_nfc_priv.session_timer_start = rtos_thread_systime();
        return;
      }
      break;

    case NFC_SESSION_COOLDOWN:
      if (RTOS_DEADLINE(hal_nfc_priv.session_timer_start, NFC_COOLDOWN_MS)) {
        hal_nfc_priv.session_state = NFC_SESSION_IDLE;
        break;
      }

      // During cooldown, deactivate any active session to keep the tag invisible
      // until the foreground session can claim the radio. Check all active RFAL
      // states — a reactivation can advance past ACTIVATED to DATAEXCHANGE
      // between listener loop iterations.
      if (rfal_state == RFAL_NFC_STATE_ACTIVATED || rfal_state == RFAL_NFC_STATE_DATAEXCHANGE ||
          rfal_state == RFAL_NFC_STATE_DATAEXCHANGE_DONE) {
        hal_nfc_deactivate_session();
        hal_nfc_priv.session_timer_start = rtos_thread_systime();
      }
      return;  // Still in cooldown window.

    default:
      break;
  }

  // Session state is IDLE, ACTIVATED, or ESTABLISHED — process RFAL state normally.
  int result = 0;
  switch (rfal_state) {
    case RFAL_NFC_STATE_IDLE:
      result = rfalNfcDiscover(&hal_nfc_priv.discovery_cfg);
      if (result != RFAL_ERR_NONE) {
        ASSERT_LOG(false, "%d", result);
      }
      break;

    case RFAL_NFC_STATE_ACTIVATED:
      hal_nfc_priv.session_state = NFC_SESSION_ACTIVATED;
      hal_nfc_priv.session_timer_start = rtos_thread_systime();

#if HAL_NFC_LOG_COMMS
      LOGD("nfc session activated — waiting for WCA");
#endif

      result = rfalNfcDataExchangeStart(NULL, 0, &hal_nfc_priv.rx_buf, &hal_nfc_priv.rx_len, 0);
      if (result != RFAL_ERR_NONE) {
        ASSERT_LOG(false, "%d", result);
      }
      (void)rfalNfcDataExchangeGetStatus();

      // This is a REALLY gross Android compatibility hack.
      //
      // We've observed that Android phones, when background tag reading, can hit this code path.
      //
      // Android phones seem (?) to inititate background tag reading with a vendor-specific byte
      // (0xe0 on Samsung, 0xa0 on Pixel).
      // And for some reason, when background tag reading happens, we get stuck in
      // RFAL_NFC_STATE_DATAEXCHANGE and can never resume until the field is torn (or we
      // deactivate it). This means that NFC won't work if you initiate an NFC transaction through
      // the Bitkey app after the field was established due to background tag reading. It also means
      // that successive NFC transactions won't work unless you tear the field and put the phone
      // back next to the hardware.
      //
      // The "fix" is to deactivate the field ourselves. This won't impact regular NFC
      // transactions through the app, since background tag reading doesn't happen when the
      // foreground app is doing an NFC transaction.
      //
      // Note also that we have to allow t4t commands (which we don't actually properly handle, but
      // do respond to) because otherwise NFC on iOS doesn't work.
      if (!hal_nfc_filter_command(hal_nfc_priv.rx_buf, *hal_nfc_priv.rx_len)) {
#if HAL_NFC_LOG_COMMS
        LOGD("nfc filtered:");
        dumphex(hal_nfc_priv.rx_buf, *hal_nfc_priv.rx_len);
#endif
        hal_nfc_deactivate_session();
        hal_nfc_priv.session_state = NFC_SESSION_IDLE;
      }

      break;

    case RFAL_NFC_STATE_DATAEXCHANGE_DONE: {
      ReturnCode status = rfalNfcDataExchangeGetStatus();
      if (status != RFAL_ERR_NONE) {
        // Only show error if this was a real communication failure, not just the phone
        // being removed after a successful command. Link loss with no data received
        // (rx_len == 0) means we were waiting for the next command and the phone left.
        if (status != RFAL_ERR_LINK_LOSS || *hal_nfc_priv.rx_len > 0) {
          UI_SHOW_EVENT(UI_EVENT_NFC_ERROR);
        }
        ASSERT(RFAL_ERR_NONE == hal_nfc_deactivate_session());
        hal_nfc_priv.session_state = NFC_SESSION_IDLE;
        break;
      }

#if HAL_NFC_LOG_COMMS
      if (*hal_nfc_priv.rx_len > 0) {
        LOGD("nfc rx:");
        dumphex(hal_nfc_priv.rx_buf, *hal_nfc_priv.rx_len);
      }
#endif

      // Transition ACTIVATED → ESTABLISHED when the first WCA command arrives.
      if (hal_nfc_priv.session_state == NFC_SESSION_ACTIVATED &&
          wca_is_valid(hal_nfc_priv.rx_buf, *hal_nfc_priv.rx_len)) {
#if HAL_NFC_LOG_COMMS
        LOGD("nfc session established — WCA received");
#endif
        hal_nfc_priv.session_state = NFC_SESSION_ESTABLISHED;
      }

      uint8_t tx_buf[RFAL_FEATURE_ISO_DEP_APDU_MAX_LEN] = {0};
      uint32_t tx_len = sizeof(tx_buf);
      if (callback(hal_nfc_priv.rx_buf, *hal_nfc_priv.rx_len, tx_buf, &tx_len)) {
        ASSERT(RFAL_ERR_NONE == rfalNfcDataExchangeStart(tx_buf, tx_len, &hal_nfc_priv.rx_buf,
                                                         &hal_nfc_priv.rx_len, RFAL_FWT_NONE));
      } else {
        hal_nfc_deactivate_session();
        hal_nfc_priv.session_state = NFC_SESSION_IDLE;
      }

#if HAL_NFC_LOG_COMMS
      if (tx_len > 0) {
        LOGD("nfc tx:");
        dumphex(tx_buf, tx_len);
      }
#endif

      break;
    }

    default:
      // Necessary to meet NFC timing requirements
      rfalNfcWorker();
      break;
  }
}

static bool hal_nfc_filter_command(uint8_t* buffer, uint32_t len) {
  return (wca_is_valid(buffer, len)) || (t4t_is_valid(buffer, len));
}

static st_ret_t hal_nfc_deactivate_session(void) {
  wca_reset_session_state();
  return rfalNfcDeactivate(RFAL_NFC_DEACTIVATE_IDLE);
}
